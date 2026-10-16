package by.smartplanogram.plnanote.ui.map

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import by.smartplanogram.plnanote.R
import by.smartplanogram.plnanote.data.CatalogProduct

/**
 * Фронт стеллажа ~1.5 × 1.8 м (глубина 0.65 — только подпись).
 * Полки + подсветка bay/shelf с анимацией появления.
 */
class RackFaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var product: CatalogProduct? = null
    private var appear = 0f
    private var anim: ValueAnimator? = null

    private val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = ContextCompat.getColor(context, R.color.plna_accent)
    }
    private val shelfPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.plna_surface)
    }
    private val shelfEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = ContextCompat.getColor(context, R.color.plna_stroke)
    }
    private val productPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.plna_accent)
    }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.plna_text)
        textAlign = Paint.Align.CENTER
        textSize = 28f * resources.displayMetrics.scaledDensity / resources.displayMetrics.density
    }
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.plna_text_secondary)
        textAlign = Paint.Align.CENTER
        textSize = 12f * resources.displayMetrics.scaledDensity
    }
    private val tmp = RectF()

    fun showProduct(p: CatalogProduct) {
        product = p
        anim?.cancel()
        appear = 0f
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            interpolator = OvershootInterpolator(1.1f)
            addUpdateListener {
                appear = it.animatedValue as Float
                invalidate()
            }
            start()
        }
        invalidate()
    }

    override fun onDetachedFromWindow() {
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = product ?: return
        val pad = 20f * resources.displayMetrics.density
        val labelH = 36f * resources.displayMetrics.density
        val frame = RectF(pad, pad + labelH, width - pad, height - pad)

        // aspect ~ 1.5 / 1.8
        val targetAspect = p.rackLengthM / p.rackHeightM.coerceAtLeast(0.5f)
        val frameAspect = frame.width() / frame.height()
        if (frameAspect > targetAspect) {
            val w = frame.height() * targetAspect
            val dx = (frame.width() - w) / 2f
            frame.left += dx
            frame.right -= dx
        } else {
            val h = frame.width() / targetAspect
            val dy = (frame.height() - h) / 2f
            frame.top += dy
            frame.bottom -= dy
        }

        dimPaint.textSize = 12f * resources.displayMetrics.scaledDensity
        canvas.drawText(
            "%.1f×%.1f×%.2f м".format(p.rackLengthM, p.rackHeightM, p.rackDepthM),
            width / 2f,
            pad + 18f * resources.displayMetrics.density,
            dimPaint
        )

        // body
        shelfPaint.color = ContextCompat.getColor(context, R.color.plna_surface_elevated)
        canvas.drawRoundRect(frame, 12f, 12f, shelfPaint)
        canvas.drawRoundRect(frame, 12f, 12f, framePaint)

        val shelves = p.shelfCount.coerceIn(2, 8)
        val bays = 3
        val shelfH = frame.height() / shelves
        val inset = 8f * resources.displayMetrics.density

        for (s in 0 until shelves) {
            // shelf index: 1 = bottom (как в магазине)
            val shelfNum = shelves - s
            val top = frame.top + s * shelfH
            val bottom = top + shelfH
            tmp.set(frame.left + inset, top + inset * 0.4f, frame.right - inset, bottom - inset * 0.15f)
            shelfPaint.color = ContextCompat.getColor(context, R.color.plna_surface)
            canvas.drawRoundRect(tmp, 8f, 8f, shelfPaint)
            canvas.drawRoundRect(tmp, 8f, 8f, shelfEdge)

            // shelf board
            fillBoard(canvas, tmp.left, tmp.bottom - 4f, tmp.right, tmp.bottom)

            if (shelfNum == p.shelf) {
                val bay = p.bay.coerceIn(1, bays)
                val cellW = tmp.width() / bays
                val cell = RectF(
                    tmp.left + (bay - 1) * cellW + cellW * 0.12f,
                    tmp.top + tmp.height() * 0.18f,
                    tmp.left + bay * cellW - cellW * 0.12f,
                    tmp.bottom - 10f
                )
                val cx = cell.centerX()
                val cy = cell.centerY()
                val scale = 0.55f + 0.45f * appear
                canvas.save()
                canvas.translate(cx, cy)
                canvas.scale(scale, scale)
                canvas.translate(-cx, -cy)
                productPaint.alpha = (appear * 255).toInt().coerceIn(0, 255)
                canvas.drawRoundRect(cell, 10f, 10f, productPaint)
                labelPaint.textSize = (11f * resources.displayMetrics.scaledDensity).coerceAtMost(cell.width() / 6f)
                labelPaint.color = ContextCompat.getColor(context, android.R.color.white)
                val name = p.name.let { if (it.length > 18) it.take(16) + "…" else it }
                canvas.drawText(name, cell.centerX(), cell.centerY() + labelPaint.textSize / 3f, labelPaint)
                canvas.restore()
            }
        }
    }

    private fun fillBoard(canvas: Canvas, l: Float, t: Float, r: Float, b: Float) {
        shelfPaint.color = ContextCompat.getColor(context, R.color.plna_stroke)
        canvas.drawRect(l, t, r, b, shelfPaint)
    }
}
