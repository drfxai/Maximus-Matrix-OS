package ai.drfx.maximus.matrixai

object NativeBridge {
    init {
        try {
            System.loadLibrary("maximus_matrix_native")
        } catch (ignored: Throwable) {
            // Fallback gracefully
        }
    }

    fun nativeAbiMarker(): Int {
        return 64
    }
}
