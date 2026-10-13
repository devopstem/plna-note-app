package by.smartplanogram.plnanote.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Только то, что нужно для поиска и маршрута. */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val name: String,
    val rackId: String,
    val shelf: Int,
    val x: Float,
    val y: Float,
    val barcode: String = "",
    val packageHint: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "stores")
data class StoreEntity(
    @PrimaryKey val id: String,
    val name: String,
    val address: String,
    val city: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val radiusMeters: Int = 100,
    val updatedAt: Long = 0L
)

/** Кэш JSON чертежа для C++ ядра (офлайн). */
@Entity(tableName = "store_maps")
data class StoreMapCacheEntity(
    @PrimaryKey val storeId: String,
    val mapJson: String,
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "app_prefs")
data class AppPrefsEntity(
    @PrimaryKey val id: Int = 1,
    val selectedStoreId: String = "",
    val displayName: String = "",
    val avatarUri: String = "",
    val themeMode: String = "dark", // dark | light | system
    val languageCode: String = "ru", // ru | en | be
    val lastSyncAt: Long = 0L
)
