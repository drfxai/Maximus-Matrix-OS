package ai.drfx.maximus.matrixai.signals

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import ai.drfx.maximus.matrixai.database.entities.TradingSignalEntity
import ai.drfx.maximus.matrixai.database.repository.TradingSignalRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.PrintWriter
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

data class WebhookLogEntry(
    val id: String = UUID.randomUUID().toString().take(8),
    val timestamp: Long = System.currentTimeMillis(),
    val sourceIp: String,
    val method: String,
    val path: String,
    val rawPayload: String,
    val statusCode: Int,
    val statusMessage: String,
    val parsedSignalId: String? = null,
    val symbol: String? = null,
    val direction: String? = null
)

data class WebhookServerState(
    val isRunning: Boolean = false,
    val port: Int = 8080,
    val localIp: String = "127.0.0.1",
    val secretToken: String = "maximus_matrix_secret",
    val requireSecret: Boolean = false,
    val totalReceived: Int = 0,
    val totalSuccess: Int = 0,
    val totalErrors: Int = 0,
    val lastReceivedTime: Long? = null,
    val lastReceivedSymbol: String? = null
)

/**
 * Embedded HTTP Webhook Server running directly inside the app,
 * capable of receiving real-time signals from TradingView, Cloudflare Workers,
 * or curl/Postman on the local network or via Cloudflare Tunnel.
 */
