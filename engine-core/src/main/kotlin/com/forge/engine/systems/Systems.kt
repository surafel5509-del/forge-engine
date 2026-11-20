package com.forge.engine.systems

import com.forge.engine.components.AnimationComponent
import com.forge.engine.components.ParticleComponent
import com.forge.engine.components.RigidBodyComponent
import com.forge.engine.components.ScriptComponent
import com.forge.engine.ecs.Entity
import com.forge.engine.ecs.System
import com.forge.engine.ecs.World
import com.forge.engine.graphics.Animator
import com.forge.engine.math.Vec2
import com.forge.engine.physics.PhysicsBody
import com.forge.engine.physics.PhysicsSolver
import com.forge.engine.render.Color
import com.forge.engine.scripting.Behavior
import com.forge.engine.scripting.BehaviorRegistry

/** Propagates local transforms down the entity hierarchy. */
class TransformSystem : System {
    override val name: String = "transform"

    override fun update(world: World, dt: Float) {
        for (entity in world.entities) {
            val parent = entity.parent
            if (parent == null) {
                entity.worldPosition.set(entity.position)
                entity.worldRotation = entity.rotation
                entity.worldScale.set(entity.scale)
            } else {
                entity.worldPosition.set(
                    parent.worldPosition.x + entity.position.x * parent.worldScale.x,
                    parent.worldPosition.y + entity.position.y * parent.worldScale.y
                )
                entity.worldRotation = parent.worldRotation + entity.rotation
                entity.worldScale.set(entity.scale.x * parent.worldScale.x, entity.scale.y * parent.worldScale.y)
            }
        }
    }
}

/**
 * Bridges rigid body components to the physics solver and writes the results
 * back to the entity transforms.
 */
class PhysicsSystem(@JvmField var solver: PhysicsSolver = PhysicsSolver()) : System {
    override val name: String = "physics"

    private val bodies = linkedMapOf<Int, PhysicsBody>()

    /** Bodies currently simulated, exposed for tests and the editor debugger. */
    val activeBodies: Collection<PhysicsBody> get() = bodies.values

    override fun update(world: World, dt: Float) {
        bodies.keys.toList().forEach { id ->
            if (world.findEntity(id) == null) bodies.remove(id)
        }

        for (entity in world.entities) {
            val rb = entity.getComponent("rigidbody") as? RigidBodyComponent ?: continue
            val body = bodies.getOrPut(entity.id) {
                PhysicsBody(entity.id)
            }
            body.x = entity.worldPosition.x
            body.y = entity.worldPosition.y
            body.width = rb.width
            body.height = rb.height
            body.vx = rb.velocityX
            body.vy = rb.velocityY
            body.isStatic = rb.isStatic
            body.isTrigger = rb.isTrigger
            body.restitution = rb.restitution
            body.friction = rb.friction
            body.mass = rb.mass
            body.gravityScale = rb.gravityScale
            body.useGravity = rb.useGravity
            body.drag = rb.drag
        }

        if (bodies.isNotEmpty()) {
            val list = bodies.values.toMutableList()
            solver.step(list, dt)
        }

        for ((id, body) in bodies) {
            val entity = world.findEntity(id) ?: continue
            val rb = entity.getComponent("rigidbody") as? RigidBodyComponent ?: continue
            rb.velocityX = body.vx
            rb.velocityY = body.vy
            entity.position.set(body.x, body.y)
        }
    }
}

/** Advances sprite sheet animations attached to entities. */
class AnimationSystem : System {
    override val name: String = "animation"

    private val animators = linkedMapOf<Int, Animator>()

    fun animatorFor(entity: Entity): Animator = animators.getOrPut(entity.id) { Animator() }

    override fun update(world: World, dt: Float) {
        for (entity in world.entities) {
            val anim = entity.getComponent("default_anim") as? AnimationComponent ?: continue
            val animator = animatorFor(entity)
            if (!animator.clips.containsKey(anim.clip)) {
                val frames = (0 until anim.frameCount.coerceAtLeast(1)).toList()
                animator.addClip(
                    com.forge.engine.graphics.AnimationClip(anim.clip, frames, 12f, anim.loop)
                )
            }
            animator.play(anim.clip)
            animator.playing = anim.playing
            animator.speed = anim.speed
            animator.update(dt)

            // Drive the sprite region so the sheet animates on screen.
            val sprite = entity.getComponent("sprite") as? com.forge.engine.components.SpriteComponent
            if (sprite != null && anim.frameCount > 1) {
                val frame = animator.currentFrame.coerceIn(0, anim.frameCount - 1)
                sprite.regionX = frame * anim.frameWidth
                sprite.regionY = 0
                sprite.regionW = anim.frameWidth
                sprite.regionH = anim.frameHeight
            }
        }
    }
}

/** Runs behaviour scripts attached to entities. */
class ScriptSystem : System {
    override val name: String = "script"

    private val behaviors = linkedMapOf<Int, Behavior>()

    override fun update(world: World, dt: Float) {
        for (entity in world.entities) {
            val script = entity.getComponent("script") as? ScriptComponent ?: continue
            if (!script.enabled) continue
            val behavior = behaviors.getOrPut(entity.id) {
                BehaviorRegistry.create(script.scriptId) ?: return@getOrPut NoopBehavior()
            }
            behavior.update(entity, world, dt, script.properties)
        }
    }

    private class NoopBehavior : Behavior {
        override fun update(entity: Entity, world: World, dt: Float, properties: Map<String, String>) = Unit
    }
}

/** Lightweight particle simulation for the built-in particle component. */
class ParticleSystem : System {
    override val name: String = "particle"

    class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        var size: Float,
        var color: Color
    )

    val particles = mutableListOf<Particle>()
    private var spawnAccumulator = 0f

    override fun update(world: World, dt: Float) {
        for (entity in world.entities) {
            val config = entity.getComponent("particle") as? ParticleComponent ?: continue
            if (!entity.visible) continue
            spawnAccumulator += dt
            val interval = 0.05f
            while (spawnAccumulator >= interval) {
                spawnAccumulator -= interval
                val angle = Math.random().toFloat() * 6.2831853f
                val speed = config.speed * (0.6f + Math.random().toFloat() * 0.8f)
                particles.add(
                    Particle(
                        entity.worldPosition.x, entity.worldPosition.y,
                        kotlin.math.cos(angle.toDouble()).toFloat() * speed, kotlin.math.sin(angle.toDouble()).toFloat() * speed,
                        config.lifetime, config.lifetime, config.size, config.color
                    )
                )
            }
        }

        val iterator = particles.iterator()
        while (iterator.hasNext()) {
            val p = iterator.next()
            p.life -= dt
            if (p.life <= 0f) {
                iterator.remove()
                continue
            }
            p.vy += 120f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
        if (particles.size > 2000) particles.subList(0, particles.size - 2000).clear()
    }

    fun clear() = particles.clear()
}

/** Convenience factory that builds a world with the standard system stack. */
object EngineSystems {
    fun standardWorld(): World = World().apply {
        addSystem(TransformSystem())
        addSystem(PhysicsSystem())
        addSystem(AnimationSystem())
        addSystem(ScriptSystem())
        addSystem(ParticleSystem())
    }

    fun tempVec(): Vec2 = Vec2()
}
