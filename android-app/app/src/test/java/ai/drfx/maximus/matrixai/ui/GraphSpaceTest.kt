package ai.drfx.maximus.matrixai.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI

class GraphSpaceTest {
    private val quarter = PI.toFloat() / 2f
    @Test fun touchAxesRotateInCameraSpace() {
        val x = GraphRotation().orbit(pitch = quarter).apply(SpacePoint(0f, 1f, 0f))
        assertEquals(0f, x.y, .00001f); assertEquals(1f, x.z, .00001f)
        val y = GraphRotation().orbit(yaw = quarter).apply(SpacePoint(1f, 0f, 0f))
        assertEquals(0f, y.x, .00001f); assertEquals(-1f, y.z, .00001f)
        val z = GraphRotation().orbit(roll = quarter).apply(SpacePoint(1f, 0f, 0f))
        assertEquals(0f, z.x, .00001f); assertEquals(1f, z.y, .00001f)
    }
    @Test fun repeatedMixedGesturesPreserveGeometry() {
        var q = GraphRotation()
        repeat(10000) { q = q.orbit(.007f, -.013f, .003f) }
        val point = SpacePoint(150f, -230f, 120f)
        assertEquals(point.length, q.apply(point).length, .002f)
        assertEquals(1f, q.w*q.w + q.x*q.x + q.y*q.y + q.z*q.z, .00001f)
    }
    @Test fun resetFitsAllOrientationsInPortraitAndLandscape() {
        listOf(360f to 640f, 840f to 240f).forEach { (w, h) ->
            repeat(180) { i ->
                val q = GraphRotation().orbit(i*.173f, i*.037f, i*.097f)
                listOf(SpacePoint(400f,0f,0f), SpacePoint(0f,400f,0f), SpacePoint(0f,0f,400f)).forEach { point ->
                    val p = projectGraph(point,q,w,h,400f,1f)
                    assertTrue(p.x in 0f..w); assertTrue(p.y in 0f..h)
                    assertTrue(p.perspective.isFinite())
                }
            }
        }
    }
    @Test fun frontNodesAppearLargerAndRollPreservesDepth() {
        val front = projectGraph(SpacePoint(50f,0f,100f), GraphRotation(),400f,600f,400f,1f)
        val back = projectGraph(SpacePoint(50f,0f,-100f), GraphRotation(),400f,600f,400f,1f)
        assertTrue(front.perspective > back.perspective)
        val rolled = GraphRotation().orbit(roll = quarter).apply(SpacePoint(50f,0f,100f))
        assertEquals(100f, rolled.z, .00001f)
    }
}
