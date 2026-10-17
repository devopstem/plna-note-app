package by.smartplanogram.plnanote.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.smartplanogram.plnanote.InsightsActivity
import by.smartplanogram.plnanote.ListDetailActivity
import by.smartplanogram.plnanote.MainActivity
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.OfflineRepository
import by.smartplanogram.plnanote.data.PurchaseHistoryRepository
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.data.UserSettings
import by.smartplanogram.plnanote.databinding.FragmentHomeBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset
import by.smartplanogram.plnanote.ui.showStorePicker
import kotlinx.coroutines.launch
import java.util.Calendar

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.homeScroll.applyTopContentInset(10)

        binding.userRow.setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_profile)
        }
        binding.btnHomeStart.setOnClickListener { openOrCreateList() }
        binding.tileLists.setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_lists)
        }
        binding.tileMap.setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_map)
        }
        binding.tileInsights.setOnClickListener {
            startActivity(InsightsActivity.intent(requireContext()))
        }
        binding.storeCard.setOnClickListener {
            showStorePicker(requireContext()) { refresh() }
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun openOrCreateList() {
        val repo = ShoppingListRepository.get(requireContext())
        val list = repo.all().firstOrNull()
            ?: repo.create(getString(R.string.list_detail_title))
        startActivity(ListDetailActivity.intent(requireContext(), list.id))
    }

    private fun refresh() {
        val settings = UserSettings.get(requireContext())
        val name = settings.displayName() ?: getString(R.string.guest_name)
        binding.userGreeting.text = greetingForNow()
        binding.userName.text = name

        val lists = ShoppingListRepository.get(requireContext()).all()
        val openItems = lists.sumOf { list -> list.items.count { !it.isDone() } }
        binding.tileListsMeta.text = getString(
            R.string.home_tile_lists_meta,
            lists.size,
            openItems
        )

        viewLifecycleOwner.lifecycleScope.launch {
            val store = runCatching {
                OfflineRepository.get(requireContext()).selectedStore()
            }.getOrNull()
            val purchases = runCatching {
                PurchaseHistoryRepository.get(requireContext()).eventCount()
            }.getOrDefault(0)
            if (_binding == null) return@launch

            binding.homeStoreName.text = store?.name ?: getString(R.string.home_store_none)
            binding.tileInsightsMeta.text = if (purchases > 0) {
                getString(R.string.home_tile_insights_meta, purchases)
            } else {
                getString(R.string.home_tile_insights_empty)
            }
            binding.homeSummary.text = when {
                lists.isEmpty() -> getString(R.string.home_summary_empty)
                openItems == 0 -> getString(R.string.home_summary_done, lists.size)
                else -> getString(
                    R.string.home_summary_active,
                    lists.size,
                    openItems,
                    store?.name ?: "—"
                )
            }
        }
    }

    private fun greetingForNow(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val res = when (hour) {
            in 5..11 -> R.string.home_greet_morning
            in 12..17 -> R.string.home_greet_day
            in 18..22 -> R.string.home_greet_evening
            else -> R.string.home_greet_night
        }
        return getString(res)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
