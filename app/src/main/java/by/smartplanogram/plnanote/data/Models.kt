package by.smartplanogram.plnanote.data

/**
 * Итог по позиции в зале (не GPS — явное решение покупателя).
 * — open: ещё идём
 * — bought: взял в корзину → история покупок
 * — skipped: нашёл, но не беру (несвежее и т.п.) — без истории
 * — missing: нет на полке / ошибка карты — без истории, сигнал для планограммы
 */
enum class ItemOutcome {
    OPEN,
    BOUGHT,
    SKIPPED,
    MISSING;

    companion object {
        fun fromRaw(raw: String?, checkedFallback: Boolean): ItemOutcome {
            when (raw?.lowercase()) {
                "bought" -> return BOUGHT
                "skipped" -> return SKIPPED
                "missing" -> return MISSING
                "open" -> return OPEN
            }
            return if (checkedFallback) BOUGHT else OPEN
        }
    }

    fun raw(): String = name.lowercase()

    /** Закрыта ли позиция для маршрута «следующий товар». */
    fun isDone(): Boolean = this != OPEN
}

data class ShoppingItem(
    val id: String,
    val name: String,
    val rackId: String,
    val shelf: Int,
    val x: Float,
    val y: Float,
    val quantityLabel: String = "",
    /** @deprecated используй outcome; оставлен для совместимости UI-галочки */
    val checked: Boolean = false,
    val outcome: ItemOutcome = if (checked) ItemOutcome.BOUGHT else ItemOutcome.OPEN
) {
    fun displayName(): String =
        if (quantityLabel.isBlank()) name else "$name · $quantityLabel"

    fun isDone(): Boolean = outcome.isDone() || checked

    fun withOutcome(next: ItemOutcome): ShoppingItem = copy(
        outcome = next,
        checked = next.isDone()
    )
}

data class ShoppingList(
    val id: String,
    var title: String,
    val createdAt: Long,
    var updatedAt: Long,
    val items: MutableList<ShoppingItem> = mutableListOf()
) {
    fun preview(): String {
        if (items.isEmpty()) return ""
        return items.joinToString(separator = "\n") { item ->
            val mark = when (item.outcome) {
                ItemOutcome.BOUGHT -> "☑"
                ItemOutcome.SKIPPED -> "⊘"
                ItemOutcome.MISSING -> "?"
                ItemOutcome.OPEN -> "☐"
            }
            "$mark ${item.displayName()}"
        }
    }
}

/**
 * Каталожный товар. Поля совместимы с Room/Firebase;
 * rack* — для UI «лица стеллажа» (демо и будущий sync).
 */
data class CatalogProduct(
    val id: String,
    val name: String,
    val rackId: String,
    val shelf: Int,
    val x: Float,
    val y: Float,
    val unitHints: List<String> = listOf("1 шт"),
    val category: String = "",
    val rackName: String = "",
    val shelfCount: Int = 5,
    val bay: Int = 1,
    val rackLengthM: Float = 1.5f,
    val rackHeightM: Float = 1.8f,
    val rackDepthM: Float = 0.65f,
    val isDemo: Boolean = false
)
