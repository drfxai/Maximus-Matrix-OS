package ai.drfx.maximus.matrixai.ui

import kotlin.math.*

/** Camera-space quaternion rotation; touch axes remain consistent after any orbit. */
internal data class SpacePoint(val x: Float, val y: Float, val z: Float) {
    val length get() = sqrt(x * x + y * y + z * z)

    operator fun plus(b: SpacePoint): SpacePoint = SpacePoint(x + b.x, y + b.y, z + b.z)
    operator fun minus(b: SpacePoint): SpacePoint = SpacePoint(x - b.x, y - b.y, z - b.z)
    operator fun times(s: Float): SpacePoint = SpacePoint(x * s, y * s, z * s)

    fun normalized(): SpacePoint {
        val l = length
        return if (l > 0.0001f) SpacePoint(x / l, y / l, z / l) else SpacePoint(0f, 1f, 0f)
    }

    fun lerp(b: SpacePoint, t: Float): SpacePoint =
        SpacePoint(x + (b.x - x) * t, y + (b.y - y) * t, z + (b.z - z) * t)

    fun quadraticBezier(control: SpacePoint, end: SpacePoint, t: Float): SpacePoint {
        val u = 1f - t
        val tt = t * t
        val uu = u * u
        val ut2 = 2f * u * t
        return SpacePoint(
            uu * x + ut2 * control.x + tt * end.x,
            uu * y + ut2 * control.y + tt * end.y,
            uu * z + ut2 * control.z + tt * end.z
        )
    }
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

    companion object {
        fun defaultBrainView(): GraphRotation = GraphRotation().orbit(pitch = -.28f, yaw = .42f)
        fun superiorDorsal(): GraphRotation = GraphRotation().orbit(pitch = -1.45f, yaw = 0f)
        fun frontalAnterior(): GraphRotation = GraphRotation().orbit(pitch = 0f, yaw = 0f)
        fun lateralLeft(): GraphRotation = GraphRotation().orbit(pitch = -.05f, yaw = 1.57f)
        fun lateralRight(): GraphRotation = GraphRotation().orbit(pitch = -.05f, yaw = -1.57f)
    }
}

internal data class GraphProjection(val x: Float, val y: Float, val z: Float, val perspective: Float)

/** The same usable rectangle is passed to painting and hit testing. */
internal data class GraphViewport(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = (right - left).coerceAtLeast(1f)
    val height: Float get() = (bottom - top).coerceAtLeast(1f)
    fun contains(x: Float, y: Float): Boolean = x in left..right && y in top..bottom
}

internal data class GraphLabelBox(val id: String, val left: Float, val top: Float,
    val right: Float, val bottom: Float) {
    fun overlaps(other: GraphLabelBox, gap: Float = 0f): Boolean =
        left < other.right + gap && right + gap > other.left &&
            top < other.bottom + gap && bottom + gap > other.top
}

/** Measured text sizes are supplied by the renderer; dense labels may be omitted from Canvas.
 * Every area remains reachable through the accessible isolation/inspector controls. */
internal fun placeGraphLabel(id: String, x: Float, y: Float, textWidth: Float, textHeight: Float,
    viewport: GraphViewport, occupied: List<GraphLabelBox>, gap: Float): GraphLabelBox? {
    val width = textWidth.coerceIn(1f, viewport.width)
    val height = textHeight.coerceAtLeast(1f)
    if (height > viewport.height) return null
    val left = (x - width / 2f).coerceIn(viewport.left, viewport.right - width)
    for (step in 0..24) {
        val direction = if (step % 2 == 0) 1 else -1
        val offset = ((step + 1) / 2) * direction * (height + gap)
        val top = (y + offset).coerceIn(viewport.top, viewport.bottom - height)
        val candidate = GraphLabelBox(id, left, top, left + width, top + height)
        if (occupied.none { candidate.overlaps(it, gap) }) return candidate
    }
    return null
}

/** A bounding sphere fits the viewport at every orientation, including perspective. */
internal fun projectGraph(p: SpacePoint, rotation: GraphRotation, width: Float, height: Float,
                          radius: Float, zoom: Float, viewport: GraphViewport? = null): GraphProjection {
    val r = radius.coerceAtLeast(1f)
    val v = rotation.apply(p)
    val perspective = 4f * r / (4f * r - v.z).coerceAtLeast(r)
    val usable = viewport ?: GraphViewport(0f, 0f, width, height)
    val scale = min(usable.width, usable.height) * .36f / r * zoom
    return GraphProjection(usable.left + usable.width/2f + v.x*scale*perspective,
        usable.top + usable.height/2f + v.y*scale*perspective, v.z, perspective)
}

