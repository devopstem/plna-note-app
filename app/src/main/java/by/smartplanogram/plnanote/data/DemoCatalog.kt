package by.smartplanogram.plnanote.data

import android.content.Context
import org.json.JSONObject

/**
 * Демо-товары из assets. Формат близок к Room/Firebase —
 * позже OfflineRepository просто отдаёт Room, демо остаётся fallback.
 */
object DemoCatalog {
    data class RackFace(
        val lengthMeters: Float = 1.5f,
        val heightMeters: Float = 1.8f,
        val depthMeters: Float = 0.65f
    )

    @Volatile private var loaded = false
    private var products: List<CatalogProduct> = emptyList()
    private var defaultFace = RackFace()

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            val text = context.applicationContext.assets
                .open(ASSET)
                .bufferedReader()
                .use { it.readText() }
            val root = JSONObject(text)
            val face = root.optJSONObject("defaultRackFace")
            if (face != null) {
                defaultFace = RackFace(
                    lengthMeters = face.optDouble("lengthMeters", 1.5).toFloat(),
                    heightMeters = face.optDouble("heightMeters", 1.8).toFloat(),
                    depthMeters = face.optDouble("depthMeters", 0.65).toFloat()
                )
            }
            val arr = root.getJSONArray("products")
            val list = ArrayList<CatalogProduct>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val hints = mutableListOf<String>()
                o.optJSONArray("hints")?.let { h ->
                    for (j in 0 until h.length()) hints += h.getString(j)
                }
                val pkg = o.optString("packageHint")
                if (pkg.isNotBlank()) hints.add(0, pkg)
                list += CatalogProduct(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    rackId = o.getString("rackId"),
                    shelf = o.optInt("shelf", 1),
                    x = o.optDouble("x").toFloat(),
                    y = o.optDouble("y").toFloat(),
                    unitHints = hints.distinct().ifEmpty { listOf("1 шт") },
                    category = o.optString("category", ""),
                    rackName = o.optString("rackName", o.getString("rackId")),
                    shelfCount = o.optInt("shelfCount", 5),
                    bay = o.optInt("bay", 1),
                    rackLengthM = defaultFace.lengthMeters,
                    rackHeightM = defaultFace.heightMeters,
                    rackDepthM = defaultFace.depthMeters,
                    isDemo = true
                )
            }
            products = list
            loaded = true
        }
    }

    fun all(context: Context): List<CatalogProduct> {
        ensureLoaded(context)
        return products
    }

    fun search(context: Context, query: String): List<CatalogProduct> {
        ensureLoaded(context)
        val q = query.trim().lowercase()
        if (q.isEmpty()) return products
        return products.filter {
            it.name.lowercase().contains(q) ||
                it.rackId.lowercase().contains(q) ||
                it.category.lowercase().contains(q)
        }
    }

    fun byId(context: Context, id: String): CatalogProduct? {
        ensureLoaded(context)
        return products.firstOrNull { it.id == id }
    }

    fun defaultFace(context: Context): RackFace {
        ensureLoaded(context)
        return defaultFace
    }

    private const val ASSET = "demo_catalog.json"
}
