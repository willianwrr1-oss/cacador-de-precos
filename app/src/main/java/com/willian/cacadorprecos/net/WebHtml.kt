package com.willian.cacadorprecos.net

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import com.willian.cacadorprecos.App
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Carrega a página num navegador embutido (WebView), como o Chrome do celular faria.
 * Serve para lojas que bloqueiam leitura direta (HTTP 403) ou montam o preço com JavaScript.
 */
object WebHtml {

    private const val ESPERA_JS_MS = 3500L

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun get(url: String): String = withTimeout(40_000) {
        suspendCancellableCoroutine { cont ->
            val main = Handler(Looper.getMainLooper())
            main.post {
                var web: WebView? = null
                try {
                    val w = WebView(App.instance)
                    web = w
                    w.settings.javaScriptEnabled = true
                    w.settings.domStorageEnabled = true
                    w.settings.loadsImagesAutomatically = false
                    var entregue = false
                    fun fechar() {
                        try { w.stopLoading(); w.destroy() } catch (e: Exception) { }
                    }
                    w.webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView, u: String) {
                            if (entregue) return
                            main.postDelayed({
                                if (entregue) return@postDelayed
                                view.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { raw ->
                                    if (entregue) return@evaluateJavascript
                                    entregue = true
                                    val html = try {
                                        JSONTokener(raw).nextValue() as? String ?: ""
                                    } catch (e: Exception) {
                                        ""
                                    }
                                    fechar()
                                    if (cont.isActive) {
                                        if (html.isBlank()) cont.resumeWithException(IOException("Página vazia no navegador"))
                                        else cont.resume(html)
                                    }
                                }
                            }, ESPERA_JS_MS)
                        }
                    }
                    cont.invokeOnCancellation { main.post { try { w.stopLoading(); w.destroy() } catch (e: Exception) { } } }
                    w.loadUrl(url)
                } catch (e: Exception) {
                    try { web?.destroy() } catch (x: Exception) { }
                    if (cont.isActive) cont.resumeWithException(IOException("Navegador embutido indisponível"))
                }
            }
        }
    }
}
