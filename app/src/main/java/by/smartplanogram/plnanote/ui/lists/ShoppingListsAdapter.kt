package by.smartplanogram.plnanote.ui.lists

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.ShoppingList
import by.smartplanogram.plnanote.databinding.ItemShoppingListBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ShoppingListsAdapter(
    private val onOpen: (ShoppingList) -> Unit,
    private val onDelete: (ShoppingList) -> Unit
) : RecyclerView.Adapter<ShoppingListsAdapter.VH>() {

    private val items = mutableListOf<ShoppingList>()
    private val dateFmt = SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru"))

    fun submit(data: List<ShoppingList>) {
        items.clear()
        items.addAll(data)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemShoppingListBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(private val binding: ItemShoppingListBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(list: ShoppingList) {
            binding.listTitle.text = list.title
            val preview = list.preview()
            binding.listPreview.text =
                if (preview.isBlank()) binding.root.context.getString(R.string.list_no_text) else preview
            binding.listDate.text = dateFmt.format(Date(list.updatedAt))
            binding.root.setOnClickListener { onOpen(list) }
            binding.btnDeleteList.setOnClickListener { onDelete(list) }
        }
    }
}
