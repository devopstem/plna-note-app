package by.smartplanogram.plnanote.nativebridge

object NativeMapEngine {
    init {
        System.loadLibrary("plna")
    }

    @JvmStatic external fun getCoreVersion(): String
    @JvmStatic external fun nativeClearScene()
    @JvmStatic external fun nativeSetFloor(widthMeters: Float, heightMeters: Float)
    @JvmStatic external fun nativeAddEquipment(
        id: String,
        type: Int,
        x: Float,
        y: Float,
        w: Float,
        d: Float,
        h: Float,
        rotationDeg: Float,
        shelves: Int,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    )

    @JvmStatic external fun nativeAddWall(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        isDoor: Boolean
    )

    @JvmStatic external fun nativeAddMarker(
        id: String,
        type: Int,
        x: Float,
        y: Float,
        w: Float,
        d: Float,
        r: Float,
        g: Float,
        b: Float,
        a: Float
    )

    @JvmStatic external fun nativeSetLayerVisible(layer: Int, visible: Boolean)
    @JvmStatic external fun nativeSetView3D(enabled: Boolean)
    @JvmStatic external fun nativeBuildDrawList(): FloatArray
    @JvmStatic external fun nativeLoadStoreMapJson(json: String): Boolean
    @JvmStatic external fun nativeRevision(): Long
    @JvmStatic external fun nativePutProduct(
        barcode: String,
        x: Float,
        y: Float,
        z: Float,
        shelf: Float
    )

    @JvmStatic external fun nativeFloorSize(): FloatArray
    @JvmStatic external fun calculateRoute(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float
    ): FloatArray

    @JvmStatic external fun findProduct(barcode: String): FloatArray
}
