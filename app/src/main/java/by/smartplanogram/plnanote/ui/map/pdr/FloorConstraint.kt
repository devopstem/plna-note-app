package by.smartplanogram.plnanote.ui.map.pdr

/**
 * Держит позицию в пределах зала и выталкивает из прямоугольников стеллажей.
 * Не идеальный nav-mesh — для PDR-MVP достаточно.
 */
class FloorConstraint(
    private var floorW: Float,
    private var floorH: Float,
    private val obstacles: MutableList<RectM> = mutableListOf()
) {
    data class RectM(
        val left: Float,
        val bottom: Float,
        val right: Float,
        val top: Float
    ) {
        fun contains(x: Float, y: Float, pad: Float = 0.05f): Boolean =
            x >= left - pad && x <= right + pad && y >= bottom - pad && y <= top + pad
    }

    fun setFloor(w: Float, h: Float) {
        floorW = w.coerceAtLeast(1f)
        floorH = h.coerceAtLeast(1f)
    }

    fun setObstacles(rects: List<RectM>) {
        obstacles.clear()
        obstacles.addAll(rects)
    }

    /** Центр footprint (x,y) + ширина/глубина → AABB в метрах зала. */
    fun addRackCentered(cx: Float, cy: Float, w: Float, d: Float) {
        val hw = w * 0.5f
        val hd = d * 0.5f
        obstacles += RectM(cx - hw, cy - hd, cx + hw, cy + hd)
    }

    fun clampAndPush(x: Float, y: Float): Pair<Float, Float> {
        var px = x.coerceIn(0.4f, floorW - 0.4f)
        var py = y.coerceIn(0.4f, floorH - 0.4f)
        // до 3 попыток вытолкнуть из стеллажа
        repeat(3) {
            val hit = obstacles.firstOrNull { it.contains(px, py) } ?: return px to py
            val toLeft = px - hit.left
            val toRight = hit.right - px
            val toBottom = py - hit.bottom
            val toTop = hit.top - py
            val minEdge = minOf(toLeft, toRight, toBottom, toTop)
            when (minEdge) {
                toLeft -> px = hit.left - 0.35f
                toRight -> px = hit.right + 0.35f
                toBottom -> py = hit.bottom - 0.35f
                else -> py = hit.top + 0.35f
            }
            px = px.coerceIn(0.4f, floorW - 0.4f)
            py = py.coerceIn(0.4f, floorH - 0.4f)
        }
        return px to py
    }
}
