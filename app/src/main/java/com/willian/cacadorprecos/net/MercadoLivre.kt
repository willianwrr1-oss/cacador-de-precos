package com.willian.cacadorprecos.net

import org.json.JSONObject
import java.net.URLDecoder

/** Apoio para links do Mercado Livre (inclusive os do tipo /up/MLBU... com item_id no endereço). */
object MercadoLivre {

    /** Extrai o número do anúncio (MLB3715351316 -> 3715351316) de qualquer formato de link. */
    fun itemId(url: String): String? {
        val dec = try {
            URLDecoder.decode(url, "UTF-8")
        } catch (e: Exception) {
            url
        }
        Regex("""item_id[:=]MLB-?(\d{6,})""").find(dec)?.let { return it.groupValues[1] }
        Regex("""MLB-?(\d{6,})""").find(dec)?.let { return it.groupValues[1] }
        return null
    }

    /** Consulta a API pública do anúncio. Retorna null se a resposta não trouxer preço. */
    fun viaApi(id: String): PriceParser.PageInfo? {
        val json = Http.get("https://api.mercadolibre.com/items/MLB" + id)
        val o = JSONObject(json)
        val price = o.optDouble("price", 0.0)
        if (price <= 0.0) return null
        return PriceParser.PageInfo(o.optString("title"), price)
    }
}
