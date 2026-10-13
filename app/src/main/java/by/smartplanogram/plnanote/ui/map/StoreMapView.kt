package by.smartplanogram.plnanote.ui.map

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import by.smartplanogram.plnanote.R

class StoreMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var drawList: FloatArray = FloatArray(0)
    private var floorW = 30f
    private var floorH = 25f

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = ContextCompat.getColor(context, R.color.plna_map_grid)
        alpha = 120
    }

    fun setScene(drawList: FloatArray, floorW: Float, floorH: Float) {
        this.drawList = drawList
        this.floorW = floorW.coerceAtLeast(1f)
        this.floorH = floorH.coerceAtLeast(1f)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = 16f * resources.displayMetrics.density
        val availW = width - pad * 2
        val availH = height - pad * 2
        val scale = minOf(availW / floorW, availH / floorH)
        val offsetX = pad + (availW - floorW * scale) / 2f
        val offsetY = pad + (availH - floorH * scale) / 2f

        fun sx(x: Float) = offsetX + x * scale
        fun sy(y: Float) = offsetY + (floorH - y) * scale

        // subtle grid
        var gx = 0f
        while (gx <= floorW) {
            canvas.drawLine(sx(gx), sy(0f), sx(gx), sy(floorH), gridPaint)
            gx += 1f
        }
        var gy = 0f
        while (gy <= floorH) {
            canvas.drawLine(sx(0f), sy(gy), sx(floorW), sy(gy), gridPaint)
            gy += 1f
        }

        var i = 0
        val data = drawList
        while (i < data.size) {
            val op = data[i].toInt()
            i++
            when (op) {
                1, 2 -> { // Floor / Box2D
                    if (i + 7 >= data.size) break
                    val x = data[i++]; val y = data[i++]
                    val w = data[i++]; val h = data[i++]
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    fillPaint.color = argb(a, r, g, b)
                    val left = sx(x)
                    val top = sy(y + h)
                    val right = sx(x + w)
                    val bottom = sy(y)
                    canvas.drawRect(left, top, right, bottom, fillPaint)
                }
                3 -> { // Line
                    if (i + 8 >= data.size) break
                    val x1 = data[i++]; val y1 = data[i++]
                    val x2 = data[i++]; val y2 = data[i++]
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    val stroke = data[i++]
                    strokePaint.color = argb(a, r, g, b)
                    strokePaint.strokeWidth = (stroke * scale).coerceAtLeast(2f)
                    canvas.drawLine(sx(x1), sy(y1), sx(x2), sy(y2), strokePaint)
                }
                4 -> { // Circle
                    if (i + 6 >= data.size) break
                    val x = data[i++]; val y = data[i++]; val radius = data[i++]
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    fillPaint.color = argb(a, r, g, b)
                    canvas.drawCircle(sx(x), sy(y), radius * scale, fillPaint)
                }
                5 -> { // Box3D -> footprint
                    if (i + 8 >= data.size) break
                    val x = data[i++]; val y = data[i++]
                    val w = data[i++]; val d = data[i++]; i++ // h unused
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    fillPaint.color = argb(a, r, g, b)
                    canvas.drawRect(sx(x), sy(y + d), sx(x + w), sy(y), fillPaint)
                }
                6 -> { // Path
                    if (i >= data.size) break
                    val count = data[i++].toInt().coerceAtLeast(0)
                    if (i + count * 2 + 4 >= data.size) break
                    val path = Path()
                    for (p in 0 until count) {
                        val px = sx(data[i++]); val py = sy(data[i++])
                        if (p == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    val stroke = data[i++]
                    strokePaint.color = argb(a, r, g, b)
                    strokePaint.strokeWidth = (stroke * scale).coerceAtLeast(3f)
                    canvas.drawPath(path, strokePaint)
                }
                7 -> { // Quad
                    if (i + 11 >= data.size) break
                    val path = Path()
                    for (p in 0 until 4) {
                        val px = sx(data[i++]); val py = sy(data[i++])
                        if (p == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    fillPaint.color = argb(a, r, g, b)
                    canvas.drawPath(path, fillPaint)
                }
                else -> return
            }
        }
    }

    private fun argb(a: Float, r: Float, g: Float, b: Float): Int {
        val aa = (a.coerceIn(0f, 1f) * 255).toInt()
        val rr = (r.coerceIn(0f, 1f) * 255).toInt()
        val gg = (g.coerceIn(0f, 1f) * 255).toInt()
        val bb = (b.coerceIn(0f, 1f) * 255).toInt()
        return (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }
}
