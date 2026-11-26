package com.willian.cacadorprecos.net

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.willian.cacadorprecos.App
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Carrega páginas usando WebView.
 *
 * Mantém cookies entre carregamentos, executa JavaScript e aguarda
 * a estabilização do conteúdo antes de devolver o HTML.
 */
object WebHtml {

    private const val TEMPO_MAXIMO_MS = 12_000L
    private const val INTERVALO_VERIFICACAO_MS = 700L
    private const val TEMPO_MINIMO_MS = 1_800L
    private const val ESTABILIDADE_NECESSARIA = 2

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun get(url: String): String = withTimeout(40_000) {

        suspendCancellableCoroutine { cont ->

            val main = Handler(Looper.getMainLooper())

            main.post {

                var web: WebView? = null

                try {
                    val w = WebView(App.instance)
                    web = w

                    val finalizado = AtomicBoolean(false)

                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    cookies.setAcceptThirdPartyCookies(w, true)

                    w.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadsImagesAutomatically = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                    }

                    // Mantém o User-Agent padrão do WebView.
                    // Não altera a identificação do navegador.

                    var inicioCarregamento = 0L
                    var ultimaVersao = ""
                    var estabilidade = 0
                    var ultimoHtml = ""

                    var tarefa: Runnable? = null

                    fun fechar() {
                        tarefa?.let { main.removeCallbacks(it) }

                        try {
                            cookies.flush()
                        } catch (_: Exception) {
                        }

                        try {
                            w.stopLoading()
                            w.destroy()
                        } catch (_: Exception) {
                        }
                    }

                    fun concluir(
                        html: String? = null,
                        erro: Exception? = null
                    ) {
                        if (!finalizado.compareAndSet(false, true)) return

                        fechar()

                        if (!cont.isActive) return

                        if (erro != null) {
                            cont.resumeWithException(erro)
                        } else if (html.isNullOrBlank()) {
                            cont.resumeWithException(
                                IOException("Página vazia no navegador")
                            )
                        } else {
                            cont.resume(html)
                        }
                    }

                    fun extrairHtml(raw: String?): String {
                        if (raw.isNullOrBlank() || raw == "null") return ""

                        return try {
                            JSONTokener(raw).nextValue() as? String ?: ""
                        } catch (_: Exception) {
                            ""
                        }
                    }

                    fun verificarPagina(view: WebView) {

                        if (finalizado.get()) return

                        val script = """
                            (function() {
                                var doc = document.documentElement;
                                if (!doc) return '';

                                var html = doc.outerHTML || '';
                                var estado = document.readyState || '';

                                return '<!--READY:' + estado +
                                       '--><!--URL:' + location.href +
                                       '-->' + html;
                            })();
                        """.trimIndent()

                        view.evaluateJavascript(script) { raw ->

                            if (finalizado.get()) return@evaluateJavascript

                            val html = extrairHtml(raw)

                            if (html.isNotBlank()) {

                                ultimoHtml = html

                                val pronto = html.contains("<!--READY:complete-->")
                                val assinatura = html.hashCode().toString()

                                if (assinatura == ultimaVersao) {
                                    estabilidade++
                                } else {
                                    ultimaVersao = assinatura
                                    estabilidade = 0
                                }

                                val decorrido =
                                    System.currentTimeMillis() - inicioCarregamento

                                val paginaEstavel =
                                    pronto &&
                                    estabilidade >= ESTABILIDADE_NECESSARIA &&
                                    decorrido >= TEMPO_MINIMO_MS

                                val tempoEsgotado =
                                    decorrido >= TEMPO_MAXIMO_MS

                                if (paginaEstavel || tempoEsgotado) {
                                    concluir(ultimoHtml)
                                    return@evaluateJavascript
                                }

                            } else {
                                val decorrido =
                                    System.currentTimeMillis() - inicioCarregamento

                                if (decorrido >= TEMPO_MAXIMO_MS) {
                                    concluir(
                                        erro = IOException(
                                            "Não foi possível obter o HTML da página"
                                        )
                                    )
                                    return@evaluateJavascript
                                }
                            }

                            tarefa = Runnable {
                                verificarPagina(view)
                            }

                            main.postDelayed(
                                tarefa!!,
                                INTERVALO_VERIFICACAO_MS
                            )
                        }
                    }

                    w.webViewClient = object : WebViewClient() {

                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {

                            val esquema = request.url.scheme?.lowercase().orEmpty()

                            // Permite navegação HTTP/HTTPS.
                            // Não abre links intent:// ou esquemas de aplicativos.
                            return esquema != "http" && esquema != "https"
                        }

                        override fun onPageStarted(
                            view: WebView,
                            url: String,
                            favicon: android.graphics.Bitmap?
                        ) {
                            inicioCarregamento = System.currentTimeMillis()
                            ultimaVersao = ""
                            estabilidade = 0
                        }

                        override fun onPageFinished(
                            view: WebView,
                            url: String
                        ) {
                            if (finalizado.get()) return

                            if (inicioCarregamento == 0L) {
                                inicioCarregamento = System.currentTimeMillis()
                            }

                            tarefa?.let { main.removeCallbacks(it) }

                            tarefa = Runnable {
                                verificarPagina(view)
                            }

                            main.post(tarefa!!)
                        }
                    }

                    cont.invokeOnCancellation {
                        main.post {
                            if (finalizado.compareAndSet(false, true)) {
                                fechar()
                            }
                        }
                    }

                    w.loadUrl(url)

                } catch (e: Exception) {

                    try {
                        web?.stopLoading()
                        web?.destroy()
                    } catch (_: Exception) {
                    }

                    if (cont.isActive) {
                        cont.resumeWithException(
                            IOException(
                                "Navegador embutido indisponível: ${e.message}",
                                e
                            )
                        )
                    }
                }
            }
        }
    }
}