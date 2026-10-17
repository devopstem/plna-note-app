package by.smartplanogram.plnanote

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import by.smartplanogram.plnanote.data.InsightCard
import by.smartplanogram.plnanote.data.InsightKind
import by.smartplanogram.plnanote.data.ProductStat
import by.smartplanogram.plnanote.data.PurchaseHistoryRepository
import by.smartplanogram.plnanote.data.PurchaseSnapshot
import by.smartplanogram.plnanote.data.ShoppingListRepository
import by.smartplanogram.plnanote.databinding.ActivityInsightsBinding
import by.smartplanogram.plnanote.databinding.ItemInsightBinding
import by.smartplanogram.plnanote.databinding.ItemInsightTopBinding
import by.smartplanogram.plnanote.ui.applyTopContentInset
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class InsightsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityInsightsBinding
    private lateinit var history: PurchaseHistoryRepository
    private lateinit var lists: ShoppingListRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityInsightsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.insightsRoot.applyTopContentInset(8)

        history = PurchaseHistoryRepository.get(this)
        lists = ShoppingListRepository.get(this)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnClearHistory.setOnClickListener { confirmClear() }
        reload()
    }

    private fun reload() {
        lifecycleScope.launch {
            val snap = runCatching { history.buildSnapshot() }.getOrElse {
                PurchaseSnapshot(
                    totalEvents = 0,
                    lookbackDays = 120,
                    top = emptyList(),
                    overdue = emptyList(),
                    habitualToday = emptyList()
                )
            }
            binding.insightsSummary.text = getString(
                R.string.insights_summary,
                snap.totalEvents,
                snap.lookbackDays
            )

            val empty = snap.totalEvents == 0
            binding.insightsEmpty.visibility = if (empty) View.VISIBLE else View.GONE
            binding.sectionSuggestionsTitle.visibility =
                if (snap.habitualToday.isNotEmpty() || snap.overdue.isNotEmpty()) View.VISIBLE
                else View.GONE
            binding.sectionTopTitle.visibility =
                if (snap.top.isNotEmpty()) View.VISIBLE else View.GONE

            binding.suggestionsContainer.removeAllViews()
            (snap.habitualToday + snap.overdue).forEach { card ->
                binding.suggestionsContainer.addView(inflateSuggestion(card))
            }

            binding.topContainer.removeAllViews()
            snap.top.forEach { stat ->
                binding.topContainer.addView(inflateTop(stat))
            }
        }
    }

    private fun inflateSuggestion(card: InsightCard): View {
        val row = ItemInsightBinding.inflate(LayoutInflater.from(this), binding.suggestionsContainer, false)
        row.insightTitle.text = card.stat.name
        row.insightBody.text = when (card.kind) {
            InsightKind.HABITUAL_TODAY -> getString(
                R.string.insights_habitual_body,
                card.stat.purchaseCount
            )
            InsightKind.OVERDUE -> getString(
                R.string.insights_overdue_body,
                card.detailDays,
                card.expectedDays
            )
        }
        row.btnAddInsight.setOnClickListener { addToList(card.stat) }
        return row.root
    }

    private fun inflateTop(stat: ProductStat): View {
        val row = ItemInsightTopBinding.inflate(LayoutInflater.from(this), binding.topContainer, false)
        row.topName.text = stat.name
        val daysAgo = TimeUnit.MILLISECONDS.toDays(System.currentTimeMillis() - stat.lastAt)
        val interval = stat.avgIntervalDays?.let { "~${it.toInt()} ${getString(R.string.insights_days)}" }
            ?: "—"
        row.topMeta.text = getString(
            R.string.insights_top_meta,
            stat.purchaseCount,
            daysAgo,
            interval
        )
        return row.root
    }

    private fun addToList(stat: ProductStat) {
        val open = lists.all()
        val target = open.firstOrNull()
            ?: lists.create(getString(R.string.list_detail_title))
        val already = target.items.any {
            PurchaseHistoryRepository.keyOf(it.name) == stat.productKey
        }
        if (already) {
            Toast.makeText(this, R.string.insights_already_in_list, Toast.LENGTH_SHORT).show()
            startActivity(ListDetailActivity.intent(this, target.id))
            return
        }
        lists.addItem(target.id, stat.toCatalogProduct(), stat.quantityLabel.ifBlank { "1 шт" })
        Toast.makeText(
            this,
            getString(R.string.insights_added, stat.name, target.title),
            Toast.LENGTH_SHORT
        ).show()
        startActivity(ListDetailActivity.intent(this, target.id))
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.insights_clear_title)
            .setMessage(R.string.insights_clear_body)
            .setPositiveButton(R.string.insights_clear) { _, _ ->
                lifecycleScope.launch {
                    history.clearAll()
                    reload()
                }
            }
            .setNegativeButton(R.string.map_pick_cancel, null)
            .show()
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, InsightsActivity::class.java)
    }
}
