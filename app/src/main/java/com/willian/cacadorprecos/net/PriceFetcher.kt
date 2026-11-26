package com.willian.cacadorprecos.net

import com.willian.cacadorprecos.data.Product
import com.willian.cacadorprecos.data.Settings
import com.willian.cacadorprecos.data.StoreStatus
import com.willian.cacadorprecos.data.Stores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException

/** Busca os preços de um produto nas duas frentes: links cadastrados e busca em grandes lojas. */
object PriceFetcher {

    /** [similar] = anúncio parecido, mas sem o modelo no título (não dispara alerta sozinho). */
    data class Quote(val store: String, val price: Double, val url: String, val title: String, val similar: Boolean = false)

    data class Result(val quotes: List<Quote>, val status: Map<String, StoreStatus>)

    suspend fun fetch(p: Product, s: Settings): Result = withContext(Dispatchers.IO) {
        val quotes = ArrayList<Quote>()
        val status = HashMap<String, StoreStatus>()
        val now = System.currentTimeMillis()
        val ref = p.history.lastOrNull()?.price
        val comLink = HashSet<String>()

        // Frente 1: fontes cadastradas (links do produto)
        if (p.searchRegistered) {
            for (l in p.links) {
                if (l.store in s.disabledStores) continue
                comLink.add(l.store)
                try {
                    val info = lerLink(l.store, l.url)
                    if (info == null) {
                        status[l.store] = StoreStatus(false, now, "Preço não encontrado na página")
                    } else {
                        quotes.add(Quote(l.store, info.price, l.url, info.title))
                        status[l.store] = StoreStatus(true, now, "OK")
                    }
                } catch (e: Exception) {
                    status[l.store] = StoreStatus(false, now, erro(e))
                }
                delay(800)
            }
        }

        // Frente 2: busca em grandes lojas
        if (s.openSearch && p.searchOpen) {
            for (st in Stores.ALL) {
                if (st.name in s.disabledStores || st.name in comLink) continue
                try {
                    val url = Stores.searchUrl(st, s, p.searchQuery())
                    val html = try {
                        Http.get(url)
                    } catch (e: Exception) {
                        WebHtml.get(url)
                    }
                    val todos = PriceParser.parseSearch(html, url, st).filter { plausivel(it.price, ref) }
                    // 1) modelo no título  2) modelo no texto do cartão  3) modelo dentro da página do anúncio
                    var exatos = todos.filter { PriceParser.matches(p.name, p.model, it.title) }
                    if (exatos.isEmpty()) {
                        exatos = todos.filter {
                            !PriceParser.rejected(p.name, p.model, it.title) &&
                                PriceParser.hasModel(p.model, it.card) &&
                                PriceParser.descScore(p.name, p.model, it.title) >= 0.4
                        }
                    }
                    if (exatos.isEmpty()) {
                        val candidatos = todos
                            .filter { !PriceParser.rejected(p.name, p.model, it.title) && it.url.startsWith("http") && it.url != url }
                            .map { it to PriceParser.descScore(p.name, p.model, it.title) }
                            .filter { it.second >= 0.5 }
                            .sortedByDescending { it.second }
                            .take(3)
                            .map { it.first }
                        val conferidos = ArrayList<PriceParser.Hit>()
                        for (c in candidatos) {
                            if (paginaTemModelo(c.url, p.model)) conferidos.add(c)
                            delay(400)
                        }
                        exatos = conferidos
                    }
                    val parecidos = if (exatos.isEmpty() && p.allowSimilar) todos.filter { PriceParser.similar(p.name, it.title) } else emptyList()
                    val best = exatos.minByOrNull { it.price }
                    val similar = parecidos.minByOrNull { it.price }
                    if (best != null) {
                        quotes.add(Quote(st.name, best.price, best.url, best.title))
                        status[st.name] = StoreStatus(true, now, "OK")
                    } else if (similar != null) {
                        quotes.add(Quote(st.name + " (similar)", similar.price, similar.url, similar.title, true))
                        status[st.name] = StoreStatus(true, now, "Só anúncio parecido, sem o modelo no título")
                    } else if (todos.isEmpty()) {
                        val tit = org.jsoup.Jsoup.parse(html).title().take(50)
                        status[st.name] = StoreStatus(false, now, "Nenhum anúncio lido na página \"" + tit + "\"")
                    } else {
                        status[st.name] = StoreStatus(
                            false, now,
                            todos.size.toString() + " anúncios, nenhum com o modelo " + p.model +
                                ". Ex.: \"" + todos.first().title.take(45) + "\""
                        )
                    }
                } catch (e: Exception) {
                    status[st.name] = StoreStatus(false, now, erro(e))
                }
                delay(800)
            }
        }
        // Sem histórico, descarta preços muito abaixo dos demais (provável acessório ou outro produto)
        val filtrados = if (ref == null && quotes.size >= 3) {
            val ord = quotes.map { it.price }.sorted()
            val mediana = ord[ord.size / 2]
            quotes.filter { it.price >= mediana * 0.4 }
        } else quotes
        Result(filtrados, status)
    }

