package com.forge.engine.physics

import com.forge.engine.math.Rect
import com.forge.engine.math.Vec2
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Simple physics body: an axis aligned box with linear velocity. */
class PhysicsBody(
    val id: Int,
    @JvmField var x: Float = 0f,
    @JvmField var y: Float = 0f,
    @JvmField var width: Float = 32f,
    @JvmField var height: Float = 32f,
    @JvmField var vx: Float = 0f,
    @JvmField var vy: Float = 0f,
    @JvmField var isStatic: Boolean = false,
    @JvmField var isTrigger: Boolean = false,
    @JvmField var restitution: Float = 0.2f,
    @JvmField var friction: Float = 0.6f,
    @JvmField var mass: Float = 1f,
    @JvmField var gravityScale: Float = 1f,
    @JvmField var useGravity: Boolean = true,
    @JvmField var drag: Float = 0f
) {
    val aabb: Rect get() = Rect(x, y, width, height)

    val centerX: Float get() = x + width * 0.5f
    val centerY: Float get() = y + height * 0.5f

    fun contains(px: Float, py: Float): Boolean = aabb.contains(px, py)

    /** Swept bounds over a timestep, used for continuous-ish collision checks. */
    fun sweptBounds(dt: Float): Rect {
        val nx = x + vx * dt
        val ny = y + vy * dt
        return Rect(min(x, nx), min(y, ny), max(width, abs(vx * dt)), max(height, abs(vy * dt)))
    }
}

/**
 * AABB physics solver with a uniform spatial hash for broad phase narrowing.
 * Deliberately simple and deterministic so it can be unit tested and reused
 * by the editor preview and the runtime game loop.
 */
class PhysicsSolver(@JvmField var gravity: Float = 980f) {

    private var cellSize = 64f

    /** Bodies that came to rest this step (collision callbacks can use it). */
    val restingBodies = mutableSetOf<Int>()

    fun step(bodies: MutableList<PhysicsBody>, dt: Float) {
        if (dt <= 0f) return
        restingBodies.clear()

        for (body in bodies) {
            if (body.isStatic) continue
            if (body.useGravity) body.vy += gravity * body.gravityScale * dt
            if (body.drag > 0f) {
                val damp = max(0f, 1f - body.drag * dt)
                body.vx *= damp
                body.vy *= damp
            }
            body.x += body.vx * dt
            body.y += body.vy * dt
        }

        val pairs = broadPhase(bodies)
        for ((a, b) in pairs) {
            resolve(a, b)
        }
    }

    /** Returns candidate colliding pairs using a uniform grid. */
    fun broadPhase(bodies: List<PhysicsBody>): List<Pair<PhysicsBody, PhysicsBody>> {
        if (bodies.size < 2) return emptyList()
        val result = ArrayList<Pair<PhysicsBody, PhysicsBody>>()
        val grid = HashMap<Long, MutableList<PhysicsBody>>()

        for (body in bodies) {
            val r = body.aabb
            val x0 = floorDiv(r.x, cellSize)
            val y0 = floorDiv(r.y, cellSize)
            val x1 = floorDiv(r.right - 1e-4f, cellSize)
            val y1 = floorDiv(r.bottom - 1e-4f, cellSize)
            for (gx in x0..x1) {
                for (gy in y0..y1) {
                    val key = hash(gx, gy)
                    grid.getOrPut(key) { ArrayList() }.add(body)
                }
            }
        }

        val seen = HashSet<Long>()
        for (bucket in grid.values) {
            if (bucket.size < 2) continue
            for (i in bucket.indices) {
                for (j in i + 1 until bucket.size) {
                    val a = bucket[i]
                    val b = bucket[j]
                    val key = if (a.id < b.id) a.id.toLong() shl 32 or b.id.toLong()
                    else b.id.toLong() shl 32 or a.id.toLong()
                    if (seen.add(key)) result.add(a to b)
                }
            }
        }
        return result
    }

    /** Separates two overlapping boxes along the smallest penetration axis. */
    fun resolve(a: PhysicsBody, b: PhysicsBody) {
        if (a.isTrigger || b.isTrigger) return
        val ra = a.aabb
        val rb = b.aabb
        if (!ra.intersects(rb)) return

        val overlapX = min(ra.right, rb.right) - max(ra.x, rb.x)
        val overlapY = min(ra.bottom, rb.bottom) - max(ra.y, rb.y)
        if (overlapX <= 0f || overlapY <= 0f) return

        if (overlapX < overlapY) {
            val sign = if (a.centerX < b.centerX) -1f else 1f
            if (!a.isStatic) a.x += overlapX * sign
            if (!b.isStatic) b.x -= overlapX * sign
            val relative = b.vx - a.vx
            if (relative != 0f) {
                val impulse = relative * (1f + min(a.restitution, b.restitution))
                if (!a.isStatic) a.vx += impulse * 0.5f
                if (!b.isStatic) b.vx -= impulse * 0.5f
            }
            a.vy *= (1f - min(1f, a.friction))
            b.vy *= (1f - min(1f, b.friction))
        } else {
            val sign = if (a.centerY < b.centerY) -1f else 1f
            if (!a.isStatic) a.y += overlapY * sign
            if (!b.isStatic) b.y -= overlapY * sign
            val relative = b.vy - a.vy
            if (relative != 0f) {
                val impulse = relative * (1f + min(a.restitution, b.restitution))
                if (!a.isStatic) a.vy += impulse * 0.5f
                if (!b.isStatic) b.vy -= impulse * 0.5f
            }
            a.vx *= (1f - min(1f, a.friction))
            b.vx *= (1f - min(1f, b.friction))
        }
        restingBodies.add(a.id)
        restingBodies.add(b.id)
    }

    /** Ray style query: returns the first body whose box contains the point. */
    fun pick(bodies: List<PhysicsBody>, px: Float, py: Float): PhysicsBody? =
        bodies.lastOrNull { it.contains(px, py) }

    private fun floorDiv(v: Float, size: Float): Int = Math.floor(v.toDouble() / size.toDouble()).toInt()

    private fun hash(x: Int, y: Int): Long = x.toLong() * 73856093L xor y.toLong() * 19349663L

    companion object {
        fun distance(a: Vec2, b: Vec2): Float = a.distanceTo(b)
    }
}
