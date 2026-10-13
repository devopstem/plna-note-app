package by.smartplanogram.plnanote.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class ShoppingListRepository private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val lists = linkedMapOf<String, ShoppingList>()
    private val listeners = mutableListOf<() -> Unit>()

    init {
        load()
        // старые демо-данные из прошлых версий убираем
        if (lists.keys.removeAll(setOf("demo-1", "demo-2"))) persistAndNotify()
    }

    fun observe(listener: () -> Unit) {
        listeners += listener
    }

    fun removeObserver(listener: () -> Unit) {
        listeners -= listener
    }

    fun all(): List<ShoppingList> = lists.values.sortedByDescending { it.updatedAt }

    fun count(): Int = lists.size

    fun get(id: String): ShoppingList? = lists[id]

    fun create(title: String): ShoppingList {
        val now = System.currentTimeMillis()
        val list = ShoppingList(
            id = UUID.randomUUID().toString(),
            title = title,
            createdAt = now,
            updatedAt = now
        )
        lists[list.id] = list
        persistAndNotify()
        return list
    }

    fun update(list: ShoppingList) {
        list.updatedAt = System.currentTimeMillis()
        lists[list.id] = list
        persistAndNotify()
    }

    fun delete(id: String) {
        lists.remove(id)
        persistAndNotify()
    }

    fun addItem(
        listId: String,
        product: CatalogProduct,
        quantityLabel: String
    ): ShoppingList? {
        val list = lists[listId] ?: return null
        list.items.add(
            ShoppingItem(
                id = UUID.randomUUID().toString(),
                name = product.name,
                rackId = product.rackId,
                shelf = product.shelf,
                x = product.x,
                y = product.y,
                quantityLabel = quantityLabel
            )
        )
        update(list)
        return list
    }

    fun toggleItem(listId: String, itemId: String) {
        val list = lists[listId] ?: return
        val idx = list.items.indexOfFirst { it.id == itemId }
        if (idx < 0) return
        val item = list.items[idx]
        list.items[idx] = item.copy(checked = !item.checked)
        update(list)
    }

    private fun persistAndNotify() {
        val arr = JSONArray()
        lists.values.forEach { list ->
            val obj = JSONObject()
            obj.put("id", list.id)
            obj.put("title", list.title)
            obj.put("createdAt", list.createdAt)
            obj.put("updatedAt", list.updatedAt)
            val items = JSONArray()
            list.items.forEach { item ->
                items.put(
                    JSONObject()
                        .put("id", item.id)
                        .put("name", item.name)
                        .put("rackId", item.rackId)
                        .put("shelf", item.shelf)
                        .put("x", item.x.toDouble())
                        .put("y", item.y.toDouble())
                        .put("quantityLabel", item.quantityLabel)
                        .put("checked", item.checked)
                )
            }
            obj.put("items", items)
            arr.put(obj)
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
        listeners.toList().forEach { it.invoke() }
    }

    private fun load() {
        val raw = prefs.getString(KEY, null) ?: return
        lists.clear()
        val arr = JSONArray(raw)
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val itemsArr = obj.optJSONArray("items") ?: JSONArray()
            val items = mutableListOf<ShoppingItem>()
            for (j in 0 until itemsArr.length()) {
                val it = itemsArr.getJSONObject(j)
                items.add(
                    ShoppingItem(
                        id = it.getString("id"),
                        name = it.getString("name"),
                        rackId = it.optString("rackId"),
                        shelf = it.optInt("shelf", 1),
                        x = it.optDouble("x", 0.0).toFloat(),
                        y = it.optDouble("y", 0.0).toFloat(),
                        quantityLabel = it.optString("quantityLabel", ""),
                        checked = it.optBoolean("checked", false)
                    )
                )
            }
            val list = ShoppingList(
                id = obj.getString("id"),
                title = obj.getString("title"),
                createdAt = obj.optLong("createdAt"),
                updatedAt = obj.optLong("updatedAt"),
                items = items
            )
            lists[list.id] = list
        }
    }

    companion object {
        private const val PREFS = "plna_lists"
        private const val KEY = "lists_json"

        @Volatile
        private var instance: ShoppingListRepository? = null

        fun get(context: Context): ShoppingListRepository {
            return instance ?: synchronized(this) {
                instance ?: ShoppingListRepository(context).also { instance = it }
            }
        }
    }
}
