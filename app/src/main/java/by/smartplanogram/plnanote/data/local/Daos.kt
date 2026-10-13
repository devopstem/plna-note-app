package by.smartplanogram.plnanote.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ProductDao {
    @Query(
        """
        SELECT * FROM products
        WHERE storeId = :storeId AND name LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    suspend fun search(storeId: String, query: String, limit: Int = 40): List<ProductEntity>

    @Query("SELECT * FROM products WHERE storeId = :storeId ORDER BY name COLLATE NOCASE")
    suspend fun forStore(storeId: String): List<ProductEntity>

    @Query("SELECT COUNT(*) FROM products WHERE storeId = :storeId")
    suspend fun countForStore(storeId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ProductEntity>)

    @Query("DELETE FROM products WHERE storeId = :storeId")
    suspend fun clearStore(storeId: String)
}

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<StoreEntity>

    @Query("SELECT * FROM stores WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): StoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StoreEntity>)
}

@Dao
interface StoreMapDao {
    @Query("SELECT * FROM store_maps WHERE storeId = :storeId LIMIT 1")
    suspend fun byStore(storeId: String): StoreMapCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: StoreMapCacheEntity)
}

@Dao
interface AppPrefsDao {
    @Query("SELECT * FROM app_prefs WHERE id = 1 LIMIT 1")
    suspend fun get(): AppPrefsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(prefs: AppPrefsEntity)
}
