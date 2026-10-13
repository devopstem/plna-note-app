package by.smartplanogram.plnanote.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import by.smartplanogram.plnanote.MainActivity
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.UserSettings
import by.smartplanogram.plnanote.databinding.FragmentHomeBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset

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
        binding.homeRoot.applyTopContentInset(10)
        binding.userRow.setOnClickListener {
            (activity as? MainActivity)?.openTab(R.id.nav_profile)
        }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val settings = UserSettings.get(requireContext())
        binding.userName.text = settings.displayName() ?: getString(R.string.guest_name)
        binding.userStatus.text = if (settings.isLoggedIn()) {
            getString(R.string.home_user_status)
        } else {
            getString(R.string.home_guest_status)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
