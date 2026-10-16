package by.smartplanogram.plnanote.data

data class ShoppingItem(
    val id: String,
    val name: String,
    val rackId: String,
    val shelf: Int,
    val x: Float,
    val y: Float,
    val quantityLabel: String = "",
    val checked: Boolean = false
) {
    fun displayName(): String =
        if (quantityLabel.isBlank()) name else "$name · $quantityLabel"
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
        return items.joinToString(separator = "\n") { "☐ ${it.displayName()}" }
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
