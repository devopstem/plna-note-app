package by.smartplanogram.plnanote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import by.smartplanogram.plnanote.data.CatalogProduct
import by.smartplanogram.plnanote.data.OfflineRepository
import by.smartplanogram.plnanote.data.ShoppingItem
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.databinding.ActivityListDetailBinding
import by.smartplanogram.plnanote.databinding.ItemListProductBinding
import by.smartplanogram.plnanote.databinding.ItemSearchProductBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset
import by.smartplanogram.plnanote.ui.map.MapFragment
import by.smartplanogram.plnanote.ui.search.QuantityBottomSheet
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ListDetailActivity : AppCompatActivity(), QuantityBottomSheet.Listener {

    private lateinit var binding: ActivityListDetailBinding
    private lateinit var repo: ShoppingListRepository
    private lateinit var offline: OfflineRepository
    private lateinit var listId: String
    private lateinit var itemsAdapter: ItemsAdapter
    private lateinit var searchAdapter: SearchAdapter
    private var searchJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.listDetailRoot.applyTopContentInset(8)

        repo = ShoppingListRepository.get(this)
        offline = OfflineRepository.get(this)
        listId = intent.getStringExtra(EXTRA_LIST_ID) ?: run {
            finish()
            return
        }

        itemsAdapter = ItemsAdapter(onToggle = { item ->
            repo.toggleItem(listId, item.id)
            refresh()
        })
        binding.itemsRecycler.layoutManager = LinearLayoutManager(this)
        binding.itemsRecycler.adapter = itemsAdapter

        searchAdapter = SearchAdapter { product ->
            QuantityBottomSheet.newInstance(product)
                .show(supportFragmentManager, QuantityBottomSheet.TAG)
        }
        binding.searchResults.layoutManager = LinearLayoutManager(this)
        binding.searchResults.adapter = searchAdapter

        binding.btnBack.setOnClickListener {
            saveTitle()
            finish()
        }
        binding.btnSave.setOnClickListener {
            saveTitle()
            Toast.makeText(this, R.string.save, Toast.LENGTH_SHORT).show()
        }
        binding.btnDelete.setOnClickListener {
            repo.delete(listId)
            finish()
        }
        binding.btnShowOnMap.setOnClickListener {
            saveTitle()
            startActivity(
                Intent(this, MainActivity::class.java)
                    .putExtra(MapFragment.EXTRA_ROUTE_LIST_ID, listId)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
            finish()
        }

        binding.searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString().orEmpty()
                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    val results = offline.searchProducts(q).map { offline.productToCatalog(it) }
                    searchAdapter.submit(results)
                    binding.searchResults.visibility =
                        if (results.isEmpty()) View.GONE else View.VISIBLE
                }
            }
        })

        refresh()
        if (intent.getBooleanExtra(EXTRA_OPEN_SEARCH, false)) {
            binding.searchInput.requestFocus()
        }
    }

    override fun onPause() {
        saveTitle() // системная «Назад» / сворачивание не должны терять название
        super.onPause()
    }

    override fun onQuantityChosen(product: CatalogProduct, quantityLabel: String) {
        repo.addItem(listId, product, quantityLabel)
        binding.searchInput.setText("")
        binding.searchResults.visibility = View.GONE
        refresh()
    }

    private fun saveTitle() {
        val list = repo.get(listId) ?: return
        val title = binding.listNameInput.text?.toString()?.trim().orEmpty()
        if (title.isNotEmpty() && title != list.title) {
            list.title = title
            repo.update(list)
        }
    }

    private fun refresh() {
        val list = repo.get(listId) ?: run {
            finish()
            return
        }
        if (binding.listNameInput.text.isNullOrEmpty() ||
            binding.listNameInput.text.toString() != list.title
        ) {
            binding.listNameInput.setText(list.title)
        }
        itemsAdapter.submit(list.items)
        binding.productsSectionTitle.text = if (list.items.isEmpty()) {
            getString(R.string.list_empty_products)
        } else {
            getString(R.string.list_products_section, list.items.size)
        }
        binding.btnShowOnMap.visibility = if (list.items.isEmpty()) View.GONE else View.VISIBLE
    }

    private class ItemsAdapter(
        private val onToggle: (ShoppingItem) -> Unit
    ) : RecyclerView.Adapter<ItemsAdapter.VH>() {
        private val items = mutableListOf<ShoppingItem>()

        fun submit(data: List<ShoppingItem>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemListProductBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        inner class VH(private val binding: ItemListProductBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(item: ShoppingItem) {
                binding.itemName.text = item.displayName()
                binding.itemLocation.text = binding.root.context.getString(
                    R.string.shelf_format,
                    item.rackId,
                    item.shelf
                )
                binding.itemCheck.setOnCheckedChangeListener(null)
                binding.itemCheck.isChecked = item.checked
                binding.itemCheck.setOnCheckedChangeListener { _, _ -> onToggle(item) }
            }
        }
    }

    private class SearchAdapter(
        private val onPick: (CatalogProduct) -> Unit
    ) : RecyclerView.Adapter<SearchAdapter.VH>() {
        private val items = mutableListOf<CatalogProduct>()

        fun submit(data: List<CatalogProduct>) {
            items.clear()
            items.addAll(data)
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val binding = ItemSearchProductBinding.inflate(
                LayoutInflater.from(parent.context), parent, false
            )
            return VH(binding)
        }

        override fun onBindViewHolder(holder: VH, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size

        inner class VH(private val binding: ItemSearchProductBinding) :
            RecyclerView.ViewHolder(binding.root) {
            fun bind(product: CatalogProduct) {
                binding.productName.text = product.name
                binding.productLocation.text = binding.root.context.getString(
                    R.string.shelf_format,
                    product.rackId,
                    product.shelf
                )
                binding.btnAddProduct.setOnClickListener { onPick(product) }
                binding.root.setOnClickListener { onPick(product) }
            }
        }
    }

    companion object {
        private const val EXTRA_LIST_ID = "list_id"
        private const val EXTRA_OPEN_SEARCH = "open_search"

        fun intent(context: Context, listId: String, openSearch: Boolean = false): Intent {
            return Intent(context, ListDetailActivity::class.java)
                .putExtra(EXTRA_LIST_ID, listId)
                .putExtra(EXTRA_OPEN_SEARCH, openSearch)
        }
    }
}