class SignalWebhookServer(
    private val signalRepository: TradingSignalRepository,
    private val context: Context? = null,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    var notificationService: SignalNotificationService? = null
) {
    private val serverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private var serverJob: Job? = null

    private val _serverState = MutableStateFlow(
        WebhookServerState(
            localIp = "127.0.0.1",
            port = 8080,
            isRunning = false
        )
    )
    val serverState: StateFlow<WebhookServerState> = _serverState.asStateFlow()

    private val _logs = MutableStateFlow<List<WebhookLogEntry>>(emptyList())
    val logs: StateFlow<List<WebhookLogEntry>> = _logs.asStateFlow()

    // Shared flow for notifying UI of incoming webhook signals in real time
    private val _onSignalReceivedEvent = MutableSharedFlow<TradingSignalEntity>(extraBufferCapacity = 10)
    val onSignalReceivedEvent: SharedFlow<TradingSignalEntity> = _onSignalReceivedEvent.asSharedFlow()

    init {
        // Asynchronously resolve local IP in background without blocking UI thread
        serverScope.launch {
            try {
                val ip = resolveDeviceIp()
                _serverState.update { it.copy(localIp = ip) }
            } catch (_: Exception) {}
        }
    }

    fun startServer(port: Int = _serverState.value.port) {
        if (running.get()) return

        serverJob = serverScope.launch {
            try {
                val ip = resolveDeviceIp()
                val socket = ServerSocket(port)
                serverSocket = socket
                running.set(true)
                _serverState.update {
                    it.copy(
                        isRunning = true,
                        port = port,
                        localIp = ip
                    )
                }
                logInternal("SYSTEM", "SERVER_START", "Webhook Server started on port $port ($ip)", 200, "Listening")

                while (isActive && running.get()) {
                    try {
                        val clientSocket = socket.accept() ?: break
                        launch(Dispatchers.IO) {
                            handleClientSocket(clientSocket)
                        }
                    } catch (e: Exception) {
                        if (!running.get()) break
                    }
                }
            } catch (e: Exception) {
                running.set(false)
                _serverState.update { it.copy(isRunning = false) }
                logInternal("SYSTEM", "ERROR", "Failed to start server on port $port: ${e.message}", 500, "Start Failed")
            }
        }
    }

    fun stopServer() {
        running.set(false)
        try {
            serverSocket?.close()
        } catch (_: Exception) {}
        serverSocket = null
        serverJob?.cancel()
        _serverState.update { it.copy(isRunning = false) }
        logInternal("SYSTEM", "SERVER_STOP", "Webhook Server stopped by user", 200, "Stopped")
    }

    fun toggleServer() {
        if (_serverState.value.isRunning) {
            stopServer()
        } else {
            startServer(_serverState.value.port)
        }
    }

    fun setPort(port: Int) {
        val wasRunning = _serverState.value.isRunning
        if (wasRunning) {
            stopServer()
        }
        _serverState.update { it.copy(port = port) }
        if (wasRunning) {
            startServer(port)
        }
    }

    fun setSecretToken(token: String) {
        _serverState.update { it.copy(secretToken = token.trim()) }
    }

    fun toggleRequireSecret() {
        _serverState.update { it.copy(requireSecret = !it.requireSecret) }
    }

    fun clearLogs() {
        _logs.value = emptyList()
    }

    /**
     * Resolves local device IP address (e.g. 192.168.x.x or 10.0.2.15).
     */
    fun resolveDeviceIp(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            var fallbackIp = "127.0.0.1"
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val hostAddress = addr.hostAddress ?: continue
                        if (hostAddress.startsWith("192.168.") || hostAddress.startsWith("10.") || hostAddress.startsWith("172.")) {
                            return hostAddress
                        }
                        fallbackIp = hostAddress
                    }
                }
            }
            fallbackIp
        } catch (_: Exception) {
            "127.0.0.1"
        }
    }

    fun getLocalWebhookUrl(): String {
        val ip = _serverState.value.localIp
        val port = _serverState.value.port
        return "http://$ip:$port/api/v1/webhook/signals"
    }

    fun getEmulatorWebhookUrl(): String {
        val port = _serverState.value.port
        return "http://10.0.2.2:$port/api/v1/webhook/signals"
    }

    fun getLocalhostUrl(): String {
        val port = _serverState.value.port
        return "http://127.0.0.1:$port/api/v1/webhook/signals"
    }

    /**
     * Dispatches a webhook payload for testing, simulating an external TradingView/Cloudflare trigger.
     */
    suspend fun dispatchTestWebhook(payload: String, sourceHint: String = "Test Simulator"): WebhookParseResult {
        val clientIp = "127.0.0.1 (Simulator)"
        val parseResult = SignalWebhookParser.parse(payload, sourceHint)

        when (parseResult) {
            is WebhookParseResult.Success -> {
                val signal = parseResult.signal
                signalRepository.saveSignal(signal)
                _onSignalReceivedEvent.emit(signal)
                notificationService?.onSignalReceived(signal, sourceHint)

                recordLog(
                    sourceIp = clientIp,
                    method = "POST",
                    path = "/api/v1/webhook/signals (Simulated)",
                    rawPayload = payload,
                    statusCode = 200,
                    statusMessage = "Success: Ingested & Saved to Room DB",
                    parsedSignalId = signal.id,
                    symbol = signal.symbol,
                    direction = signal.direction
                )
                updateMetrics(success = true, symbol = signal.symbol)
            }
            is WebhookParseResult.Failure -> {
                recordLog(
                    sourceIp = clientIp,
                    method = "POST",
                    path = "/api/v1/webhook/signals (Simulated)",
                    rawPayload = payload,
                    statusCode = 400,
                    statusMessage = "Parse Error: ${parseResult.errorMessage}",
                    parsedSignalId = null,
                    symbol = null,
                    direction = null
                )
                updateMetrics(success = false, symbol = null)
            }
        }
        return parseResult
    }

    private suspend fun handleClientSocket(socket: Socket) = withContext(Dispatchers.IO) {
        val clientIp = socket.inetAddress?.hostAddress ?: "unknown"
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val writer = PrintWriter(OutputStreamWriter(socket.getOutputStream()), true)

            // Read Request Line
            val requestLine = reader.readLine() ?: return@withContext
            val parts = requestLine.split(" ")
            if (parts.size < 2) return@withContext

            val method = parts[0].uppercase()
            val fullPath = parts[1]
            val path = fullPath.substringBefore("?")

            // Read Headers
            val headers = mutableMapOf<String, String>()
            var line: String? = reader.readLine()
            var contentLength = 0
            while (!line.isNullOrEmpty()) {
                val colon = line.indexOf(':')
                if (colon > 0) {
                    val key = line.substring(0, colon).trim().lowercase()
                    val value = line.substring(colon + 1).trim()
                    headers[key] = value
                    if (key == "content-length") {
                        contentLength = value.toIntOrNull() ?: 0
                    }
                }
                line = reader.readLine()
            }

            // Handle CORS OPTIONS request
            if (method == "OPTIONS") {
                sendResponse(
                    writer,
                    200,
                    "OK",
                    """{"status":"OK","cors":"enabled"}""",
                    cors = true
                )
                return@withContext
            }

            // Handle Health / Status GET request
            if (method == "GET") {
                val statusJson = JSONObject().apply {
                    put("status", "ONLINE")
                    put("gateway", "MAXIMUS MATRIX WEBHOOK ENGINE")
                    put("port", _serverState.value.port)
                    put("active", true)
                    put("totalReceived", _serverState.value.totalReceived)
                    put("time", System.currentTimeMillis())
                }.toString()
                sendResponse(writer, 200, "OK", statusJson, cors = true)
                return@withContext
            }

            if (method != "POST") {
                sendResponse(writer, 405, "Method Not Allowed", """{"error":"Method not allowed, use POST"}""")
                return@withContext
            }

            // Read Body
            val bodyBuilder = StringBuilder()
            if (contentLength > 0) {
                val buffer = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val count = reader.read(buffer, readTotal, contentLength - readTotal)
                    if (count < 0) break
                    readTotal += count
                }
                bodyBuilder.append(buffer, 0, readTotal)
            } else {
                // If chunked or no content-length specified
                while (reader.ready()) {
                    val ch = reader.read()
                    if (ch < 0) break
                    bodyBuilder.append(ch.toChar())
                }
            }
            val rawBody = bodyBuilder.toString()

            // Verify Secret if enabled
            val currentState = _serverState.value
            if (currentState.requireSecret) {
                val providedSecret = headers["x-webhook-secret"]
                    ?: headers["secret"]
                    ?: fullPath.substringAfter("token=", "")
                    ?: extractSecretFromJson(rawBody)

                if (providedSecret != currentState.secretToken) {
                    recordLog(
                        sourceIp = clientIp,
                        method = method,
                        path = path,
                        rawPayload = rawBody,
                        statusCode = 401,
                        statusMessage = "Unauthorized: Invalid secret token"
                    )
                    updateMetrics(success = false, symbol = null)
                    sendResponse(
                        writer,
                        401,
                        "Unauthorized",
                        """{"error":"Unauthorized: Invalid webhook secret token"}""",
                        cors = true
                    )
                    return@withContext
                }
            }

            // Source detection hint (TradingView, Cloudflare, etc.)
            val userAgent = headers["user-agent"] ?: ""
            val sourceHint = when {
                headers.containsKey("cf-ray") || headers.containsKey("cf-worker") || rawBody.contains("cloudflare", ignoreCase = true) -> "Cloudflare Worker"
                userAgent.contains("TradingView", ignoreCase = true) || rawBody.contains("tradingview", ignoreCase = true) -> "TradingView"
                else -> "External Webhook"
            }

            // Parse signal
            val parseResult = SignalWebhookParser.parse(rawBody, sourceHint)
            when (parseResult) {
                is WebhookParseResult.Success -> {
                    val signal = parseResult.signal
                    signalRepository.saveSignal(signal)
                    _onSignalReceivedEvent.emit(signal)
                    notificationService?.onSignalReceived(signal, sourceHint)

                    recordLog(
                        sourceIp = clientIp,
                        method = method,
                        path = path,
                        rawPayload = rawBody,
                        statusCode = 200,
                        statusMessage = "Signal Accepted & Saved to Room DB",
                        parsedSignalId = signal.id,
                        symbol = signal.symbol,
                        direction = signal.direction
                    )
                    updateMetrics(success = true, symbol = signal.symbol)

                    val responseBody = JSONObject().apply {
                        put("status", "SUCCESS")
                        put("signal_id", signal.id)
                        put("symbol", signal.symbol)
                        put("direction", signal.direction)
                        put("entry", signal.entryPrice)
                        put("stored", "ROOM_DATABASE")
                        put("timestamp", System.currentTimeMillis())
                    }.toString()

                    sendResponse(writer, 200, "OK", responseBody, cors = true)
                }
                is WebhookParseResult.Failure -> {
                    recordLog(
                        sourceIp = clientIp,
                        method = method,
                        path = path,
                        rawPayload = rawBody,
                        statusCode = 400,
                        statusMessage = "Validation Failed: ${parseResult.errorMessage}"
                    )
                    updateMetrics(success = false, symbol = null)

                    val responseBody = JSONObject().apply {
                        put("status", "ERROR")
                        put("error", parseResult.errorMessage)
                    }.toString()

                    sendResponse(writer, 400, "Bad Request", responseBody, cors = true)
                }
            }
        } catch (e: Exception) {
            recordLog(
                sourceIp = clientIp,
                method = "POST",
                path = "/webhook",
                rawPayload = e.message ?: "",
                statusCode = 500,
                statusMessage = "Server Exception: ${e.localizedMessage}"
            )
            updateMetrics(success = false, symbol = null)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {}
        }
    }

    private fun extractSecretFromJson(body: String): String? {
        return try {
            if (body.trim().startsWith("{")) {
                val json = JSONObject(body)
                if (json.has("secret")) json.getString("secret") else null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun sendResponse(
        writer: PrintWriter,
        statusCode: Int,
        statusText: String,
        body: String,
        cors: Boolean = false
    ) {
        val bytes = body.toByteArray(Charsets.UTF_8)
        writer.print("HTTP/1.1 $statusCode $statusText\r\n")
        writer.print("Content-Type: application/json; charset=utf-8\r\n")
        writer.print("Content-Length: ${bytes.size}\r\n")
        if (cors) {
            writer.print("Access-Control-Allow-Origin: *\r\n")
            writer.print("Access-Control-Allow-Methods: GET, POST, OPTIONS\r\n")
            writer.print("Access-Control-Allow-Headers: Content-Type, Authorization, x-webhook-secret, secret\r\n")
        }
        writer.print("Connection: close\r\n")
        writer.print("\r\n")
        writer.print(body)
        writer.flush()
    }

    private fun recordLog(
        sourceIp: String,
        method: String,
        path: String,
        rawPayload: String,
        statusCode: Int,
        statusMessage: String,
        parsedSignalId: String? = null,
        symbol: String? = null,
        direction: String? = null
    ) {
        val entry = WebhookLogEntry(
            sourceIp = sourceIp,
            method = method,
            path = path,
            rawPayload = rawPayload,
            statusCode = statusCode,
            statusMessage = statusMessage,
            parsedSignalId = parsedSignalId,
            symbol = symbol,
            direction = direction
        )
        _logs.update { list ->
            listOf(entry) + list.take(99) // Keep last 100 entries
        }
    }

    private fun logInternal(source: String, method: String, message: String, code: Int, status: String) {
        recordLog(
            sourceIp = source,
            method = method,
            path = "/internal",
            rawPayload = message,
            statusCode = code,
            statusMessage = status
        )
    }

    private fun updateMetrics(success: Boolean, symbol: String?) {
        _serverState.update { current ->
            current.copy(
                totalReceived = current.totalReceived + 1,
                totalSuccess = if (success) current.totalSuccess + 1 else current.totalSuccess,
                totalErrors = if (!success) current.totalErrors + 1 else current.totalErrors,
                lastReceivedTime = System.currentTimeMillis(),
                lastReceivedSymbol = symbol ?: current.lastReceivedSymbol
            )
        }
    }
}
