package ai.drfx.maximus.matrixai

object NativeBridge {
    init {
        System.loadLibrary("maximus_matrix_native")
    }

    external fun nativeAbiMarker(): Int
}
