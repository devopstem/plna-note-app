package by.smartplanogram.plnanote.data.sync

import android.content.Context
import android.util.Log
import by.smartplanogram.plnanote.data.local.AppDatabase
import by.smartplanogram.plnanote.data.local.AppPrefsEntity
import by.smartplanogram.plnanote.data.local.ProductEntity
import by.smartplanogram.plnanote.data.local.StoreEntity
import by.smartplanogram.plnanote.data.local.StoreMapCacheEntity
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Из RTDB/assets берём только нужное:
 * name + rackId + shelf + x/y (+ кэш карты для C++).
 * Источник правды для UI — Room (офлайн).
 */
class RtdbSyncEngine(
    private val context: Context,
    private val db: AppDatabase = AppDatabase.get(context)
) {
    data class Result(
        val stores: Int,
        val products: Int,
        val source: String,
        val ok: Boolean,
        val error: String? = null
    )

    suspend fun sync(): Result = withContext(Dispatchers.IO) {
        // 1) assets всегда доступны офлайн; 2) Firebase — если проект совпадёт
        try {
            val (root, source) = loadAssetJson()
            val assetResult = applyRoot(root, source)
            tryRefreshFromFirebase()
            return@withContext assetResult
        } catch (t: Throwable) {
            Log.e(TAG, "asset sync failed", t)
            return@withContext try {
                val (root, source) = tryFirebase()
                applyRoot(root, source)
            } catch (t2: Throwable) {
                Result(0, 0, "none", false, t2.message)
            }
        }
    }

    private suspend fun tryRefreshFromFirebase() {
        try {
            val (root, source) = tryFirebase()
            applyRoot(root, source)
            Log.i(TAG, "refreshed from $source")
        } catch (t: Throwable) {
            Log.w(TAG, "Firebase refresh skipped: ${t.message}")
        }
    }

    private suspend fun tryFirebase(): Pair<JSONObject, String> {
        val snap = FirebaseDatabase.getInstance().reference.get().await()
        require(snap.exists()) { "empty firebase" }
        val map = snap.value as? Map<*, *> ?: error("bad firebase shape")
        val json = toJsonValue(map) as JSONObject
        require(json.has("stores") || json.has("catalog")) { "unexpected firebase tree" }
        return json to "firebase"
    }

    private fun loadAssetJson(): Pair<JSONObject, String> {
        val text = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        return JSONObject(text) to "assets"
    }

    private fun toJsonValue(value: Any?): Any {
        return when (value) {
            null -> JSONObject.NULL
            is Boolean, is Number, is String -> value
            is Map<*, *> -> {
                val o = JSONObject()
                value.forEach { (k, v) ->
                    if (k != null) o.put(k.toString(), toJsonValue(v))
                }
                o
            }
            is List<*> -> {
                val a = JSONArray()
                value.forEach { a.put(toJsonValue(it)) }
                a
            }
            else -> value.toString()
        }
    }

    private suspend fun applyRoot(root: JSONObject, source: String): Result {
        val now = System.currentTimeMillis()
        val stores = parseStores(root)
        db.stores().upsertAll(stores)

        var productCount = 0
        val catalogProducts = parseCatalogProducts(root)
        val storesNode = root.optJSONObject("stores") ?: JSONObject()
        val storeKeys = storesNode.keys()
        while (storeKeys.hasNext()) {
            val storeId = storeKeys.next()
            val storeObj = storesNode.optJSONObject(storeId) ?: continue
            val maps = storeObj.optJSONObject("store_maps") ?: continue
            val mapObj = maps.optJSONObject(storeId)
                ?: maps.keys().asSequence().firstOrNull()?.let { maps.optJSONObject(it) }
                ?: continue

            if (mapObj.has("layout") && (mapObj.has("racks") || mapObj.has("walls"))) {
                db.storeMaps().upsert(StoreMapCacheEntity(storeId, mapObj.toString(), now))
            }

            val racks = collectRacks(mapObj)
            if (racks.isEmpty()) continue

            val placed = placeProducts(storeId, catalogProducts, racks, now)
            db.products().clearStore(storeId)
            db.products().upsertAll(placed)
            productCount += placed.size
        }

        val prefs = db.prefs().get() ?: AppPrefsEntity()
        val selected = prefs.selectedStoreId.ifBlank { stores.firstOrNull()?.id.orEmpty() }
        db.prefs().upsert(prefs.copy(selectedStoreId = selected, lastSyncAt = now))

        return Result(stores.size, productCount, source, true)
    }

    private fun parseStores(root: JSONObject): List<StoreEntity> {
        val out = mutableListOf<StoreEntity>()
        val stores = root.optJSONObject("stores") ?: return out
        val keys = stores.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val store = stores.optJSONObject(id) ?: continue
            val indexRoot = store.optJSONObject("stores_index")
            val index = indexRoot?.optJSONObject(id)
                ?: indexRoot?.keys()?.asSequence()?.firstOrNull()?.let { indexRoot.optJSONObject(it) }
            out += StoreEntity(
                id = id,
                name = index?.optString("name")?.ifBlank { id } ?: id,
                address = index?.optString("address").orEmpty(),
                city = index?.optString("city").orEmpty(),
                latitude = index?.optDouble("latitude") ?: 0.0,
                longitude = index?.optDouble("longitude") ?: 0.0,
                radiusMeters = index?.optInt("radiusMeters") ?: 100
            )
        }
        return out
    }

    private data class CatalogItem(
        val barcode: String,
        val name: String,
        val category: String,
        val packageHint: String
    )

    private data class RackSlot(
        val id: String,
        val displayName: String,
        val zoneCode: String,
        val x: Float,
        val y: Float,
        val shelfCount: Int
    )

    private fun parseCatalogProducts(root: JSONObject): List<CatalogItem> {
        val out = mutableListOf<CatalogItem>()
        val tenants = root.optJSONObject("catalog")?.optJSONObject("tenants") ?: return out
        val seen = HashSet<String>()
        val tKeys = tenants.keys()
        while (tKeys.hasNext()) {
            val tenant = tenants.optJSONObject(tKeys.next()) ?: continue
            val products = tenant.optJSONObject("products") ?: continue
            val pKeys = products.keys()
            while (pKeys.hasNext()) {
                val barcode = pKeys.next()
                if (!seen.add(barcode)) continue
                val p = products.optJSONObject(barcode) ?: continue
                val name = p.optString("name")
                if (name.isBlank()) continue
                val volume = p.optDouble("volume_ml", Double.NaN)
                val pkg = p.optString("package_type")
                val hint = when {
                    !volume.isNaN() && volume > 0 -> {
                        val liters = volume / 1000.0
                        if (liters >= 1) "${trimNum(liters)}л" else "${volume.toInt()}мл"
                    }
                    pkg.isNotBlank() -> pkg
                    else -> ""
                }
                out += CatalogItem(barcode, name, p.optString("category"), hint)
            }
        }
        return out
    }

    private fun collectRacks(mapObj: JSONObject): List<RackSlot> {
        val out = mutableListOf<RackSlot>()
        fun eat(arr: JSONArray?) {
            if (arr == null) return
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                val levels = o.optJSONArray("levels")?.length() ?: 0
                out += RackSlot(
                    id = id,
                    displayName = o.optString("displayName", id),
                    zoneCode = o.optString("zoneCode"),
                    x = o.optDouble("posX", o.optDouble("rx")).toFloat(),
                    y = o.optDouble("posY", o.optDouble("ry")).toFloat(),
                    shelfCount = o.optInt("shelfCount", if (levels > 0) levels else 4).coerceAtLeast(1)
                )
            }
        }
        eat(mapObj.optJSONArray("racks"))
        eat(mapObj.optJSONArray("coolers"))
        eat(mapObj.optJSONArray("stands"))
        return out
    }

    private fun placeProducts(
        storeId: String,
        catalog: List<CatalogItem>,
        racks: List<RackSlot>,
        now: Long
    ): List<ProductEntity> {
        if (catalog.isEmpty() || racks.isEmpty()) return emptyList()
        return catalog.mapIndexed { index, item ->
            val rack = matchRack(item, racks) ?: racks[index % racks.size]
            val shelf = (index % rack.shelfCount) + 1
            ProductEntity(
                id = "${storeId}_${item.barcode}",
                storeId = storeId,
                name = item.name,
                rackId = rack.id,
                shelf = shelf,
                x = rack.x,
                y = rack.y,
                barcode = item.barcode,
                packageHint = item.packageHint,
                updatedAt = now
            )
        }
    }

    private fun matchRack(item: CatalogItem, racks: List<RackSlot>): RackSlot? {
        val hay = (item.name + " " + item.category).lowercase()
        val rules = listOf(
            listOf("кола", "вод", "напит") to listOf("WC", "вод", "кол"),
            listOf("квас") to listOf("KW", "квас"),
            listOf("снек", "чипс", "печень") to listOf("SN", "снек"),
            listOf("молоко", "сыр", "йогурт", "молоч") to listOf("DF", "молоч", "сыр"),
            listOf("морожен") to listOf("IC", "мороз")
        )
        for ((needles, zones) in rules) {
            if (needles.none { hay.contains(it) }) continue
            return racks.firstOrNull { r ->
                zones.any { z ->
                    r.zoneCode.equals(z, true) ||
                        r.displayName.lowercase().contains(z.lowercase()) ||
                        r.id.uppercase().startsWith(z.uppercase())
                }
            }
        }
        return null
    }

    private fun trimNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else String.format("%.1f", v)

    companion object {
        private const val TAG = "RtdbSync"
        private const val ASSET_NAME = "rtdb_export.json"
    }
}
