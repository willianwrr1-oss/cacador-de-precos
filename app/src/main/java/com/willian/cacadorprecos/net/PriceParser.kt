package com.willian.cacadorprecos.net

import com.willian.cacadorprecos.data.StoreDef
import com.willian.cacadorprecos.data.Txt
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL

/** Leitura de preços a partir do HTML das lojas. */
object PriceParser {

    data class PageInfo(val title: String, val price: Double)
    data class Hit(val title: String, val price: Double, val url: String)

    // ---------- valores ----------

    /** Converte "R$ 1.649,00", "1649.00", "1.649" etc. em número. */
    fun parseMoney(raw: String?): Double? {
        if (raw == null) return null
        val m = Regex("""\d[\d.,]*""").find(raw) ?: return null
        val s = m.value.trimEnd('.', ',')
        val lastDot = s.lastIndexOf('.')
        val lastCom = s.lastIndexOf(',')
        val limpo = when {
            lastDot >= 0 && lastCom >= 0 ->
                if (lastCom > lastDot) s.replace(".", "").replace(',', '.') else s.replace(",", "")
            lastCom >= 0 -> s.replace(".", "").replace(',', '.')
            lastDot >= 0 ->
                if (Regex("""^\d{1,3}(\.\d{3})+$""").matches(s)) s.replace(".", "") else s
            else -> s
        }
        val v = limpo.toDoubleOrNull() ?: return null
        return if (v > 0.0) v else null
    }

    // ---------- JSON-LD ----------

    private fun typeIs(o: JSONObject, t: String): Boolean {
        val ty = o.opt("@type")
        if (ty is String) return ty.equals(t, ignoreCase = true)
        if (ty is JSONArray) {
            for (i in 0 until ty.length()) if (ty.optString(i).equals(t, ignoreCase = true)) return true
        }
        return false
    }

    private fun collectProducts(node: Any?, out: MutableList<JSONObject>) {
        if (node is JSONObject) {
            if (typeIs(node, "Product")) out.add(node)
            val keys = node.keys()
            while (keys.hasNext()) collectProducts(node.opt(keys.next()), out)
        } else if (node is JSONArray) {
            for (i in 0 until node.length()) collectProducts(node.opt(i), out)
        }
    }

