package by.smartplanogram.plnanote.ui.lists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import by.smartplanogram.plnanote.ListDetailActivity
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.InsightCard
import by.smartplanogram.plnanote.data.InsightKind
import by.smartplanogram.plnanote.data.PurchaseHistoryRepository
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.databinding.FragmentListsBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset
import kotlinx.coroutines.launch

class ListsFragment : Fragment() {
    private var _binding: FragmentListsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: ShoppingListRepository
    private lateinit var history: PurchaseHistoryRepository
    private lateinit var adapter: ShoppingListsAdapter
    private var pendingSuggestion: InsightCard? = null
    private val refresh = {
        val lists = repo.all()
        adapter.submit(lists)
        binding.listsEmpty.visibility = if (lists.isEmpty()) View.VISIBLE else View.GONE
        binding.listsRecycler.visibility = if (lists.isEmpty()) View.GONE else View.VISIBLE
        loadHabitBanner()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentListsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        repo = ShoppingListRepository.get(requireContext())
        history = PurchaseHistoryRepository.get(requireContext())
        binding.listsContent.applyTopContentInset(10)
        adapter = ShoppingListsAdapter(
            onOpen = { list ->
                startActivity(ListDetailActivity.intent(requireContext(), list.id))
            },
            onDelete = { list -> repo.delete(list.id) }
        )
        binding.listsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.listsRecycler.adapter = adapter
        val createList = {
            val list = repo.create(getString(R.string.list_detail_title))
            startActivity(ListDetailActivity.intent(requireContext(), list.id))
        }
        binding.fabAddList.setOnClickListener { createList() }
        binding.btnListsEmptyCta.setOnClickListener { createList() }
        binding.btnSearchLists.setOnClickListener {
            val list = repo.all().firstOrNull() ?: repo.create(getString(R.string.list_detail_title))
            startActivity(ListDetailActivity.intent(requireContext(), list.id, openSearch = true))
        }
        binding.btnHabitAdd.setOnClickListener { addSuggestion() }
        repo.observe(refresh)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun loadHabitBanner() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = runCatching {
                val namesInLists = repo.all()
                    .flatMap { it.items }
                    .filter { !it.isDone() }
                    .map { it.name }
                    .toSet()
                history.primarySuggestion(namesInLists)
            }
            if (_binding == null) return@launch
            val suggestion = result.getOrNull()
            pendingSuggestion = suggestion
            if (suggestion == null) {
                binding.habitBanner.visibility = View.GONE
                return@launch
            }
            binding.habitBanner.visibility = View.VISIBLE
            binding.habitBannerTitle.text = suggestion.stat.name
            binding.habitBannerBody.text = when (suggestion.kind) {
                InsightKind.HABITUAL_TODAY -> getString(R.string.insights_banner_habitual)
                InsightKind.OVERDUE -> getString(
                    R.string.insights_banner_overdue,
                    suggestion.detailDays
                )
            }
        }
    }

    private fun addSuggestion() {
        val card = pendingSuggestion ?: return
        val target = repo.all().firstOrNull()
            ?: repo.create(getString(R.string.list_detail_title))
        repo.addItem(
            target.id,
            card.stat.toCatalogProduct(),
            card.stat.quantityLabel.ifBlank { "1 шт" }
        )
        Toast.makeText(
            requireContext(),
            getString(R.string.insights_added, card.stat.name, target.title),
            Toast.LENGTH_SHORT
        ).show()
        startActivity(ListDetailActivity.intent(requireContext(), target.id))
        loadHabitBanner()
    }

    override fun onDestroyView() {
        repo.removeObserver(refresh)
        _binding = null
        super.onDestroyView()
    }
}
