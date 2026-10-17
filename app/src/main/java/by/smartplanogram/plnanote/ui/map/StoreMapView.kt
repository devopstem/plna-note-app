package by.smartplanogram.plnanote.ui.map

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import by.smartplanogram.plnanote.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * План зала в стиле навигационных карт гипермаркета:
 * пол, стеллажи-гондолы с полками, холодильники, тонкий маршрут со стрелками.
 */
class StoreMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var drawList: FloatArray = FloatArray(0)
    private var floorW = 30f
    private var floorH = 25f
    private var view3d = false

    private var userScale = 1f
    private var userRotation = 0f
    private var userPanX = 0f
    private var userPanY = 0f

    private var highlightX = Float.NaN
    private var highlightY = Float.NaN
    private var highlightRackW = 1.5f
    private var highlightRackD = 0.7f
    private var highlightLabel: String = ""
    private var startX = Float.NaN
    private var startY = Float.NaN
    private var startLabel: String = ""
    private var meX = Float.NaN
    private var meY = Float.NaN
    private var meHeadingDeg = 0f
    private var relocateMode = false
    private var onRelocate: ((Float, Float) -> Unit)? = null
    private var mapBearingDeg = 0f
    private var pulse = 0.55f
    private var pulseAnim: ValueAnimator? = null

    private var baseScale = 1f
    private var baseOffsetX = 0f
    private var baseOffsetY = 0f

    private val density = resources.displayMetrics.density
    private val tmpRect = RectF()
    private val tmpPath = Path()

    private val compassPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF64748B.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 10f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        letterSpacing = 0.08f
    }
    private val compassNorthPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.plna_accent)
        textAlign = Paint.Align.CENTER
        textSize = 11f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val rackLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        textAlign = Paint.Align.CENTER
        textSize = 9f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val targetGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * density
        color = ContextCompat.getColor(context, R.color.plna_accent)
    }

    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFE9EDF2.toInt()
    }
    private val floorEdge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        color = 0xFFB8C0CC.toInt()
    }
    private val aislePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = 0x33A0AAB8
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val shelfLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * density
        color = 0x55FFFFFF
    }
    private val routeGlow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0x553B82F6
    }
    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = 0xFF2563EB.toInt()
        pathEffect = DashPathEffect(floatArrayOf(10f * density, 7f * density), 0f)
    }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFF1D4ED8.toInt()
    }
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = ContextCompat.getColor(context, R.color.plna_accent)
    }
    private val pinRing = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        color = 0xFFFFFFFF.toInt()
    }

    private var lastAngle = Float.NaN
    private var rotating = false

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                zoomBy(detector.scaleFactor, detector.focusX, detector.focusY)
                return true
            }
        }
    )

    private val gestureDetector = GestureDetector(
        context,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onScroll(
                e1: MotionEvent?,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {
                if (scaleDetector.isInProgress || rotating) return false
                userPanX -= distanceX
                userPanY -= distanceY
                invalidate()
                return true
            }
            override fun onDoubleTap(e: MotionEvent): Boolean {
                resetView()
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (!relocateMode) return
                val map = screenToMap(e.x, e.y) ?: return
                onRelocate?.invoke(map.first, map.second)
            }
        }
    )

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
    }

    fun setScene(
        drawList: FloatArray,
        floorW: Float,
        floorH: Float,
        view3d: Boolean = false,
        bearingDeg: Float = 0f
    ) {
        this.drawList = drawList
        this.floorW = floorW.coerceAtLeast(1f)
        this.floorH = floorH.coerceAtLeast(1f)
        this.view3d = view3d
        this.mapBearingDeg = bearingDeg
        invalidate()
    }

    /**
     * Подсветка цели: точка товара + прямоугольник стеллажа + подпись (id/название).
     */
    fun setHighlight(
        x: Float,
        y: Float,
        rackW: Float = 1.5f,
        rackD: Float = 0.7f,
        label: String = ""
    ) {
        highlightX = x
        highlightY = y
        highlightRackW = rackW.coerceAtLeast(0.4f)
        highlightRackD = rackD.coerceAtLeast(0.3f)
        highlightLabel = label
        // без пульсирующего «свечения» — мешает читать UI поверх карты
        pulseAnim?.cancel()
        pulseAnim = null
        pulse = 1f
        invalidate()
    }

    fun clearHighlight() {
        highlightX = Float.NaN
        highlightY = Float.NaN
        highlightLabel = ""
        pulseAnim?.cancel()
        pulseAnim = null
        invalidate()
    }

    /** Якорь «я здесь» (вход / кассы) — не GPS, а выбранная точка старта. */
    fun setStart(x: Float, y: Float, label: String = "") {
        startX = x
        startY = y
        startLabel = label
        invalidate()
    }

    fun clearStart() {
        startX = Float.NaN
        startY = Float.NaN
        startLabel = ""
        invalidate()
    }

    /** Живая точка «я» (PDR). headingDeg: 0 = +Y (север карты). */
    fun setMe(x: Float, y: Float, headingDeg: Float = 0f) {
        meX = x
        meY = y
        meHeadingDeg = headingDeg
        invalidate()
    }

    fun clearMe() {
        meX = Float.NaN
        meY = Float.NaN
        invalidate()
    }

    fun setRelocateMode(enabled: Boolean, listener: ((Float, Float) -> Unit)? = null) {
        relocateMode = enabled
        onRelocate = if (enabled) listener else null
        invalidate()
    }

    /** Экран → метры зала (с учётом pan/zoom/rotate). */
    fun screenToMap(screenX: Float, screenY: Float): Pair<Float, Float>? {
        if (width == 0 || height == 0 || baseScale <= 0f) return null
        val cx = width / 2f
        val cy = height / 2f
        // обратный порядок: un-pan, un-rotate around center, un-scale around center
        var x = screenX - userPanX
        var y = screenY - userPanY
        val rad = Math.toRadians((-userRotation).toDouble())
        val cosR = cos(rad).toFloat()
        val sinR = sin(rad).toFloat()
        val dx = x - cx
        val dy = y - cy
        x = cx + dx * cosR - dy * sinR
        y = cy + dx * sinR + dy * cosR
        x = cx + (x - cx) / userScale
        y = cy + (y - cy) / userScale
        val mapX = (x - baseOffsetX) / baseScale
        val mapY = floorH - (y - baseOffsetY) / baseScale
        if (mapX !in -1f..(floorW + 1f) || mapY !in -1f..(floorH + 1f)) return null
        return mapX to mapY
    }

    fun zoomIn() = zoomBy(1.2f, width / 2f, height / 2f)
    fun zoomOut() = zoomBy(1f / 1.2f, width / 2f, height / 2f)

    fun rotateBy(degrees: Float) {
        userRotation = (userRotation + degrees) % 360f
        if (userRotation < 0f) userRotation += 360f
        invalidate()
    }

    fun resetView() {
        userScale = 1f
        userRotation = 0f
        userPanX = 0f
        userPanY = 0f
        invalidate()
    }

    private fun zoomBy(factor: Float, focusX: Float, focusY: Float) {
        val old = userScale
        val next = (userScale * factor).coerceIn(MIN_ZOOM, MAX_ZOOM)
        if (next == old) return
        val ratio = next / old
        userPanX = focusX - (focusX - userPanX) * ratio
        userPanY = focusY - (focusY - userPanY) * ratio
        userScale = next
        invalidate()
    }

    override fun onDetachedFromWindow() {
        pulseAnim?.cancel()
        super.onDetachedFromWindow()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)
        handleRotation(event)
        if (!scaleDetector.isInProgress) gestureDetector.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP ||
            event.actionMasked == MotionEvent.ACTION_CANCEL
        ) {
            lastAngle = Float.NaN
            rotating = false
        }
        return true
    }

    private fun handleRotation(event: MotionEvent) {
        if (event.pointerCount < 2) {
            lastAngle = Float.NaN
            rotating = false
            return
        }
        val angle = atan2(
            event.getY(1) - event.getY(0),
            event.getX(1) - event.getX(0)
        ) * 180f / Math.PI.toFloat()
        when (event.actionMasked) {
            MotionEvent.ACTION_POINTER_DOWN -> {
                lastAngle = angle
                rotating = true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!lastAngle.isNaN()) {
                    var delta = angle - lastAngle
                    if (delta > 180f) delta -= 360f
                    if (delta < -180f) delta += 360f
                    if (kotlin.math.abs(delta) > 0.4f && !scaleDetector.isInProgress) {
                        rotateBy(delta)
                    }
                }
                lastAngle = angle
                rotating = true
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width == 0 || height == 0) return

        val pad = 10f * density
        val availW = width - pad * 2
        val availH = height - pad * 2
        baseScale = minOf(availW / floorW, availH / floorH)
        baseOffsetX = pad + (availW - floorW * baseScale) / 2f
        baseOffsetY = pad + (availH - floorH * baseScale) / 2f

        canvas.save()
        canvas.translate(userPanX, userPanY)
        canvas.rotate(userRotation, width / 2f, height / 2f)
        canvas.scale(userScale, userScale, width / 2f, height / 2f)

        val s = baseScale
        fun sx(x: Float) = baseOffsetX + x * s
        fun sy(y: Float) = baseOffsetY + (floorH - y) * s

        // Пол зала
        tmpRect.set(sx(0f), sy(floorH), sx(floorW), sy(0f))
        val radius = 10f * density
        canvas.drawRoundRect(tmpRect, radius, radius, floorPaint)
        canvas.drawRoundRect(tmpRect, radius, radius, floorEdge)

        // Лёгкие проходы
        var gx = 5f
        while (gx < floorW) {
            canvas.drawLine(sx(gx), sy(0f), sx(gx), sy(floorH), aislePaint)
            gx += 5f
        }
        var gy = 5f
        while (gy < floorH) {
            canvas.drawLine(sx(0f), sy(gy), sx(floorW), sy(gy), aislePaint)
            gy += 5f
        }

        var routePts: FloatArray? = null

        var i = 0
        val data = drawList
        while (i < data.size) {
            val op = data[i].toInt()
            i++
            when (op) {
                1 -> { // Floor from core — уже нарисовали свой
                    if (i + 7 >= data.size) break
                    i += 8
                }
                2 -> {
                    if (i + 7 >= data.size) break
                    val x = data[i++]; val y = data[i++]
                    val w = data[i++]; val d = data[i++]
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    drawGondola(canvas, s, x, y, w, d, r, g, b, a, cooler = false)
                }
                3 -> {
                    if (i + 8 >= data.size) break
                    val x1 = data[i++]; val y1 = data[i++]
                    val x2 = data[i++]; val y2 = data[i++]
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    val stroke = data[i++]
                    // стены толще, сетку ядра пропускаем (тонкие серые)
                    val isGrid = r < 0.25f && g < 0.3f && b < 0.4f && a < 0.5f
                    if (isGrid) continue
                    strokePaint.color = argb(a, r, g, b)
                    val isDoor = g > 0.5f && r < 0.3f
                    strokePaint.strokeWidth = if (isDoor) {
                        max(3f * density, stroke * s * 0.35f)
                    } else {
                        max(2.2f * density, stroke * s * 0.25f)
                    }
                    canvas.drawLine(sx(x1), sy(y1), sx(x2), sy(y2), strokePaint)
                }
                4 -> {
                    if (i + 6 >= data.size) break
                    val x = data[i++]; val y = data[i++]; val radiusM = data[i++]
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    fillPaint.color = argb(a, r, g, b)
                    canvas.drawCircle(sx(x), sy(y), max(4f * density, radiusM * s), fillPaint)
                }
                5 -> {
                    if (i + 8 >= data.size) break
                    val x = data[i++]; val y = data[i++]
                    val w = data[i++]; val d = data[i++]; val h = data[i++]
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    if (view3d) {
                        drawIsoGondola(canvas, s, x, y, w, d, h, r, g, b, a)
                    } else {
                        drawGondola(canvas, s, x, y, w, d, r, g, b, a, cooler = isCooler(r, g, b))
                    }
                }
                6 -> {
                    if (i >= data.size) break
                    val count = data[i++].toInt().coerceAtLeast(0)
                    if (i + count * 2 + 4 >= data.size) break
                    val pts = FloatArray(count * 2)
                    for (p in 0 until count) {
                        pts[p * 2] = data[i++]
                        pts[p * 2 + 1] = data[i++]
                    }
                    i += 5 // rgba + stroke — рисуем своим стилем
                    routePts = pts
                }
                7 -> {
                    if (i + 11 >= data.size) break
                    val path = Path()
                    for (p in 0 until 4) {
                        val px = sx(data[i++]); val py = sy(data[i++])
                        if (p == 0) path.moveTo(px, py) else path.lineTo(px, py)
                    }
                    path.close()
                    val r = data[i++]; val g = data[i++]; val b = data[i++]; val a = data[i++]
                    fillPaint.color = softFill(r, g, b, a)
                    canvas.drawPath(path, fillPaint)
                    strokePaint.color = argb(0.55f, r * 0.5f, g * 0.5f, b * 0.55f)
                    strokePaint.strokeWidth = 1.5f * density
                    canvas.drawPath(path, strokePaint)
                }
                else -> {
                    canvas.restore()
                    return
                }
            }
        }

        routePts?.let { drawRoute(canvas, s, it) }

        if (!startX.isNaN() && !startY.isNaN() && meX.isNaN()) {
            drawStartDot(canvas, sx(startX), sy(startY))
        }

        if (!highlightX.isNaN() && !highlightY.isNaN()) {
            drawTargetRack(canvas, s)
            drawPin(canvas, sx(highlightX), sy(highlightY), s)
        }

        if (!meX.isNaN() && !meY.isNaN()) {
            drawMe(canvas, sx(meX), sy(meY), meHeadingDeg)
        }

        // Стороны света по краям зала (вращаются вместе с картой)
        drawEdgeCompass(canvas, s)
        canvas.restore()

        // Мини-компас в углу экрана: «С» всегда к северу
        drawScreenCompass(canvas)
    }

    private fun drawTargetRack(canvas: Canvas, scale: Float) {
        fun toX(mx: Float) = baseOffsetX + mx * scale
        fun toY(my: Float) = baseOffsetY + (floorH - my) * scale
        val hw = highlightRackW * 0.5f
        val hd = highlightRackD * 0.5f
        val left = toX(highlightX - hw)
        val right = toX(highlightX + hw)
        val top = toY(highlightY + hd)
        val bottom = toY(highlightY - hd)
        tmpRect.set(left, top, right, bottom)
        val rr = 5f * density
        targetGlow.alpha = 200
        targetGlow.strokeWidth = 2.5f * density
        canvas.drawRoundRect(tmpRect, rr, rr, targetGlow)
        fillPaint.color = argb(0.14f, 0.1f, 0.48f, 0.52f)
        canvas.drawRoundRect(tmpRect, rr, rr, fillPaint)
        if (highlightLabel.isNotBlank()) {
            val ty = top - 4f * density
            fillPaint.color = argb(0.85f, 0.1f, 0.45f, 0.5f)
            val tw = rackLabelPaint.measureText(highlightLabel) + 10f * density
            canvas.drawRoundRect(
                RectF(
                    tmpRect.centerX() - tw / 2f,
                    ty - 11f * density,
                    tmpRect.centerX() + tw / 2f,
                    ty + 2f * density
                ),
                4f * density,
                4f * density,
                fillPaint
            )
            canvas.drawText(highlightLabel, tmpRect.centerX(), ty - 1.5f * density, rackLabelPaint)
        }
    }

    /** Буквы С/В/Ю/З по сторонам плана (учёт bearing магазина). */
    private fun drawEdgeCompass(canvas: Canvas, scale: Float) {
        fun toX(mx: Float) = baseOffsetX + mx * scale
        fun toY(my: Float) = baseOffsetY + (floorH - my) * scale
        // bearing 0 → +Y = север
        val labels = arrayOf("С", "В", "Ю", "З")
        val positions = arrayOf(
            floatArrayOf(floorW * 0.5f, floorH),
            floatArrayOf(floorW, floorH * 0.5f),
            floatArrayOf(floorW * 0.5f, 0f),
            floatArrayOf(0f, floorH * 0.5f)
        )
        val shift = ((mapBearingDeg / 90f).toInt() % 4 + 4) % 4
        for (i in 0..3) {
            val label = labels[(i - shift + 4) % 4]
            val p = positions[i]
            val paint = if (label == "С") compassNorthPaint else compassPaint
            canvas.drawText(label, toX(p[0]), toY(p[1]) + paint.textSize * 0.35f, paint)
        }
    }

    private fun drawScreenCompass(canvas: Canvas) {
        val cx = 22f * density
        val cy = 22f * density
        val r = 14f * density
        fillPaint.color = 0xEEFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r + 2f * density, fillPaint)
        strokePaint.color = 0xFFCBD5E1.toInt()
        strokePaint.strokeWidth = 1f * density
        canvas.drawCircle(cx, cy, r + 2f * density, strokePaint)

        canvas.save()
        // карта повёрнута на userRotation → компас крутим обратно + bearing
        canvas.rotate(-(userRotation + mapBearingDeg), cx, cy)
        canvas.drawText("С", cx, cy - r * 0.35f, compassNorthPaint)
        compassPaint.textSize = 8f * resources.displayMetrics.scaledDensity
        canvas.drawText("Ю", cx, cy + r * 0.75f, compassPaint)
        canvas.drawText("З", cx - r * 0.55f, cy + compassPaint.textSize * 0.35f, compassPaint)
        canvas.drawText("В", cx + r * 0.55f, cy + compassPaint.textSize * 0.35f, compassPaint)
        canvas.restore()
        compassPaint.textSize = 10f * resources.displayMetrics.scaledDensity
    }

    /** Стеллаж-гондола: корпус + линии полок + тёмный «спинной» край. */
    private fun drawGondola(
        canvas: Canvas,
        scale: Float,
        x: Float,
        y: Float,
        w: Float,
        d: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float,
        cooler: Boolean
    ) {
        fun toX(mx: Float) = baseOffsetX + mx * scale
        fun toY(my: Float) = baseOffsetY + (floorH - my) * scale
        val left = toX(x)
        val right = toX(x + w)
        val top = toY(y + d)
        val bottom = toY(y)
        tmpRect.set(left, top, right, bottom)
        val rr = min(6f * density, min(tmpRect.width(), tmpRect.height()) * 0.22f)

        fillPaint.color = if (cooler) coolerFill(r, g, b, a) else softFill(r, g, b, a)
        canvas.drawRoundRect(tmpRect, rr, rr, fillPaint)

        // спинка (узкая полоска вдоль длинной стороны)
        val longHoriz = w >= d
        fillPaint.color = argb(0.35f, r * 0.35f, g * 0.35f, b * 0.4f)
        if (longHoriz) {
            canvas.drawRoundRect(
                RectF(left, top, right, top + max(2.5f * density, tmpRect.height() * 0.14f)),
                rr, rr, fillPaint
            )
        } else {
            canvas.drawRoundRect(
                RectF(left, top, left + max(2.5f * density, tmpRect.width() * 0.14f), bottom),
                rr, rr, fillPaint
            )
        }

        // полки — параллельные линии внутри
        val shelves = if (cooler) 3 else 4
        shelfLinePaint.strokeWidth = 1.1f * density
        shelfLinePaint.color = if (cooler) 0x66E0F2FE.toInt() else 0x66FFFFFF
        if (longHoriz) {
            val step = tmpRect.height() / (shelves + 1)
            for (i in 1..shelves) {
                val yy = top + step * i
                canvas.drawLine(left + rr, yy, right - rr, yy, shelfLinePaint)
            }
        } else {
            val step = tmpRect.width() / (shelves + 1)
            for (i in 1..shelves) {
                val xx = left + step * i
                canvas.drawLine(xx, top + rr, xx, bottom - rr, shelfLinePaint)
            }
        }

        strokePaint.color = argb(0.7f, r * 0.45f, g * 0.45f, b * 0.5f)
        strokePaint.strokeWidth = 1.4f * density
        canvas.drawRoundRect(tmpRect, rr, rr, strokePaint)

        // для масштаба: тонкая «тень» снизу
        strokePaint.color = 0x22000000
        strokePaint.strokeWidth = 2f * density
        canvas.drawLine(left + 2f, bottom + 1.5f * density, right - 2f, bottom + 1.5f * density, strokePaint)
    }

    private fun drawIsoGondola(
        canvas: Canvas,
        scale: Float,
        x: Float,
        y: Float,
        w: Float,
        d: Float,
        h: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    ) {
        fun toX(mx: Float) = baseOffsetX + mx * scale
        fun toY(my: Float) = baseOffsetY + (floorH - my) * scale
        val lift = (h.coerceIn(0.4f, 3f) * 0.38f) * scale
        val skew = 0.22f * scale
        val x0 = toX(x)
        val y0 = toY(y)
        val x1 = toX(x + w)
        val y1 = toY(y + d)

        fillPaint.color = argb(a, r * 0.65f, g * 0.65f, b * 0.7f)
        tmpPath.reset()
        tmpPath.moveTo(x1, y0)
        tmpPath.lineTo(x1 + skew, y0 - lift)
        tmpPath.lineTo(x1 + skew, y1 - lift)
        tmpPath.lineTo(x1, y1)
        tmpPath.close()
        canvas.drawPath(tmpPath, fillPaint)

        fillPaint.color = softFill((r + 0.12f).coerceAtMost(1f), (g + 0.12f).coerceAtMost(1f), (b + 0.15f).coerceAtMost(1f), a)
        tmpPath.reset()
        tmpPath.moveTo(x0, y1)
        tmpPath.lineTo(x1, y1)
        tmpPath.lineTo(x1 + skew, y1 - lift)
        tmpPath.lineTo(x0 + skew, y1 - lift)
        tmpPath.close()
        canvas.drawPath(tmpPath, fillPaint)

        fillPaint.color = softFill(r, g, b, a)
        canvas.drawRect(x0, y1 - lift, x1, y0, fillPaint)

        // полки на фасаде
        shelfLinePaint.color = 0x55FFFFFF
        val shelves = 3
        val faceH = (y0 - (y1 - lift))
        for (i in 1..shelves) {
            val yy = (y1 - lift) + faceH * i / (shelves + 1f)
            canvas.drawLine(x0 + 2f, yy, x1 - 2f, yy, shelfLinePaint)
        }

        strokePaint.color = argb(0.5f, 0.1f, 0.12f, 0.16f)
        strokePaint.strokeWidth = 1.2f * density
        canvas.drawRect(x0, y1 - lift, x1, y0, strokePaint)
    }

    private fun drawRoute(
        canvas: Canvas,
        scale: Float,
        pts: FloatArray
    ) {
        fun toX(mx: Float) = baseOffsetX + mx * scale
        fun toY(my: Float) = baseOffsetY + (floorH - my) * scale
        val n = pts.size / 2
        if (n < 2) return
        tmpPath.reset()
        for (p in 0 until n) {
            val px = toX(pts[p * 2])
            val py = toY(pts[p * 2 + 1])
            if (p == 0) tmpPath.moveTo(px, py) else tmpPath.lineTo(px, py)
        }
        routeGlow.strokeWidth = 7f * density
        canvas.drawPath(tmpPath, routeGlow)
        routePaint.strokeWidth = 2.4f * density
        canvas.drawPath(tmpPath, routePaint)

        // маленькие стрелки вдоль сегментов
        val arrowSize = 5.5f * density
        for (p in 0 until n - 1) {
            val x1 = toX(pts[p * 2]); val y1 = toY(pts[p * 2 + 1])
            val x2 = toX(pts[p * 2 + 2]); val y2 = toY(pts[p * 2 + 3])
            val len = hypot((x2 - x1).toDouble(), (y2 - y1).toDouble()).toFloat()
            if (len < 18f * density) continue
            val ang = atan2(y2 - y1, x2 - x1)
            val cx = (x1 + x2) * 0.5f
            val cy = (y1 + y2) * 0.5f
            drawChevron(canvas, cx, cy, ang, arrowSize)
        }
        // финиш — маленькая точка
        val fx = toX(pts[(n - 1) * 2])
        val fy = toY(pts[(n - 1) * 2 + 1])
        fillPaint.color = 0xFF1D4ED8.toInt()
        canvas.drawCircle(fx, fy, 3.5f * density, fillPaint)
        canvas.drawCircle(fx, fy, 3.5f * density, pinRing)
    }

    private fun drawChevron(canvas: Canvas, cx: Float, cy: Float, angle: Float, size: Float) {
        val cosA = cos(angle.toDouble()).toFloat()
        val sinA = sin(angle.toDouble()).toFloat()
        val tipX = cx + cosA * size
        val tipY = cy + sinA * size
        val bx = cx - cosA * size * 0.35f
        val by = cy - sinA * size * 0.35f
        val px = -sinA * size * 0.55f
        val py = cosA * size * 0.55f
        tmpPath.reset()
        tmpPath.moveTo(tipX, tipY)
        tmpPath.lineTo(bx + px, by + py)
        tmpPath.lineTo(bx - px, by - py)
        tmpPath.close()
        canvas.drawPath(tmpPath, arrowPaint)
    }

    private fun drawPin(canvas: Canvas, cx: Float, cy: Float, scale: Float) {
        val r = 5.5f * density
        pinPaint.alpha = 220
        // капля
        tmpPath.reset()
        tmpPath.addCircle(cx, cy - r * 0.35f, r, Path.Direction.CW)
        canvas.drawPath(tmpPath, pinPaint)
        canvas.drawCircle(cx, cy - r * 0.35f, r, pinRing)
        fillPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy - r * 0.35f, r * 0.35f, fillPaint)
    }

    private fun drawStartDot(canvas: Canvas, cx: Float, cy: Float) {
        val r = 5.5f * density
        fillPaint.color = 0xEE0F766E.toInt()
        canvas.drawCircle(cx, cy, r + 2.5f * density, fillPaint)
        fillPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.color = 0xFF0D9488.toInt()
        canvas.drawCircle(cx, cy, r * 0.45f, fillPaint)
        if (startLabel.isNotBlank()) {
            compassNorthPaint.textSize = 9f * resources.displayMetrics.scaledDensity
            canvas.drawText(startLabel, cx, cy - r - 3f * density, compassNorthPaint)
            compassNorthPaint.textSize = 11f * resources.displayMetrics.scaledDensity
        }
    }

    /** Синяя стрелка «я»: круг + клинышек по направлению хода. */
    private fun drawMe(canvas: Canvas, cx: Float, cy: Float, headingDeg: Float) {
        val r = 7f * density
        fillPaint.color = 0xAA1D4ED8.toInt()
        canvas.drawCircle(cx, cy, r + 3f * density, fillPaint)
        fillPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.color = 0xFF2563EB.toInt()
        canvas.drawCircle(cx, cy, r * 0.4f, fillPaint)

        // стрелка: в экранных координатах +Y карты → вверх экрана уже учтён в sy;
        // heading 0 (север/+Y) → вверх, т.е. отрицательный screen Y
        canvas.save()
        canvas.rotate(-headingDeg, cx, cy)
        tmpPath.reset()
        tmpPath.moveTo(cx, cy - r - 6f * density)
        tmpPath.lineTo(cx - 5f * density, cy - 1f * density)
        tmpPath.lineTo(cx + 5f * density, cy - 1f * density)
        tmpPath.close()
        canvas.drawPath(tmpPath, arrowPaint)
        canvas.restore()

        if (relocateMode) {
            strokePaint.color = 0xFF1D4ED8.toInt()
            strokePaint.strokeWidth = 1.5f * density
            canvas.drawCircle(cx, cy, r + 8f * density, strokePaint)
        }
    }

    private fun isCooler(r: Float, g: Float, b: Float): Boolean =
        b > r + 0.08f && b > g + 0.05f

    private fun softFill(r: Float, g: Float, b: Float, a: Float): Int {
        // пастель как на картах ТЦ
        val rr = (r * 0.72f + 0.22f).coerceAtMost(1f)
        val gg = (g * 0.72f + 0.22f).coerceAtMost(1f)
        val bb = (b * 0.72f + 0.25f).coerceAtMost(1f)
        return argb(a.coerceIn(0.85f, 1f), rr, gg, bb)
    }

    private fun coolerFill(r: Float, g: Float, b: Float, a: Float): Int {
        val rr = (r * 0.45f + 0.35f).coerceAtMost(1f)
        val gg = (g * 0.55f + 0.4f).coerceAtMost(1f)
        val bb = (b * 0.55f + 0.5f).coerceAtMost(1f)
        return argb(a, rr, gg, bb)
    }

    private fun argb(a: Float, r: Float, g: Float, b: Float): Int {
        val aa = (a.coerceIn(0f, 1f) * 255).toInt()
        val rr = (r.coerceIn(0f, 1f) * 255).toInt()
        val gg = (g.coerceIn(0f, 1f) * 255).toInt()
        val bb = (b.coerceIn(0f, 1f) * 255).toInt()
        return (aa shl 24) or (rr shl 16) or (gg shl 8) or bb
    }

    companion object {
        private const val MIN_ZOOM = 0.55f
        private const val MAX_ZOOM = 5f
    }
}
