package by.smartplanogram.plnanote.ui.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.smartplanogram.plnanote.nativebridge.NativeMapEngine
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.OfflineRepository
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.databinding.FragmentMapBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset
import kotlinx.coroutines.launch

class MapFragment : Fragment() {
    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.mapRoot.applyTopContentInset(10)
    }

    override fun onResume() {
        super.onResume()
        loadMap()
    }

    private fun loadMap() {
        viewLifecycleOwner.lifecycleScope.launch {
            val offline = OfflineRepository.get(requireContext())
            val store = offline.selectedStore()
            binding.coreVersion.text = runCatching {
                "${NativeMapEngine.getCoreVersion()} · ${store?.name ?: "—"}"
            }.getOrDefault("${getString(R.string.core_version_fallback)} · ${store?.name ?: "—"}")

            val json = offline.mapJsonForSelectedStore()
                ?: requireContext().assets.open("store_map_sample.json")
                    .bufferedReader().use { it.readText() }
            NativeMapEngine.nativeLoadStoreMapJson(json)

            val routeListId = requireActivity().intent?.getStringExtra(EXTRA_ROUTE_LIST_ID)
            val listId = arguments?.getString(ARG_LIST_ID) ?: routeListId
            if (listId != null) {
                val list = ShoppingListRepository.get(requireContext()).get(listId)
                val first = list?.items?.firstOrNull { !it.checked }
                if (first != null) {
                    NativeMapEngine.calculateRoute(15f, 25f, first.x, first.y)
                }
            }

            val floor = NativeMapEngine.nativeFloorSize()
            val fw = if (floor.size >= 2) floor[0] else 30f
            val fh = if (floor.size >= 2) floor[1] else 25f
            binding.storeMapView.setScene(NativeMapEngine.nativeBuildDrawList(), fw, fh)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    companion object {
        const val ARG_LIST_ID = "list_id"
        const val EXTRA_ROUTE_LIST_ID = "route_list_id"

        fun withRoute(listId: String): MapFragment {
            return MapFragment().apply {
                arguments = Bundle().apply { putString(ARG_LIST_ID, listId) }
            }
        }
    }
}
