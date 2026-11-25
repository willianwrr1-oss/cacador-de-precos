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

    data class Quote(val store: String, val price: Double, val url: String, val title: String)

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
                    val hits = PriceParser.parseSearch(html, url, st)
                        .filter { PriceParser.matches(p.name, p.model, it.title) && plausivel(it.price, ref) }
                    val best = hits.minByOrNull { it.price }
                    if (best != null) {
                        quotes.add(Quote(st.name, best.price, best.url, best.title))
                        status[st.name] = StoreStatus(true, now, "OK")
                    } else {
                        status[st.name] = StoreStatus(false, now, "Produto não encontrado")
                    }
                } catch (e: Exception) {
                    status[st.name] = StoreStatus(false, now, erro(e))
                }
                delay(800)
            }
        }
        Result(quotes, status)
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
                val r = PriceParser.fromProductPage(WebHtml.get(u))
                if (r != null) return r
                falha = IOException("Preço não encontrado na página (navegador embutido)")
            } catch (e: Exception) {
                falha = e
            }
        }
        if (falha != null) throw falha
        return null
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
