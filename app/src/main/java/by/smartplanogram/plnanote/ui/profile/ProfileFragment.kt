package by.smartplanogram.plnanote.ui.profile

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import by.smartplanogram.plnanote.AboutActivity
import by.smartplanogram.plnanote.AuthActivity
import by.smartplanogram.plnanote.InsightsActivity
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.OfflineRepository
import by.smartplanogram.plnanote.data.PurchaseHistoryRepository
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.data.UserSettings
import by.smartplanogram.plnanote.databinding.FragmentProfileBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset
import by.smartplanogram.plnanote.ui.showStorePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

class ProfileFragment : Fragment() {
    private var _binding: FragmentProfileBinding? = null
    private val binding get() = _binding!!
    private lateinit var settings: UserSettings
    private lateinit var offline: OfflineRepository

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        settings = UserSettings.get(requireContext())
        offline = OfflineRepository.get(requireContext())
        binding.profileScroll.applyTopContentInset(10)

        binding.btnAuth.setOnClickListener {
            if (settings.isLoggedIn()) {
                settings.logout()
                refresh()
            } else {
                startActivity(Intent(requireContext(), AuthActivity::class.java))
            }
        }
        binding.storeRow.setOnClickListener {
            showStorePicker(requireContext()) { refresh() }
        }
        binding.aboutRow.setOnClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java))
        }
        binding.analyticsCard.setOnClickListener {
            startActivity(InsightsActivity.intent(requireContext()))
        }
        binding.themeRow.setOnClickListener { pickTheme() }
        binding.languageRow.setOnClickListener { pickLanguage() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        binding.userName.text = settings.displayName() ?: getString(R.string.guest_name)
        binding.userLogin.text = if (settings.isLoggedIn()) {
            settings.loginId()
        } else {
            getString(R.string.auth_guest_label)
        }
        binding.btnAuth.setText(
            if (settings.isLoggedIn()) R.string.auth_logout else R.string.auth_open
        )

        binding.themeValue.text = when (settings.themeMode()) {
            "light" -> getString(R.string.theme_light)
            "system" -> getString(R.string.theme_system)
            else -> getString(R.string.theme_dark)
        }
        binding.languageValue.text = when (settings.languageCode()) {
            "en" -> "English"
            "be" -> "Беларуская"
            else -> "Русский"
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val store = runCatching { offline.selectedStore() }.getOrNull()
            if (_binding == null) return@launch
            binding.activeStore.text = getString(
                R.string.store_label,
                store?.name ?: "—"
            )

            val purchases = runCatching {
                PurchaseHistoryRepository.get(requireContext()).eventCount()
            }.getOrDefault(0)
            if (_binding == null) return@launch
            val lists = ShoppingListRepository.get(requireContext()).all()
            val items = lists.sumOf { it.items.size }
            binding.analyticsBody.text = if (purchases > 0) {
                getString(R.string.insights_profile_body, purchases, lists.size, items)
            } else {
                getString(R.string.insights_profile_empty)
            }
        }
    }

    private fun pickTheme() {
        val modes = arrayOf("dark", "light", "system")
        val labels = arrayOf(
            getString(R.string.theme_dark),
            getString(R.string.theme_light),
            getString(R.string.theme_system)
        )
        val checked = modes.indexOf(settings.themeMode()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.theme_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                settings.setThemeMode(modes[which])
                dialog.dismiss()
                settings.applyThemeNow()
            }
            .show()
    }

    private fun pickLanguage() {
        val codes = arrayOf("ru", "en", "be")
        val labels = arrayOf("Русский", "English", "Беларуская")
        val checked = codes.indexOf(settings.languageCode()).coerceAtLeast(0)
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                settings.setLanguageCode(codes[which])
                dialog.dismiss()
                settings.applyLanguageNow()
            }
            .show()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
