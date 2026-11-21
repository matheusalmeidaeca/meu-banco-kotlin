package com.meubanco.patrimonio.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import com.meubanco.patrimonio.data.Backend

private class Bridge(val onItem: (String) -> Unit, val onError: (String) -> Unit, val onClose: () -> Unit) {
    private val h = Handler(Looper.getMainLooper())
    @JavascriptInterface fun onItem(id: String) { h.post { onItem(id) } }
    @JavascriptInterface fun onError(m: String) { h.post { onError(m) } }
    @JavascriptInterface fun onClose() { h.post { onClose() } }
}

/** Tela cheia com o widget oficial da Pluggy. Bancos que pedem autorização no app/site do banco abrem fora e voltam por meubanco://pluggy. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PluggyConnectView(connectToken: String, onItem: (String) -> Unit, onError: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    fun external(u: Uri) { try { ctx.startActivity(Intent(Intent.ACTION_VIEW, u).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) { } }
    fun internalHost(u: Uri) = (u.host ?: "").let { it == "pluggy.ai" || it.endsWith(".pluggy.ai") } && (u.scheme == "https")

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false)) {
        AndroidView(modifier = Modifier.fillMaxSize(), factory = { c ->
            WebView(c).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.setSupportMultipleWindows(true)
                settings.javaScriptCanOpenWindowsAutomatically = true
                addJavascriptInterface(Bridge(onItem, onError, onClose), "Android")
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                        val u = r.url
                        return if (u.scheme == "about" || u.scheme == "data" || internalHost(u)) false else { external(u); true }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message): Boolean {
                        val tmp = WebView(view.context)
                        tmp.webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean { external(r.url); return true }
                        }
                        (resultMsg.obj as WebView.WebViewTransport).webView = tmp; resultMsg.sendToTarget(); return true
                    }
                }
                loadDataWithBaseURL("https://cdn.pluggy.ai/", Backend.widgetHtml(connectToken, Backend.sandbox()), "text/html", "utf-8", null)
            }
        }, onRelease = { it.destroy() })
    }
}
