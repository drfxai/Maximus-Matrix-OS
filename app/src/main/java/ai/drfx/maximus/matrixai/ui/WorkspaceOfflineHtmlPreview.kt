package ai.drfx.maximus.matrixai.ui

import android.annotation.SuppressLint
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.ByteArrayInputStream

/**
 * Self-contained local preview of model-supplied HTML/CSS/JS. No remote URLs,
 * file access, cookies, JS interfaces, or implicit JavaScript execution.
 */
@Composable
internal fun WorkspaceOfflineHtmlPreview(html: String, css: String, js: String) {
    var previewOpen by remember(html, css, js) { mutableStateOf(false) }
    var scriptsApproved by remember(html, css, js) { mutableStateOf(false) }
    MaximusFramedCard {
        Row {
            Icon(Icons.Default.Language, null, tint = MaximusReference.gold,
                modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("HTML preview", color = MaximusReference.white)
                Text("Internal WebView · remote requests blocked", color = MaximusReference.muted,
                    fontSize = 10.sp)
            }
        }
        OutlinedButton(onClick = { previewOpen = !previewOpen },
            modifier = Modifier.fillMaxWidth()) {
            Text(if (previewOpen) "Close preview" else "Preview locally")
        }
        if (previewOpen) {
            if (js.isNotBlank()) {
                Row(Modifier.fillMaxWidth()) {
                    Text("Run untrusted inline JavaScript (local only)",
                        modifier = Modifier.weight(1f), fontSize = 11.sp,
                        color = MaximusReference.muted)
                    Switch(checked = scriptsApproved,
                        onCheckedChange = { scriptsApproved = it })
                }
            }
            val previewPage = remember(html, css, js, scriptsApproved) {
                val csp = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; " +
                    (if (scriptsApproved) "script-src 'unsafe-inline'; " else "script-src 'none'; ") +
                    "connect-src 'none'; frame-src 'none'; form-action 'none';"
                val injected = "<meta http-equiv=\"Content-Security-Policy\" content=\"$csp\">" +
                    "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
                    "<style>body{margin:8px;font-family:sans-serif;} $css</style>" +
                    (if (scriptsApproved) "<script>$js<\\/script>".replace("<\\/script>", "</script>") else "")
                // Wrap the user's markup in a document; never treat it as trusted UI.
                "<!DOCTYPE html><html><head>$injected</head><body>$html</body></html>"
            }
            LockedDownWebView(previewPage, scriptsApproved)
            Text("Preview only: no deployment, network fetch, server execution or verification.",
                color = MaximusReference.muted, fontSize = 10.sp)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LockedDownWebView(markup: String, javascriptEnabled: Boolean) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val browser = remember(ctx) {
        WebView(ctx).apply {
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.blockNetworkLoads = true
            settings.setSupportMultipleWindows(false)
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.mediaPlaybackRequiresUserGesture = true
            setDownloadListener { _, _, _, _, _ -> }
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean = true
                override fun shouldInterceptRequest(
                    view: WebView, request: WebResourceRequest
                ): WebResourceResponse? {
                    val protocol = request.url.scheme?.lowercase()
                    return if (protocol == "https" || protocol == "http" ||
                        protocol == "file" || protocol == "content") {
                        WebResourceResponse("text/plain", "UTF-8",
                            ByteArrayInputStream(ByteArray(0)))
                    } else null
                }
            }
        }
    }
    DisposableEffect(browser) {
        onDispose {
            browser.stopLoading()
            browser.loadUrl("about:blank")
            browser.destroy()
        }
    }
    LaunchedEffect(browser, markup, javascriptEnabled) {
        browser.settings.javaScriptEnabled = javascriptEnabled
        browser.loadDataWithBaseURL(null, markup, "text/html", "UTF-8", null)
    }
    AndroidView(
        factory = { browser },
        modifier = Modifier.fillMaxWidth().height(240.dp),
        update = { it.setBackgroundColor(android.graphics.Color.WHITE) }
    )
}