    private fun jsonLdProducts(doc: Document): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        for (s in doc.select("script[type=application/ld+json]")) {
            val txt = s.data().ifBlank { s.html() }.trim()
            if (txt.isEmpty()) continue
            try {
                collectProducts(JSONTokener(txt).nextValue(), out)
            } catch (e: Exception) {
                // JSON-LD malformado: ignora
            }
        }
        return out
    }

    private fun priceOf(offers: Any?): Double? {
        if (offers is JSONObject) {
            for (k in listOf("price", "lowPrice")) {
                if (offers.has(k)) {
                    val v = parseMoney(offers.opt(k)?.toString())
                    if (v != null) return v
                }
            }
            val spec = offers.optJSONObject("priceSpecification")
            if (spec != null) {
                val v = parseMoney(spec.opt("price")?.toString())
                if (v != null) return v
            }
            if (offers.has("offers")) return priceOf(offers.opt("offers"))
        } else if (offers is JSONArray) {
            var best: Double? = null
            for (i in 0 until offers.length()) {
                val v = priceOf(offers.opt(i))
                if (v != null && (best == null || v < best)) best = v
            }
            return best
        }
        return null
    }

    // ---------- página de um produto ----------

    fun fromProductPage(html: String): PageInfo? {
        val doc = Jsoup.parse(html)
        val ogTitle = doc.selectFirst("meta[property=og:title]")?.attr("content").orEmpty()
        val title = ogTitle.ifBlank { doc.title() }

        for (o in jsonLdProducts(doc)) {
            val p = priceOf(o.opt("offers"))
            if (p != null) return PageInfo(o.optString("name").ifBlank { title }, p)
        }

        val metas = listOf(
            "meta[property=product:price:amount]",
            "meta[property=og:price:amount]",
            "meta[itemprop=price]"
        )
        for (sel in metas) {
            val v = parseMoney(doc.selectFirst(sel)?.attr("content"))
            if (v != null) return PageInfo(title, v)
        }

        val sels = listOf(
            "#corePrice_feature_div .a-offscreen",
            "span.a-price span.a-offscreen",
            "[data-testid=price-value]",
            "[itemprop=price]",
            ".andes-money-amount__fraction"
        )
        for (sel in sels) {
            val el = doc.selectFirst(sel) ?: continue
            val v = parseMoney(el.attr("content").ifBlank { el.text() })
            if (v != null) return PageInfo(title, v)
        }

        // Último recurso: valores soltos no JSON embutido da página (Mercado Livre e outras)
        val bruto = doc.select("script").joinToString("\n") { it.data() }
        val padroes = listOf(
            Regex(""""current_price"\s*:\s*([0-9]+(?:\.[0-9]+)?)"""),
            Regex(""""price"\s*:\s*([0-9]+(?:\.[0-9]+)?)[,}]""")
        )
        for (re in padroes) {
            val v = re.find(bruto)?.groupValues?.get(1)?.toDoubleOrNull()
            if (v != null && v > 1.0) return PageInfo(title, v)
        }
        val tw = parseMoney(doc.selectFirst("meta[name=twitter:data1]")?.attr("content"))
        if (tw != null) return PageInfo(title, tw)
        return null
    }

    // ---------- página de resultados de busca ----------

    private fun absolute(base: String, u: String): String =
        try {
            URL(URL(base), u).toString()
        } catch (e: Exception) {
            u
        }

    private fun priceFromItem(el: Element, st: StoreDef): Double? {
        if (st.priceSel.isBlank()) return null
        if (st.host.contains("mercadolivre")) {
            val frac = el.selectFirst(st.priceSel)?.text() ?: return null
            val cents = el.selectFirst(".poly-price__current .andes-money-amount__cents")?.text()
                ?: el.selectFirst(".andes-money-amount__cents")?.text()
                ?: "00"
            return parseMoney(frac + "," + cents)
        }
        return parseMoney(el.selectFirst(st.priceSel)?.text())
    }

    fun parseSearch(html: String, baseUrl: String, st: StoreDef): List<Hit> {
        val doc = Jsoup.parse(html, baseUrl)
        val out = ArrayList<Hit>()

        for (o in jsonLdProducts(doc)) {
            val name = o.optString("name")
            val p = priceOf(o.opt("offers"))
            val u = o.optString("url")
            if (name.isNotBlank() && p != null) {
                out.add(Hit(name, p, if (u.isBlank()) baseUrl else absolute(baseUrl, u)))
            }
        }

        if (st.itemSel.isNotBlank()) {
            for (el in doc.select(st.itemSel)) {
                val title = if (st.titleSel.isBlank()) "" else el.selectFirst(st.titleSel)?.text().orEmpty()
                val price = priceFromItem(el, st)
                val link = (if (st.linkSel.isBlank()) null else el.selectFirst(st.linkSel))
                    ?: el.selectFirst("a[href]")
                val href = link?.absUrl("href").orEmpty()
                if (title.isNotBlank() && price != null) {
                    out.add(Hit(title, price, href.ifBlank { baseUrl }))
                }
            }
        }

        if (out.isEmpty()) out.addAll(generic(doc, baseUrl))
        return out.distinctBy { it.url + "|" + it.price }
    }

    /** Leitura genérica: links com texto de produto e um "R$" perto do link. */
    private fun generic(doc: Document, baseUrl: String): List<Hit> {
        val out = ArrayList<Hit>()
        val reMoney = Regex("""R\$\s*\d[\d.,]*""")
        for (a in doc.select("a[href]")) {
            var title = a.attr("title").ifBlank { a.text() }.trim()
            title = title.substringBefore("R$").trim()
            if (title.length < 12) continue
            var node: Element? = a
            var price: Double? = null
            var depth = 0
            while (node != null && depth < 5) {
                val t = node.text()
                if (t.length < 700) {
                    val m = reMoney.find(t)
                    if (m != null) {
                        price = parseMoney(m.value)
                        break
                    }
                }
                node = node.parent()
                depth++
            }
            val href = a.absUrl("href")
            if (price != null && href.isNotBlank()) out.add(Hit(title, price, href))
            if (out.size >= 60) break
        }
        return out
    }

    // ---------- comparação de nomes ----------

    private val STOP = setOf("de", "da", "do", "das", "dos", "com", "para", "em", "um", "uma", "por", "sem")

    private fun tokens(s: String): List<String> =
        Txt.norm(s).split(Regex("[^a-z0-9]+")).filter { it.length >= 2 && it !in STOP }

    private fun compact(s: String): String = Txt.norm(s).replace(Regex("[^a-z0-9]"), "")

    /**
     * O título encontrado na loja é do mesmo produto cadastrado?
     * O modelo precisa aparecer inteiro no título (ignorando hífens e espaços);
     * as palavras da descrição valem como confirmação adicional.
     */
    fun matches(desc: String, model: String, title: String): Boolean {
        val tn = Txt.norm(title)
        val modelo = compact(model)
        if (modelo.isNotEmpty() && !compact(title).contains(modelo)) return false

        val doModelo = tokens(model).toSet()
        val want = tokens(desc).filter { it !in doModelo }
        if (want.isEmpty()) return true
        val numeros = want.filter { w -> w.any { it.isDigit() } }
        if (numeros.any { !tn.contains(it) }) return false
        val palavras = want.filter { it !in numeros }
        if (palavras.isEmpty()) return true
        val ok = palavras.count { tn.contains(it) }
        val minimo = if (modelo.isNotEmpty()) 0.5 else 0.7
        return ok.toDouble() / palavras.size >= minimo
    }
}
