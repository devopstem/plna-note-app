package by.smartplanogram.plnanote.data

import android.content.Context
import by.smartplanogram.plnanote.data.local.AppDatabase
import by.smartplanogram.plnanote.data.local.AppPrefsEntity
import by.smartplanogram.plnanote.data.local.ProductEntity
import by.smartplanogram.plnanote.data.local.StoreEntity
import by.smartplanogram.plnanote.data.sync.RtdbSyncEngine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Единая точка: Room офлайн + prefs. Firebase только через sync.
 */
class OfflineRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.get(app)
    private val syncEngine = RtdbSyncEngine(app, db)

    private val _prefs = MutableStateFlow(AppPrefsEntity())
    val prefs: StateFlow<AppPrefsEntity> = _prefs.asStateFlow()

    suspend fun ensureSynced(): RtdbSyncEngine.Result {
        // assets → Room сразу, затем попытка обновиться из Firebase
        val result = syncEngine.sync()
        refreshPrefs()
        return result
    }

    suspend fun refreshPrefs() {
        _prefs.value = db.prefs().get() ?: AppPrefsEntity()
    }

    suspend fun selectedStoreId(): String {
        val prefs = db.prefs().get() ?: AppPrefsEntity()
        if (prefs.selectedStoreId.isNotBlank()) return prefs.selectedStoreId
        return db.stores().all().firstOrNull()?.id.orEmpty()
    }

    suspend fun selectedStore(): StoreEntity? {
        val id = selectedStoreId()
        return if (id.isBlank()) null else db.stores().byId(id)
    }

    suspend fun allStores(): List<StoreEntity> = db.stores().all()

    suspend fun setSelectedStore(storeId: String) {
        val prefs = db.prefs().get() ?: AppPrefsEntity()
        db.prefs().upsert(prefs.copy(selectedStoreId = storeId))
        refreshPrefs()
    }

    suspend fun searchProducts(query: String): List<ProductEntity> {
        val storeId = selectedStoreId()
        if (storeId.isBlank() || query.isBlank()) return emptyList()
        return db.products().search(storeId, query.trim())
    }

    suspend fun mapJsonForSelectedStore(): String? {
        val storeId = selectedStoreId()
        if (storeId.isBlank()) return null
        return db.storeMaps().byStore(storeId)?.mapJson
    }

    fun productToCatalog(p: ProductEntity): CatalogProduct = CatalogProduct(
        id = p.id,
        name = p.name,
        rackId = p.rackId,
        shelf = p.shelf,
        x = p.x,
        y = p.y,
        unitHints = buildHints(p)
    )

    private fun buildHints(p: ProductEntity): List<String> {
        val base = mutableListOf<String>()
        if (p.packageHint.isNotBlank()) base += p.packageHint
        base += listOf("1 шт", "1л", "1.5л ПЭТ", "500г", "1кг", "3кг")
        return base.distinct()
    }

    companion object {
        @Volatile private var instance: OfflineRepository? = null

        fun get(context: Context): OfflineRepository {
            return instance ?: synchronized(this) {
                instance ?: OfflineRepository(context).also { instance = it }
            }
        }
    }
}
