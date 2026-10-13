package by.smartplanogram.plnanote.ui

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.OfflineRepository
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch

fun showStorePicker(context: Context, onSelected: (() -> Unit)? = null) {
    val activity = context as? AppCompatActivity ?: return
    val repo = OfflineRepository.get(context)
    activity.lifecycleScope.launch {
        val stores = repo.allStores()
        if (stores.isEmpty()) {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.choose_store_title)
                .setMessage(R.string.no_stores_yet)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return@launch
        }
        val names = stores.map {
            buildString {
                append(it.name)
                if (it.address.isNotBlank()) append("\n").append(it.address)
            }
        }.toTypedArray()
        val selectedId = repo.selectedStoreId()
        val checked = stores.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)

        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.choose_store_title)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                activity.lifecycleScope.launch {
                    repo.setSelectedStore(stores[which].id)
                    onSelected?.invoke()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
