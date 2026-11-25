package com.willian.cacadorprecos.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Guarda tudo em um arquivo JSON dentro do aparelho (filesDir/dados.json).
 * Qualquer alteração passa por [update], que é protegida contra acessos simultâneos
 * (interface e verificação em segundo plano).
 */
object Repo {
    private var file: File? = null
    private val _state = MutableStateFlow(AppState())
    val state: StateFlow<AppState> = _state.asStateFlow()

    fun newId(): String = UUID.randomUUID().toString()

    @Synchronized
    fun start(ctx: Context) {
        if (file != null) return
        val f = File(ctx.applicationContext.filesDir, "dados.json")
        file = f
        _state.value = try {
            if (f.exists()) parse(f.readText()) else AppState()
        } catch (e: Exception) {
            AppState()
        }
    }

    @Synchronized
    fun update(block: (AppState) -> AppState) {
        val novo = block(_state.value)
        _state.value = novo
        save(novo)
    }

    // ---------- operações ----------

    fun addProduct(p: Product) = update { it.copy(products = it.products + p) }

    fun editProduct(id: String, f: (Product) -> Product) = update { s ->
        s.copy(products = s.products.map { if (it.id == id) f(it) else it })
    }

    fun removeProduct(id: String) = update { s ->
        s.copy(
            products = s.products.filter { it.id != id },
            alerts = s.alerts.filter { it.productId != id }
        )
    }

    fun setSettings(f: (Settings) -> Settings) = update { it.copy(settings = f(it.settings)) }

    fun markAlertsRead() = update { s -> s.copy(alerts = s.alerts.map { it.copy(unread = false) }) }

    fun clearHistory() = update { s ->
        s.copy(
            products = s.products.map {
                it.copy(history = emptyList(), lowest = null, lastAlertPrice = null, lastError = null)
            },
            alerts = emptyList()
        )
    }

    // ---------- gravação ----------

    private fun save(s: AppState) {
        val f = file ?: return
        try {
            val tmp = File(f.path + ".tmp")
            tmp.writeText(toJson(s).toString())
            if (!tmp.renameTo(f)) {
                f.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (e: Exception) {
            // sem espaço ou sem permissão: mantém o estado em memória
        }
    }

    // ---------- JSON ----------

    private fun JSONObject.dbl(k: String): Double? = if (has(k) && !isNull(k)) getDouble(k) else null

    private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

    private fun <T> JSONArray?.objs(f: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        val out = ArrayList<T>()
        for (i in 0 until length()) {
            val o = optJSONObject(i)
            if (o != null) out.add(f(o))
        }
        return out
    }

    private fun pointToJson(p: PricePoint): JSONObject =
        JSONObject().put("t", p.time).put("p", p.price).put("s", p.store).put("u", p.url)

    private fun pointFromJson(o: JSONObject): PricePoint =
        PricePoint(o.optLong("t"), o.optDouble("p"), o.optString("s"), o.optString("u"))

    private fun productToJson(p: Product): JSONObject {
        val o = JSONObject()
        o.put("id", p.id)
        o.put("name", p.name)
        o.put("model", p.model)
        o.put("keep", p.keepDays)
        o.put("cat", p.category)
        if (p.targetPrice != null) o.put("target", p.targetPrice)
        o.put("pct", p.alertPct)
        o.put("cmp", p.compare)
        o.put("reg", p.searchRegistered)
        o.put("open", p.searchOpen)
        o.put("paused", p.paused)
        o.put("links", JSONArray().also { a ->
            p.links.forEach { a.put(JSONObject().put("s", it.store).put("u", it.url)) }
        })
        o.put("hist", JSONArray().also { a -> p.history.forEach { a.put(pointToJson(it)) } })
        if (p.lowest != null) o.put("low", pointToJson(p.lowest))
        if (p.lastAlertPrice != null) o.put("lastAlert", p.lastAlertPrice)
        o.put("lastCheck", p.lastCheck)
        if (p.lastError != null) o.put("err", p.lastError)
        return o
    }

    private fun productFromJson(o: JSONObject): Product = Product(
        id = o.optString("id"),
        name = o.optString("name"),
        model = o.optString("model"),
        keepDays = o.optInt("keep", 30),
        category = o.optString("cat"),
        targetPrice = o.dbl("target"),
        alertPct = o.optInt("pct", 5),
        compare = o.optString("cmp", "ultimo"),
        searchRegistered = o.optBoolean("reg", true),
        searchOpen = o.optBoolean("open", true),
        paused = o.optBoolean("paused", false),
        links = o.optJSONArray("links").objs { ProductLink(it.optString("s"), it.optString("u")) },
        history = o.optJSONArray("hist").objs { pointFromJson(it) },
        lowest = o.optJSONObject("low")?.let { pointFromJson(it) },
        lastAlertPrice = o.dbl("lastAlert"),
        lastCheck = o.optLong("lastCheck"),
        lastError = o.str("err")
    )

    private fun toJson(s: AppState): JSONObject {
        val o = JSONObject()
        o.put("products", JSONArray().also { a -> s.products.forEach { a.put(productToJson(it)) } })
        o.put("alerts", JSONArray().also { a ->
            s.alerts.forEach {
                a.put(
                    JSONObject().put("id", it.id).put("t", it.time).put("pid", it.productId)
                        .put("title", it.title).put("text", it.text).put("kind", it.kind)
                        .put("unread", it.unread)
                )
            }
        })
        val st = s.settings
        o.put(
            "settings", JSONObject()
                .put("interval", st.intervalMin).put("pct", st.defaultPct)
                .put("notif", st.notifications).put("quiet", st.quiet).put("open", st.openSearch)
                .put("off", JSONArray().also { a -> st.disabledStores.forEach { a.put(it) } })
                .put("lastRun", st.lastRun)
        )
        val ss = JSONObject()
        s.status.forEach { (k, v) ->
            ss.put(k, JSONObject().put("ok", v.ok).put("t", v.time).put("m", v.message))
        }
        o.put("status", ss)
        return o
    }

    private fun parse(text: String): AppState {
        val o = JSONObject(text)
        val products = o.optJSONArray("products").objs { productFromJson(it) }
        val alerts = o.optJSONArray("alerts").objs {
            AlertItem(
                it.optString("id"), it.optLong("t"), it.optString("pid"),
                it.optString("title"), it.optString("text"), it.optString("kind"),
                it.optBoolean("unread", false)
            )
        }
        val so = o.optJSONObject("settings")
        val settings = if (so == null) Settings() else {
            val off = HashSet<String>()
            val arr = so.optJSONArray("off")
            if (arr != null) for (i in 0 until arr.length()) off.add(arr.optString(i))
            Settings(
                intervalMin = so.optInt("interval", 180),
                defaultPct = so.optInt("pct", 5),
                notifications = so.optBoolean("notif", true),
                quiet = so.optBoolean("quiet", false),
                openSearch = so.optBoolean("open", true),
                disabledStores = off,
                lastRun = so.optLong("lastRun")
            )
        }
        val status = HashMap<String, StoreStatus>()
        val sj = o.optJSONObject("status")
        if (sj != null) {
            val keys = sj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                val v = sj.optJSONObject(k) ?: continue
                status[k] = StoreStatus(v.optBoolean("ok"), v.optLong("t"), v.optString("m"))
            }
        }
        return AppState(products, alerts, settings, status, false)
    }
}
