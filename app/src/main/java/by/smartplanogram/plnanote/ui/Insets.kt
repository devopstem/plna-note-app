package by.smartplanogram.plnanote.ui

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/**
 * Сдвигает контент вниз: status bar (если контент под ним) + дополнительный отступ.
 */
fun View.applyTopContentInset(extraDp: Int = 12) {
    val extraPx = (extraDp * resources.displayMetrics.density).toInt()
    val baseTop = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val topInset = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        // Если система уже выделила место под status bar, topInset у view часто 0 —
        // тогда просто добавляем extra. Если рисуемся edge-to-edge — учитываем оба.
        val add = if (topInset > 0) topInset + extraPx else extraPx
        v.updatePadding(top = baseTop + add)
        insets
    }
    ViewCompat.requestApplyInsets(this)
}
