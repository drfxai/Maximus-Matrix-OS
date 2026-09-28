package ai.drfx.maximus.matrixai.ui

import kotlin.math.*

/** Camera-space quaternion rotation; touch axes remain consistent after any orbit. */
internal data class SpacePoint(val x: Float, val y: Float, val z: Float) {
    val length get() = sqrt(x * x + y * y + z * z)
}

internal data class GraphRotation(val w: Float = 1f, val x: Float = 0f, val y: Float = 0f, val z: Float = 0f) {
    fun apply(p: SpacePoint): SpacePoint {
        val tx = 2f * (y * p.z - z * p.y)
        val ty = 2f * (z * p.x - x * p.z)
        val tz = 2f * (x * p.y - y * p.x)
        return SpacePoint(p.x + w * tx + y * tz - z * ty,
            p.y + w * ty + z * tx - x * tz, p.z + w * tz + x * ty - y * tx)
    }
    private fun times(b: GraphRotation): GraphRotation = GraphRotation(
        w*b.w-x*b.x-y*b.y-z*b.z, w*b.x+x*b.w+y*b.z-z*b.y,
        w*b.y-x*b.z+y*b.w+z*b.x, w*b.z+x*b.y-y*b.x+z*b.w)
    fun orbit(pitch: Float = 0f, yaw: Float = 0f, roll: Float = 0f): GraphRotation {
        val a = GraphRotation(cos(pitch/2), sin(pitch/2), 0f, 0f)
        val b = GraphRotation(cos(yaw/2), 0f, sin(yaw/2), 0f)
        val c = GraphRotation(cos(roll/2), 0f, 0f, sin(roll/2))
        val q = c.times(b).times(a).times(this)
        val n = sqrt(q.w*q.w+q.x*q.x+q.y*q.y+q.z*q.z)
        return GraphRotation(q.w/n, q.x/n, q.y/n, q.z/n)
    }
}

internal data class GraphProjection(val x: Float, val y: Float, val z: Float, val perspective: Float)

/** A bounding sphere fits the viewport at every orientation, including perspective. */
internal fun projectGraph(p: SpacePoint, rotation: GraphRotation, width: Float, height: Float,
                          radius: Float, zoom: Float): GraphProjection {
    val r = radius.coerceAtLeast(1f)
    val v = rotation.apply(p)
    val perspective = 4f * r / (4f * r - v.z).coerceAtLeast(r)
    val scale = min(width, height) * .36f / r * zoom
    return GraphProjection(width/2f + v.x*scale*perspective,
        height/2f + v.y*scale*perspective, v.z, perspective)
}