    /** Lê um link cadastrado. No Mercado Livre tenta primeiro a API pública do anúncio, depois a página. */
    private suspend fun lerLink(store: String, url: String): PriceParser.PageInfo? {
        var falha: Exception? = null
        val id = if (store == "Mercado Livre") MercadoLivre.itemId(url) else null
        if (id != null) {
            try {
                val r = MercadoLivre.viaApi(id)
                if (r != null) return r
            } catch (e: Exception) {
                falha = e
            }
        }
        val paginas = ArrayList<String>()
        paginas.add(url)
        if (id != null) paginas.add("https://produto.mercadolivre.com.br/MLB-" + id)
        for (u in paginas) {
            try {
                val r = PriceParser.fromProductPage(Http.get(u))
                if (r != null) return r
            } catch (e: Exception) {
                falha = e
            }
            // Bloqueio (403) ou preço montado por JavaScript: tenta como o navegador do celular
            try {
                val html = WebHtml.get(u)
                val r = PriceParser.fromProductPage(html)
                if (r != null) return r
                val titulo = org.jsoup.Jsoup.parse(html).title().take(60)
                val aberta = Regex("""<!--URL:(.*?)-->""").find(html)?.groupValues?.get(1)?.take(70) ?: ""
                falha = IOException("Preço não encontrado. Página aberta: \"" + titulo + "\" " + aberta)
            } catch (e: Exception) {
                falha = e
            }
        }
        if (falha != null) throw falha
        return null
    }

    /** Abre a página do anúncio e confere se o modelo aparece nela (ficha técnica, descrição). */
    private suspend fun paginaTemModelo(url: String, model: String): Boolean {
        val html = try {
            Http.get(url)
        } catch (e: Exception) {
            try {
                WebHtml.get(url)
            } catch (x: Exception) {
                return false
            }
        }
        // Só o produto principal: título da página e ficha técnica. Vitrines de "produtos relacionados"
        // e o termo pesquisado guardado no código da página não contam.
        return try {
            val doc = org.jsoup.Jsoup.parse(html)
            doc.select("script, style, noscript").remove()
            val partes = ArrayList<String>()
            partes.add(doc.select("h1").text())
            doc.select(
                "table, dl, [class*=spec], [class*=Spec], [class*=ficha], [class*=caracter], [class*=Caracter], " +
                    "[class*=tecnic], [class*=Tecnic], [class*=attribute], [class*=Attribute], [id*=spec], [id*=detail]"
            ).forEach { el ->
                if (el.select("[class*=related], [class*=carousel], [class*=recommend], [class*=shelf]").isEmpty()) {
                    partes.add(el.text())
                }
            }
            PriceParser.hasModel(model, partes.joinToString(" "))
        } catch (e: Exception) {
            false
        }
    }

    /** Descarta preços muito fora do que o produto costuma custar (acessórios, peças, erros). */
    private fun plausivel(price: Double, ref: Double?): Boolean {
        if (ref == null) return true
        return price >= ref * 0.4 && price <= ref * 2.5
    }

    private fun erro(e: Exception): String {
        val m = e.message ?: e.javaClass.simpleName
        return if (m.length > 80) m.substring(0, 80) else m
    }
}
