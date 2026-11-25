package com.willian.cacadorprecos.data

import java.net.URI
import java.net.URLEncoder
import java.text.Normalizer

object Txt {
    fun norm(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

    fun slug(s: String): String = norm(s).replace(Regex("[^a-z0-9]+"), "-").trim('-')

    fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
}

/**
 * Loja de grande porte usada na busca aberta.
 * Os seletores CSS (itemSel, titleSel, priceSel, linkSel) servem para ler a página de resultados.
 * Se ficarem em branco, ou se o site mudar, o app tenta uma leitura genérica.
 */
data class StoreDef(
    val name: String,
    val host: String,
    val searchUrl: (String) -> String,
    val itemSel: String = "",
    val titleSel: String = "",
    val priceSel: String = "",
    val linkSel: String = ""
)

object Stores {
    val ALL: List<StoreDef> = listOf(
        StoreDef(
            "Amazon", "amazon.com.br",
            { q -> "https://www.amazon.com.br/s?k=" + Txt.enc(q) },
            "div[data-component-type=s-search-result]", "h2", "span.a-price span.a-offscreen", "h2 a"
        ),
        StoreDef(
            "Mercado Livre", "mercadolivre.com.br",
            { q -> "https://lista.mercadolivre.com.br/" + Txt.slug(q) },
            "li.ui-search-layout__item, div.ui-search-result__wrapper",
            "a.poly-component__title, h2.ui-search-item__title, h2",
            ".poly-price__current .andes-money-amount__fraction, .andes-money-amount__fraction",
            "a.poly-component__title, a.ui-search-link"
        ),
        StoreDef(
            "Magazine Luiza", "magazineluiza.com.br",
            { q -> "https://www.magazineluiza.com.br/busca/" + Txt.enc(q).replace("+", "%20") + "/" },
            "li[data-testid=product-card-container], a[data-testid=product-card-container]",
            "h2[data-testid=product-title]", "p[data-testid=price-value]", "a"
        ),
        StoreDef(
            "KaBuM!", "kabum.com.br",
            { q -> "https://www.kabum.com.br/busca/" + Txt.slug(q) },
            "article.productCard", "span.nameCard", "span.priceCard", "a.productLink"
        ),
        StoreDef("Americanas", "americanas.com.br", { q -> "https://www.americanas.com.br/busca/" + Txt.slug(q) }),
        StoreDef("Casas Bahia", "casasbahia.com.br", { q -> "https://www.casasbahia.com.br/" + Txt.slug(q) + "/b" }),
        StoreDef("Fast Shop", "fastshop.com.br", { q -> "https://www.fastshop.com.br/web/s/" + Txt.enc(q) })
    )

    /** Descobre o nome da loja pelo endereço do link. */
    fun nameFor(url: String): String {
        val host = try {
            URI(url.trim()).host?.lowercase() ?: ""
        } catch (e: Exception) {
            ""
        }
        for (s in ALL) {
            if (host == s.host || host.endsWith("." + s.host)) return s.name
        }
        val limpo = host.removePrefix("www.")
        return if (limpo.isBlank()) "Loja" else limpo
    }
}
