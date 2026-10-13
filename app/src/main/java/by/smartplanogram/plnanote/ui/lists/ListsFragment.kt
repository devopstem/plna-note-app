package by.smartplanogram.plnanote.ui.lists

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import by.smartplanogram.plnanote.ListDetailActivity
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.databinding.FragmentListsBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset

class ListsFragment : Fragment() {
    private var _binding: FragmentListsBinding? = null
    private val binding get() = _binding!!
    private lateinit var repo: ShoppingListRepository
    private lateinit var adapter: ShoppingListsAdapter
    private val refresh = { adapter.submit(repo.all()) }

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
        binding.listsContent.applyTopContentInset(10)
        adapter = ShoppingListsAdapter(
            onOpen = { list ->
                startActivity(ListDetailActivity.intent(requireContext(), list.id))
            },
            onDelete = { list -> repo.delete(list.id) }
        )
        binding.listsRecycler.layoutManager = LinearLayoutManager(requireContext())
        binding.listsRecycler.adapter = adapter
        binding.fabAddList.setOnClickListener {
            val list = repo.create(getString(R.string.list_detail_title))
            startActivity(ListDetailActivity.intent(requireContext(), list.id))
        }
        binding.btnSearchLists.setOnClickListener {
            val list = repo.all().firstOrNull() ?: repo.create(getString(R.string.list_detail_title))
            startActivity(ListDetailActivity.intent(requireContext(), list.id, openSearch = true))
        }
        repo.observe(refresh)
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onDestroyView() {
        repo.removeObserver(refresh)
        _binding = null
        super.onDestroyView()
    }
}
