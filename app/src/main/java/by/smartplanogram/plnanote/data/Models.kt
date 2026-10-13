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

data class CatalogProduct(
    val id: String,
    val name: String,
    val rackId: String,
    val shelf: Int,
    val x: Float,
    val y: Float,
    val unitHints: List<String> = listOf("1 шт")
)
