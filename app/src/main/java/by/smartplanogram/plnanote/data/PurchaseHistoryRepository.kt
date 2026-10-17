package by.smartplanogram.plnanote.data

import android.content.Context
import by.smartplanogram.plnanote.data.local.AppDatabase
import by.smartplanogram.plnanote.data.local.PurchaseEventEntity
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * Локальная история покупок + простые инсайты без нейросети.
 * Правило: частота, день недели, давность.
 */
class PurchaseHistoryRepository private constructor(context: Context) {
    private val app = context.applicationContext
    private val db = AppDatabase.get(app)

    suspend fun recordChecked(
        item: ShoppingItem,
        listId: String,
        storeId: String
    ) {
        db.purchaseEvents().insert(
            PurchaseEventEntity(
                productKey = keyOf(item.name),
                name = item.name.trim(),
                rackId = item.rackId,
                shelf = item.shelf,
                x = item.x,
                y = item.y,
                quantityLabel = item.quantityLabel,
                listId = listId,
                storeId = storeId,
                purchasedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun eventCount(): Int = db.purchaseEvents().count()

    suspend fun clearAll() {
        db.purchaseEvents().clearAll()
    }

    suspend fun buildSnapshot(lookbackDays: Int = 120): PurchaseSnapshot {
        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(lookbackDays.toLong())
        val events = db.purchaseEvents().since(since)
        if (events.isEmpty()) {
            return PurchaseSnapshot(
                totalEvents = 0,
                lookbackDays = lookbackDays,
                top = emptyList(),
                overdue = emptyList(),
                habitualToday = emptyList()
            )
        }

        val byKey = events.groupBy { it.productKey }
        val stats = byKey.values.map { group ->
            val sorted = group.sortedBy { it.purchasedAt }
            val last = sorted.last()
            val intervals = sorted.zipWithNext { a, b ->
                TimeUnit.MILLISECONDS.toDays(b.purchasedAt - a.purchasedAt).toDouble()
            }.filter { it > 0.0 }
            val avgDays = if (intervals.isNotEmpty()) intervals.average() else null
            // индексы 0..6 (Calendar.DAY_OF_WEEK даёт 1..7)
            val weekdayCounts = IntArray(7)
            sorted.forEach { e ->
                weekdayCounts[weekdayIndex(e.purchasedAt)]++
            }
            val favWeekday = weekdayCounts.indices.maxByOrNull { weekdayCounts[it] } ?: 0
            ProductStat(
                productKey = last.productKey,
                name = last.name,
                rackId = last.rackId,
                shelf = last.shelf,
                x = last.x,
                y = last.y,
                quantityLabel = last.quantityLabel,
                purchaseCount = sorted.size,
                lastAt = last.purchasedAt,
                avgIntervalDays = avgDays,
                favoriteWeekday = favWeekday
            )
        }

        val top = stats.sortedByDescending { it.purchaseCount }.take(8)
        val now = System.currentTimeMillis()
        val todayWeekday = weekdayIndex(now)

        val overdue = stats
            .filter { it.purchaseCount >= 2 && it.avgIntervalDays != null }
            .mapNotNull { s ->
                val avg = s.avgIntervalDays ?: return@mapNotNull null
                val daysSince = TimeUnit.MILLISECONDS.toDays(now - s.lastAt).toDouble()
                if (daysSince >= avg * 1.4 && daysSince >= 2.0) {
                    InsightCard(
                        kind = InsightKind.OVERDUE,
                        stat = s,
                        detailDays = daysSince.roundToInt(),
                        expectedDays = avg.roundToInt()
                    )
                } else null
            }
            .sortedByDescending { it.detailDays }
            .take(5)

        val habitualToday = stats
            .filter { it.purchaseCount >= 2 && it.favoriteWeekday == todayWeekday }
            .filter { TimeUnit.MILLISECONDS.toDays(now - it.lastAt) >= 5 }
            .sortedByDescending { it.purchaseCount }
            .take(4)
            .map {
                InsightCard(
                    kind = InsightKind.HABITUAL_TODAY,
                    stat = it,
                    detailDays = todayWeekday,
                    expectedDays = it.avgIntervalDays?.roundToInt() ?: 0
                )
            }

        return PurchaseSnapshot(
            totalEvents = events.size,
            lookbackDays = lookbackDays,
            top = top,
            overdue = overdue,
            habitualToday = habitualToday
        )
    }

    /** Первая подсказка для баннера на экране списков. */
    suspend fun primarySuggestion(
        namesAlreadyInLists: Set<String>
    ): InsightCard? {
        val snap = buildSnapshot()
        val blocked = namesAlreadyInLists.map { keyOf(it) }.toSet()
        return (snap.habitualToday + snap.overdue)
            .firstOrNull { it.stat.productKey !in blocked }
    }

    companion object {
        fun keyOf(name: String): String = name.trim().lowercase()

        /** 0=вс … 6=сб — безопасный индекс для IntArray(7). */
        private fun weekdayIndex(epochMs: Long): Int {
            val cal = Calendar.getInstance()
            cal.timeInMillis = epochMs
            // Calendar.SUNDAY=1 … SATURDAY=7 → 0..6
            return (cal.get(Calendar.DAY_OF_WEEK) - 1).coerceIn(0, 6)
        }

        @Volatile
        private var instance: PurchaseHistoryRepository? = null

        fun get(context: Context): PurchaseHistoryRepository {
            return instance ?: synchronized(this) {
                instance ?: PurchaseHistoryRepository(context).also { instance = it }
            }
        }
    }
}

data class ProductStat(
    val productKey: String,
    val name: String,
    val rackId: String,
    val shelf: Int,
    val x: Float,
    val y: Float,
    val quantityLabel: String,
    val purchaseCount: Int,
    val lastAt: Long,
    val avgIntervalDays: Double?,
    val favoriteWeekday: Int
) {
    fun toCatalogProduct(): CatalogProduct = CatalogProduct(
        id = "hist-$productKey",
        name = name,
        rackId = rackId,
        shelf = shelf,
        x = x,
        y = y,
        unitHints = if (quantityLabel.isBlank()) listOf("1 шт") else listOf(quantityLabel),
        rackName = rackId
    )
}

enum class InsightKind { OVERDUE, HABITUAL_TODAY }

data class InsightCard(
    val kind: InsightKind,
    val stat: ProductStat,
    val detailDays: Int,
    val expectedDays: Int
)

data class PurchaseSnapshot(
    val totalEvents: Int,
    val lookbackDays: Int,
    val top: List<ProductStat>,
    val overdue: List<InsightCard>,
    val habitualToday: List<InsightCard>
)
