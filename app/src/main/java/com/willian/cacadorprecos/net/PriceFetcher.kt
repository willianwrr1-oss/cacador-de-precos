package com.willian.cacadorprecos.net

import com.willian.cacadorprecos.data.Product
import com.willian.cacadorprecos.data.Settings
import com.willian.cacadorprecos.data.StoreStatus
import com.willian.cacadorprecos.data.Stores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/** Busca preços por links cadastrados e por pesquisa nas lojas. */
object PriceFetcher {

    data class Quote(
        val store: String,
        val price: Double,
        val url: String,
        val title: String,
        val similar: Boolean = false
    )

    data class Result(
        val quotes: List<Quote>,
        val status: Map<String, StoreStatus>
    )

    suspend fun fetch(p: Product, s: Settings): Result =
        withContext(Dispatchers.IO) {

            val quotes = ArrayList<Quote>()
            val status = HashMap<String, StoreStatus>()

            val now = System.currentTimeMillis()
            val ref = p.history.lastOrNull()?.price
            val comLink = HashSet<String>()

            // -------------------------------------------------
            // FRENTE 1: LINKS CADASTRADOS
            // -------------------------------------------------

            if (p.searchRegistered) {

                for (l in p.links) {

                    if (l.store in s.disabledStores) continue

                    comLink.add(l.store)

                    try {

                        val info = lerLink(l.store, l.url)

                        if (info == null) {

                            status[l.store] = StoreStatus(
                                false,
                                now,
                                "Preço não encontrado na página"
                            )

                        } else {

                            quotes.add(
                                Quote(
                                    l.store,
                                    info.price,
                                    l.url,
                                    info.title
                                )
                            )

                            status[l.store] = StoreStatus(
                                true,
                                now,
                                "OK"
                            )
                        }

                    } catch (e: Exception) {

                        status[l.store] = StoreStatus(
                            false,
                            now,
                            erro(e)
                        )
                    }

                    delay(800)
                }
            }

            // -------------------------------------------------
            // FRENTE 2: BUSCA NAS LOJAS
            // -------------------------------------------------

            if (s.openSearch && p.searchOpen) {

                for (st in Stores.ALL) {

                    if (
                        st.name in s.disabledStores ||
                        st.name in comLink
                    ) continue

                    try {

                        val url = Stores.searchUrl(
                            st,
                            s,
                            p.searchQuery()
                        )

                        var html: String
                        var usouWebView = false

                        // Primeira tentativa: HTTP direto.
                        try {

                            html = Http.get(url)

                        } catch (_: Exception) {

                            // Segunda tentativa: navegador embutido.
                            html = WebHtml.get(url)
                            usouWebView = true
                        }

                        var todos = PriceParser
                            .parseSearch(html, url, st)
                            .filter { plausivel(it.price, ref) }

                        // Se HTTP retornou HTML, mas não encontrou anúncios,
                        // tenta novamente usando JavaScript no WebView.
                        if (todos.isEmpty() && !usouWebView) {

                            try {

                                val htmlNavegador = WebHtml.get(url)

                                if (htmlNavegador.isNotBlank()) {

                                    val resultadosNavegador =
                                        PriceParser
                                            .parseSearch(
                                                htmlNavegador,
                                                url,
                                                st
                                            )
                                            .filter {
                                                plausivel(it.price, ref)
                                            }

                                    if (resultadosNavegador.isNotEmpty()) {

                                        html = htmlNavegador
                                        todos = resultadosNavegador
                                    }
                                }

                            } catch (_: Exception) {
                                // Mantém os resultados da tentativa HTTP.
                            }
                        }

                        // 1. Modelo no título.
                        var exatos = todos.filter {
                            PriceParser.matches(
                                p.name,
                                p.model,
                                it.title
                            )
                        }

                        // 2. Modelo no texto do cartão.
                        if (exatos.isEmpty()) {

                            exatos = todos.filter {

                                !PriceParser.rejected(
                                    p.name,
                                    p.model,
                                    it.title
                                ) &&
                                PriceParser.hasModel(
                                    p.model,
                                    it.card
                                ) &&
                                PriceParser.descScore(
                                    p.name,
                                    p.model,
                                    it.title
                                ) >= 0.4
                            }
                        }

                        // 3. Modelo na página individual do produto.
                        if (exatos.isEmpty()) {

                            val candidatos = todos
                                .filter {
                                    !PriceParser.rejected(
                                        p.name,
                                        p.model,
                                        it.title
                                    ) &&
                                    it.url.startsWith("http") &&
                                    it.url != url
                                }
                                .map {
                                    it to PriceParser.descScore(
                                        p.name,
                                        p.model,
                                        it.title
                                    )
                                }
                                .filter {
                                    it.second >= 0.5
                                }
                                .sortedByDescending {
                                    it.second
                                }
                                .take(3)
                                .map {
                                    it.first
                                }

                            val conferidos =
                                ArrayList<PriceParser.Hit>()

                            for (c in candidatos) {

                                if (paginaTemModelo(c.url, p.model)) {
                                    conferidos.add(c)
                                }

                                delay(400)
                            }

                            exatos = conferidos
                        }

                        // Anúncios parecidos são mantidos separados.
                        val parecidos =
                            if (exatos.isEmpty() && p.allowSimilar) {

                                todos.filter {
                                    PriceParser.similar(
                                        p.name,
                                        it.title
                                    )
                                }

                            } else {
                                emptyList()
                            }

                        val best = exatos.minByOrNull { it.price }
                        val similar = parecidos.minByOrNull { it.price }

                        when {

                            best != null -> {

                                quotes.add(
                                    Quote(
                                        st.name,
                                        best.price,
                                        best.url,
                                        best.title
                                    )
                                )

                                status[st.name] = StoreStatus(
                                    true,
                                    now,
                                    "OK"
                                )
                            }

                            similar != null -> {

                                quotes.add(
                                    Quote(
                                        st.name + " (similar)",
                                        similar.price,
                                        similar.url,
                                        similar.title,
                                        true
                                    )
                                )

                                status[st.name] = StoreStatus(
                                    true,
                                    now,
                                    "Só anúncio parecido, sem o modelo no título"
                                )
                            }

                            todos.isEmpty() -> {

                                val titulo = Jsoup
                                    .parse(html)
                                    .title()
                                    .take(50)

                                status[st.name] = StoreStatus(
                                    false,
                                    now,
                                    "Nenhum anúncio lido na página \"$titulo\""
                                )
                            }

                            else -> {

                                status[st.name] = StoreStatus(
                                    false,
                                    now,
                                    todos.size.toString() +
                                        " anúncios, nenhum com o modelo " +
                                        p.model +
                                        ". Ex.: \"" +
                                        todos.first().title.take(45) +
                                        "\""
                                )
                            }
                        }

                    } catch (e: Exception) {

                        status[st.name] = StoreStatus(
                            false,
                            now,
                            erro(e)
                        )
                    }

                    delay(800)
                }
            }

            // -------------------------------------------------
            // FILTRO FINAL DE PREÇOS
            // -------------------------------------------------

            val filtrados =
                if (ref == null && quotes.size >= 3) {

                    val ord = quotes
                        .map { it.price }
                        .sorted()

                    val mediana = ord[ord.size / 2]

                    quotes.filter {
                        it.price >= mediana * 0.4
                    }

                } else {
                    quotes
                }

            Result(filtrados, status)
        }

