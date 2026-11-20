package com.forge.engine

import com.forge.engine.math.MathUtils
import com.forge.engine.math.Mat2D
import com.forge.engine.math.Rect
import com.forge.engine.math.Vec2
import com.forge.engine.render.Camera2D
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MathTest {

    @Test
    fun vectorArithmeticWorks() {
        val a = Vec2(3f, 4f)
        assertEquals(5f, a.length, 1e-5f)
        assertEquals(25f, a.lengthSquared, 1e-5f)
        assertEquals(0.6f, a.normalized().x, 1e-5f)
        assertEquals(0.8f, a.normalized().y, 1e-5f)
        assertEquals(Vec2(6f, 8f), a * 2f)
        assertEquals(Vec2(1f, 4f), a + Vec2(-2f, 0f))
        assertEquals(10f, a.dot(Vec2(2f, 1f)), 1e-5f)
        assertEquals(5f, a.distanceTo(Vec2(0f, 0f)), 1e-5f)
    }

    @Test
    fun rectIntersectionAndContainment() {
        val a = Rect(0f, 0f, 10f, 10f)
        val b = Rect(5f, 5f, 10f, 10f)
        assertTrue(a.intersects(b))
        assertFalse(a.containsRect(b))
        assertTrue(a.contains(5f, 5f))
        assertFalse(a.contains(15f, 5f))
        val overlap = a.intersection(b)
        assertEquals(5f, overlap?.width ?: 0f, 1e-5f)
        assertEquals(5f, overlap?.height ?: 0f, 1e-5f)
        assertEquals(null, a.intersection(Rect(50f, 50f, 1f, 1f)))
    }

    @Test
    fun matrixTransformAndInverse() {
        val m = Mat2D().translate(10f, 20f).scale(2f, 2f)
        val p = m.apply(3f, 4f)
        assertEquals(16f, p.x, 1e-4f)
        assertEquals(28f, p.y, 1e-4f)

        val inv = m.invert()
        val round = inv.apply(p.x, p.y)
        assertEquals(3f, round.x, 1e-3f)
        assertEquals(4f, round.y, 1e-3f)
    }

    @Test
    fun matrixRotationPreservesLength() {
        val m = Mat2D().rotate(MathUtils.rad(37f))
        val p = m.apply(2f, 0f)
        assertEquals(2f, kotlin.math.hypot(p.x, p.y), 1e-3f)
    }

    @Test
    fun cameraConvertsBetweenWorldAndScreen() {
        val camera = Camera2D(Vec2(100f, 50f), zoom = 2f)
        camera.viewportWidth = 800
        camera.viewportHeight = 600

        val screen = camera.worldToScreen(100f, 50f)
        assertEquals(400f, screen.x, 1e-3f)
        assertEquals(300f, screen.y, 1e-3f)

        val world = camera.screenToWorld(screen.x, screen.y)
        assertEquals(100f, world.x, 1e-3f)
        assertEquals(50f, world.y, 1e-3f)
    }

    @Test
    fun cameraZoomKeepsPointUnderCursor() {
        val camera = Camera2D(Vec2(10f, 10f), zoom = 1f)
        camera.viewportWidth = 400
        camera.viewportHeight = 400
        val before = camera.screenToWorld(120f, 80f)
        camera.zoomAt(120f, 80f, 1.5f)
        val after = camera.screenToWorld(120f, 80f)
        assertEquals(before.x, after.x, 1e-3f)
        assertEquals(before.y, after.y, 1e-3f)
    }

    @Test
    fun clampAndSnapBehave() {
        assertEquals(5f, MathUtils.clamp(10f, 0f, 5f))
        assertEquals(0f, MathUtils.clamp(-3f, 0f, 5f))
        assertEquals(16f, MathUtils.snap(15.4f, 8f))
    }
}
