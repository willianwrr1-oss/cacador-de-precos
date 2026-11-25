package com.willian.cacadorprecos.work

import android.content.Context
import com.willian.cacadorprecos.data.AlertItem
import com.willian.cacadorprecos.data.Fmt
import com.willian.cacadorprecos.data.PricePoint
import com.willian.cacadorprecos.data.Product
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.net.PriceFetcher
import java.util.Calendar

/** Roda a verificação de preços, atualiza o histórico e dispara os alertas. */
object Checker {

    suspend fun runAll(ctx: Context) {
        Repo.update { it.copy(checking = true) }
        try {
            val ids = Repo.state.value.products.filter { !it.paused }.map { it.id }
            for (id in ids) checkOne(ctx, id)
        } finally {
            Repo.update { s ->
                s.copy(checking = false, settings = s.settings.copy(lastRun = System.currentTimeMillis()))
            }
        }
    }

    suspend fun runOne(ctx: Context, id: String) {
        Repo.update { it.copy(checking = true) }
        try {
            checkOne(ctx, id)
        } finally {
            Repo.update { it.copy(checking = false) }
        }
    }

    private suspend fun checkOne(ctx: Context, id: String) {
        val before = Repo.state.value
        val p0 = before.products.firstOrNull { it.id == id } ?: return
        val res = PriceFetcher.fetch(p0, before.settings)
        val now = System.currentTimeMillis()
        // alerta e histórico usam anúncios confirmados pelo modelo; parecidos só entram se não houver nenhum
        val best = res.quotes.filter { !it.similar }.minByOrNull { it.price } ?: res.quotes.minByOrNull { it.price }
        // um preço por loja (o menor de cada), do mais barato para o mais caro
        val ofertas = res.quotes.groupBy { it.store }
            .map { (_, qs) -> qs.minBy { it.price } }
            .sortedBy { it.price }
            .map { PricePoint(now, it.price, it.store, it.url, it.title) }
        var fired: AlertItem? = null

        val falhas = res.status.entries.filter { !it.value.ok }
            .joinToString("; ") { it.key + ": " + it.value.message }

        Repo.update { st ->
            val p = st.products.firstOrNull { it.id == id } ?: return@update st
            val status = st.status + res.status
            if (best == null) {
                val err = if (res.status.isEmpty()) "Nenhuma fonte ativa (confira se há link cadastrado e a loja está ligada em Fontes)"
                else "Nenhum preço encontrado. " + falhas
                return@update st.copy(
                    status = status,
                    products = st.products.map { if (it.id == id) it.copy(lastCheck = now, lastError = err, offers = emptyList()) else it }
                )
            }
            val (np0, alert) = record(p, best, now)
            val np = np0.copy(
                offers = ofertas,
                lastError = if (ofertas.size < 3 && falhas.isNotEmpty()) falhas else null
            )
            fired = alert
            st.copy(
                status = status,
                products = st.products.map { if (it.id == id) np else it },
                alerts = if (alert != null) (listOf(alert) + st.alerts).take(60) else st.alerts
            )
        }

        val a = fired
        if (a != null) notifyIfAllowed(ctx, a)
    }

    private fun refLabel(compare: String): String = when (compare) {
        "media" -> "média"
        "menor" -> "menor do período"
        else -> "antes"
    }

    /**
     * Grava a leitura no histórico (período escolhido no cadastro) e atualiza o menor valor.
     * Se o preço e a loja forem iguais aos da última leitura, só atualiza o horário dela.
     */
    private fun record(p: Product, q: PriceFetcher.Quote, now: Long): Pair<Product, AlertItem?> {
        val hist = p.history
        val last = hist.lastOrNull()
        val base: Double? = when (p.compare) {
            "media" -> if (hist.isEmpty()) null else hist.map { it.price }.average()
            "menor" -> if (hist.isEmpty()) null else hist.minOf { it.price }
            else -> last?.price
        }

        val comNovo: List<PricePoint> =
            if (last != null && last.price == q.price && last.store == q.store) {
                hist.dropLast(1) + last.copy(time = now, url = q.url)
            } else {
                hist + PricePoint(now, q.price, q.store, q.url, q.title)
            }
        // guarda só o período escolhido no cadastro (o menor valor histórico fica à parte)
        val limite = now - p.keepDays * 86_400_000L
        var newHist = comNovo.filter { it.time >= limite }
        if (newHist.isEmpty()) newHist = comNovo.takeLast(1)
        newHist = newHist.takeLast(500)

        val oldLow = p.lowest
        val isNewLow = oldLow == null || q.price < oldLow.price
        val low: PricePoint? = if (isNewLow) PricePoint(now, q.price, q.store, q.url, q.title) else oldLow

        val drop = base != null && q.price < base && q.price <= base * (1 - p.alertPct / 100.0)
        val alvo = p.targetPrice
        val target = alvo != null && q.price <= alvo
        val fresh = p.lastAlertPrice == null || p.lastAlertPrice != q.price

        var alert: AlertItem? = null
        var lastAlert = p.lastAlertPrice
        if (fresh && (drop || target || (isNewLow && hist.isNotEmpty()))) {
            val kind: String
            val title: String
            val text: String
            if (drop && base != null) {
                val pct = (base - q.price) / base * 100.0
                kind = "queda"
                title = p.name + " caiu " + Fmt.pctPlain(pct)
                text = q.store + " · " + Fmt.money(q.price) + " (" + refLabel(p.compare) + " " + Fmt.money(base) + ")"
            } else if (target && alvo != null) {
                kind = "alvo"
                title = p.name + " atingiu o preço-alvo"
                text = q.store + " · " + Fmt.money(q.price) + " (alvo " + Fmt.money(alvo) + ")"
            } else {
                kind = "menor"
                title = p.name + " no menor preço registrado"
                text = q.store + " · " + Fmt.money(q.price)
            }
            alert = AlertItem(Repo.newId(), now, p.id, title, text, kind, true)
            lastAlert = q.price
        }

        val np = p.copy(
            history = newHist,
            lowest = low,
            lastAlertPrice = lastAlert,
            lastCheck = now,
            lastError = null
        )
        return Pair(np, alert)
    }

    private fun notifyIfAllowed(ctx: Context, a: AlertItem) {
        val s = Repo.state.value.settings
        if (!s.notifications) return
        if (s.quiet) {
            val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            if (h >= 22 || h < 7) return
        }
        Notifier.show(ctx, a)
    }
}
