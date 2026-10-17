package by.smartplanogram.plnanote.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ProductEntity::class,
        StoreEntity::class,
        StoreMapCacheEntity::class,
        AppPrefsEntity::class,
        PurchaseEventEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun products(): ProductDao
    abstract fun stores(): StoreDao
    abstract fun storeMaps(): StoreMapDao
    abstract fun prefs(): AppPrefsDao
    abstract fun purchaseEvents(): PurchaseEventDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "plna_offline.db"
                ).fallbackToDestructiveMigration(true)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