    // ---------------------------------------------------------
    // LEITURA DE LINK CADASTRADO
    // ---------------------------------------------------------

    private suspend fun lerLink(
        store: String,
        url: String
    ): PriceParser.PageInfo? {

        var falha: Exception? = null

        val id =
            if (store == "Mercado Livre") {
                MercadoLivre.itemId(url)
            } else {
                null
            }

        // API pública do Mercado Livre.
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

        if (id != null) {
            paginas.add(
                "https://produto.mercadolivre.com.br/MLB-$id"
            )
        }

        for (u in paginas) {

            // Primeira tentativa: HTTP.
            try {

                val html = Http.get(u)

                val r = PriceParser.fromProductPage(html)

                if (r != null) return r

            } catch (e: Exception) {

                falha = e
            }

            // Segunda tentativa: WebView.
            try {

                val html = WebHtml.get(u)

                val r = PriceParser.fromProductPage(html)

                if (r != null) return r

                val doc = Jsoup.parse(html)

                val titulo = doc.title().take(60)

                val aberta = Regex(
                    """<!--URL:(.*?)-->"""
                )
                    .find(html)
                    ?.groupValues
                    ?.get(1)
                    ?.take(70)
                    ?: ""

                falha = IOException(
                    "Preço não encontrado. Página aberta: " +
                        "\"$titulo\" $aberta"
                )

            } catch (e: Exception) {

                falha = e
            }
        }

        if (falha != null) throw falha

        return null
    }

    // ---------------------------------------------------------
    // VERIFICAÇÃO DO MODELO NA PÁGINA DO PRODUTO
    // ---------------------------------------------------------

