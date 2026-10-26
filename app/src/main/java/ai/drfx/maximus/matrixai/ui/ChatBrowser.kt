package ai.drfx.maximus.matrixai.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import ai.drfx.maximus.matrixai.llm.AgentTools
import ai.drfx.maximus.matrixai.llm.ChatAttachment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import org.json.JSONArray

/** What the Chat browser should open: a URL or search, or an HTML document to view. */
internal sealed interface BrowserStart {
    data class Address(val input: String) : BrowserStart
    data class Html(val name: String, val html: String) : BrowserStart
}

/**
 * Full-screen browser inside Chat. The bar takes a URL or a Google search. Pages run with
 * JavaScript like a normal browser, but without file or content access and without any bridge
 * into the app. HTML documents from Chat are shown with JavaScript and network loads off.
 * "Ask about this page" hands the page's readable text to the composer as an attachment.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun ChatBrowserDialog(start: BrowserStart, onDismiss: () -> Unit, onAskAboutPage: (ChatAttachment) -> Unit) {
    val context = LocalContext.current
    val focus = LocalFocusManager.current
    var address by remember { mutableStateOf((start as? BrowserStart.Address)?.input.orEmpty()) }
    var currentUrl by remember { mutableStateOf("") }
    var title by remember { mutableStateOf((start as? BrowserStart.Html)?.name.orEmpty()) }
    var progress by remember { mutableIntStateOf(0) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val document = start as? BrowserStart.Html
    val web = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = document == null
                domStorageEnabled = document == null
                allowFileAccess = false
                allowContentAccess = false
                @Suppress("DEPRECATION") allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION") allowUniversalAccessFromFileURLs = false
                blockNetworkLoads = document != null
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                setGeolocationEnabled(false)
                builtInZoomControls = true
                displayZoomControls = false
                loadWithOverviewMode = true
                useWideViewPort = true
            }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val scheme = request.url.scheme?.lowercase()
                    if (scheme == "http" || scheme == "https") return document != null
                    // tel:, mailto:, intent: and app links leave the in-app browser only on a tap.
                    if (request.hasGesture() && scheme in setOf("tel", "mailto", "sms", "geo", "market")) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                    }
                    return true
                }
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    error = ""
                    if (document == null) currentUrl = url.orEmpty()
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    canBack = view.canGoBack(); canForward = view.canGoForward()
                    if (document == null) title = view.title.orEmpty()
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest,
                                             error2: android.webkit.WebResourceError) {
                    if (request.isForMainFrame) error = error2.description?.toString().orEmpty().ifBlank { "Page failed to load" }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                override fun onReceivedTitle(view: WebView, value: String?) { if (document == null) title = value.orEmpty() }
            }
            when (start) {
                is BrowserStart.Address -> loadUrl(AgentTools.browserTarget(start.input))
                is BrowserStart.Html -> loadDataWithBaseURL(null, start.html, "text/html", "UTF-8", null)
            }
        }
    }
    DisposableEffect(web) { onDispose { web.stopLoading(); web.destroy() } }
    fun go(input: String) {
        focus.clearFocus(); editing = false
        web.loadUrl(AgentTools.browserTarget(input))
    }
    fun askAboutPage() {
        capturing = true
        val script = "(function(){var t=document.body?document.body.innerText:'';" +
            "return [document.title||'', location.href||'', t.substring(0, 60000)];})()"
        web.evaluateJavascript(script) { raw ->
            capturing = false
            val parts = runCatching { JSONArray(raw) }.getOrNull()
            val pageTitle = parts?.optString(0).orEmpty().ifBlank { title }
            val pageUrl = parts?.optString(1).orEmpty().ifBlank { currentUrl }
            val text = parts?.optString(2).orEmpty().ifBlank { document?.html?.let { AgentTools.pageText(it, 60_000).text }.orEmpty() }
            if (text.isBlank()) { error = "This page has no readable text yet."; return@evaluateJavascript }
            val host = AgentTools.host(pageUrl) ?: document?.name ?: "page"
            onAskAboutPage(ChatAttachment(
                name = (pageTitle.ifBlank { host }).take(80) + ".txt", mimeType = "text/plain",
                data = "Web page: " + pageTitle + "\nURL: " + pageUrl + "\n\n" + text.trim(),
                isText = true, summary = "Web page · $host"))
        }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandlerCompat(enabled = canBack && document == null) { web.goBack() }
        Surface(Modifier.fillMaxSize(), color = MaximusReference.navy) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding().testTag("chat_browser")) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(40.dp)) { Icon(Icons.Default.Close, "Close browser", tint = MaximusReference.white) }
                    if (document == null) {
                        val shown = if (editing) address else (AgentTools.host(currentUrl) ?: address)
                        OutlinedTextField(
                            value = shown, onValueChange = { address = it },
                            singleLine = true, placeholder = { Text("Search Google or type a URL", fontSize = 13.sp) },
                            leadingIcon = { Icon(if (currentUrl.startsWith("https://") && !editing) Icons.Default.Lock else Icons.Default.Search,
                                null, Modifier.size(16.dp), tint = MaximusReference.muted) },
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp, color = MaximusReference.white),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { go(address) }),
                            shape = RoundedCornerShape(22.dp),
                            modifier = Modifier.weight(1f).heightIn(min = 44.dp).testTag("chat_browser_address")
                                .onFocusChanged { state ->
                                    if (state.isFocused && !editing) { editing = true; address = currentUrl.ifBlank { address } }
                                }
                        )
                    } else {
                        Column(Modifier.weight(1f).padding(horizontal = 6.dp)) {
                            Text(document.name, color = MaximusReference.white, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("HTML view · scripts and network off", color = MaximusReference.muted, fontSize = 10.sp)
                        }
                    }
                    IconButton(onClick = { if (progress in 1..99) web.stopLoading() else web.reload() }, modifier = Modifier.size(40.dp)) {
                        Icon(if (progress in 1..99) Icons.Default.Close else Icons.Default.Refresh, "Reload", tint = MaximusReference.muted)
                    }
                }
                AnimatedVisibility(progress in 1..99) {
                    LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp),
                        color = MaximusReference.gold, trackColor = MaximusReference.navy)
                }
                if (error.isNotBlank()) Text(error, color = MaximusReference.orange, fontSize = 11.sp,
                    modifier = Modifier.fillMaxWidth().background(MaximusReference.orange.copy(alpha = .08f)).padding(8.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    AndroidView(factory = { web }, modifier = Modifier.fillMaxSize())
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { web.goBack() }, enabled = canBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = if (canBack) MaximusReference.white else MaximusReference.muted.copy(alpha = .4f))
                    }
                    IconButton(onClick = { web.goForward() }, enabled = canForward) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, "Forward", tint = if (canForward) MaximusReference.white else MaximusReference.muted.copy(alpha = .4f))
                    }
                    if (document == null) IconButton(onClick = { go("") }) {
                        Icon(Icons.Default.Public, "Google", tint = MaximusReference.muted)
                    }
                    Spacer(Modifier.weight(1f))
                    if (document == null && currentUrl.startsWith("http")) IconButton(onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))) }
                    }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, "Open in external browser", tint = MaximusReference.muted) }
                    Button(onClick = { askAboutPage() }, enabled = !capturing && progress !in 1..60,
                        colors = ButtonDefaults.buttonColors(containerColor = MaximusReference.gold, contentColor = androidx.compose.ui.graphics.Color.Black),
                        contentPadding = PaddingValues(horizontal = 14.dp), modifier = Modifier.height(38.dp).testTag("chat_browser_ask")) {
                        Icon(Icons.Default.AutoAwesome, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Ask about this page", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackHandlerCompat(enabled: Boolean, onBack: () -> Unit) {
    androidx.activity.compose.BackHandler(enabled = enabled, onBack = onBack)
}
