package com.willian.cacadorprecos.data

/** Uma leitura de preço guardada no histórico. */
data class PricePoint(
    val time: Long,
    val price: Double,
    val store: String,
    val url: String
)

/** Link de uma loja para acompanhar um produto (fonte cadastrada). */
data class ProductLink(
    val store: String,
    val url: String
)

data class Product(
    val id: String,
    /** Descrição do produto (ex.: "Fone de ouvido sem fio Sony com cancelamento de ruído"). */
    val name: String,
    /** Modelo exato (ex.: "WH-1000XM5"). Faz a busca ser precisa. */
    val model: String = "",
    /** Por quantos dias as leituras ficam guardadas para comparação. */
    val keepDays: Int = 30,
    val category: String = "",
    val targetPrice: Double? = null,
    val alertPct: Int = 5,
    /** "ultimo", "media" ou "menor" */
    val compare: String = "ultimo",
    val searchRegistered: Boolean = true,
    val searchOpen: Boolean = true,
    val paused: Boolean = false,
    val links: List<ProductLink> = emptyList(),
    /** No máximo as 10 últimas leituras (a mais antiga primeiro). */
    val history: List<PricePoint> = emptyList(),
    /** Menor valor já verificado, guardado à parte das 10 últimas. */
    val lowest: PricePoint? = null,
    val lastAlertPrice: Double? = null,
    val lastCheck: Long = 0L,
    val lastError: String? = null
) {
    val current: PricePoint? get() = history.lastOrNull()

    /** Texto usado na busca das lojas: descrição curta + modelo. */
    fun searchQuery(): String {
        val desc = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.take(6).joinToString(" ")
        val m = model.trim()
        if (m.isEmpty()) return desc
        val descCompacta = Txt.norm(desc).replace(Regex("[^a-z0-9]"), "")
        val modeloCompacto = Txt.norm(m).replace(Regex("[^a-z0-9]"), "")
        return if (modeloCompacto.isNotEmpty() && descCompacta.contains(modeloCompacto)) desc else "$desc $m"
    }

    /** Variação do último preço em relação ao preço anterior diferente. */
    val changePct: Double?
        get() {
            if (history.size < 2) return null
            val a = history[history.size - 2].price
            val b = history[history.size - 1].price
            return (b - a) / a * 100.0
        }
}

data class AlertItem(
    val id: String,
    val time: Long,
    val productId: String,
    val title: String,
    val text: String,
    /** "queda", "alvo" ou "menor" */
    val kind: String,
    val unread: Boolean
)

data class Settings(
    val intervalMin: Int = 180,
    val defaultPct: Int = 5,
    val notifications: Boolean = true,
    val quiet: Boolean = false,
    val openSearch: Boolean = true,
    val disabledStores: Set<String> = emptySet(),
    val lastRun: Long = 0L
)

data class StoreStatus(
    val ok: Boolean,
    val time: Long,
    val message: String
)

data class AppState(
    val products: List<Product> = emptyList(),
    val alerts: List<AlertItem> = emptyList(),
    val settings: Settings = Settings(),
    val status: Map<String, StoreStatus> = emptyMap(),
    val checking: Boolean = false
)