    private suspend fun paginaTemModelo(
        url: String,
        model: String
    ): Boolean {

        // Primeiro verifica o HTML direto.
        val htmlHttp = try {
            Http.get(url)
        } catch (_: Exception) {
            null
        }

        if (
            !htmlHttp.isNullOrBlank() &&
            conteudoTemModelo(htmlHttp, model)
        ) {
            return true
        }

        // Se o HTTP não trouxe o modelo, tenta a página renderizada.
        val htmlNavegador = try {
            WebHtml.get(url)
        } catch (_: Exception) {
            null
        }

        if (htmlNavegador.isNullOrBlank()) {
            return false
        }

        return conteudoTemModelo(htmlNavegador, model)
    }

    /**
     * Analisa somente o conteúdo principal do produto.
     * Não utiliza texto de produtos relacionados, recomendações ou vitrines.
     */
    private fun conteudoTemModelo(
        html: String,
        model: String
    ): Boolean {

        return try {

            val doc = Jsoup.parse(html)

            val partes = ArrayList<String>()

            // Metadados descritivos da página.
            doc.select(
                "meta[name=description], " +
                    "meta[property=og:description], " +
                    "meta[name=twitter:description]"
            ).forEach {

                partes.add(it.attr("content"))
            }

            // Título principal.
            partes.add(doc.select("h1").text())

            // Remove áreas que não representam o produto principal.
            doc.select(
                "script, style, noscript, nav, footer, header, aside, " +
                    "[class*=related], [class*=Related], " +
                    "[class*=carousel], [class*=Carousel], " +
                    "[class*=recommend], [class*=Recommend], " +
                    "[class*=shelf], [class*=Shelf], " +
                    "[id*=related], [id*=Related], " +
                    "[id*=recommend], [id*=Recommend], " +
                    "[id*=carousel], [id*=Carousel]"
            ).remove()

            // Descrição principal.
            val seletoresDescricao = listOf(
                "main",
                "[role=main]",
                "#description",
                "#descricao",
                "[id*=description]",
                "[id*=Description]",
                "[id*=descricao]",
                "[class*=description]",
                "[class*=Description]",
                "[class*=descricao]",
                "[class*=Descricao]",
                "[itemprop=description]"
            )

            for (seletor in seletoresDescricao) {

                doc.select(seletor).forEach { elemento ->

                    if (!estaEmAreaRelacionada(elemento)) {
                        partes.add(elemento.text())
                    }
                }
            }

            // Ficha técnica e características.
            val seletoresTecnicos = listOf(
                "table",
                "dl",
                "[class*=spec]",
                "[class*=Spec]",
                "[class*=ficha]",
                "[class*=Ficha]",
                "[class*=caracter]",
                "[class*=Caracter]",
                "[class*=tecnic]",
                "[class*=Tecnic]",
                "[class*=attribute]",
                "[class*=Attribute]",
                "[id*=spec]",
                "[id*=Spec]",
                "[id*=detail]",
                "[id*=Detail]",
                "[id*=caracter]",
                "[id*=Caracter]"
            )

            for (seletor in seletoresTecnicos) {

                doc.select(seletor).forEach { elemento ->

                    if (!estaEmAreaRelacionada(elemento)) {
                        partes.add(elemento.text())
                    }
                }
            }

            val textoProduto = partes
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" ")

            PriceParser.hasModel(
                model,
                textoProduto
            )

        } catch (_: Exception) {

            false
        }
    }

    /**
     * Proteção adicional contra elementos dentro de vitrines relacionadas.
     */
    private fun estaEmAreaRelacionada(
        elemento: org.jsoup.nodes.Element
    ): Boolean {

        val termos = listOf(
            "related",
            "carousel",
            "recommend",
            "shelf",
            "similar",
            "upsell",
            "cross-sell"
        )

        val elementos = listOf(elemento) + elemento.parents()

        return elementos.any { el ->

            val identificacao = (
                el.className() + " " +
                    el.id()
                ).lowercase()

            termos.any {
                identificacao.contains(it)
            }
        }
    }

    // ---------------------------------------------------------
    // VALIDAÇÃO DE PREÇOS
    // ---------------------------------------------------------

    private fun plausivel(
        price: Double,
        ref: Double?
    ): Boolean {

        if (ref == null) return true

        return price >= ref * 0.4 &&
            price <= ref * 2.5
    }

    private fun erro(e: Exception): String {

        val m = e.message ?: e.javaClass.simpleName

        return if (m.length > 80) {
            m.substring(0, 80)
        } else {
            m
        }
    }
}