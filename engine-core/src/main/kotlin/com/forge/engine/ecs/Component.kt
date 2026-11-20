package com.forge.engine.ecs

import com.forge.engine.math.Vec2
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlin.reflect.KClass

/** Marker for any data attached to an [Entity]. */
interface Component {
    val typeId: String
}

/** Bidirectional mapping between a component instance and its persisted JSON form. */
class ComponentCodec<T : Component>(
    val typeId: String,
    val type: KClass<T>,
    val create: () -> T,
    val encode: (T) -> JsonObject,
    val decode: (JsonObject) -> T
)

/**
 * Registry of every component type the engine understands. Serialization of a
 * scene walks this registry, so adding a component type is a single registration.
 */
object ComponentRegistry {
    private val codecs = LinkedHashMap<String, ComponentCodec<*>>()
    private val classToType = LinkedHashMap<KClass<*>, String>()
    private var built = false

    /** Registers the built-in component types exactly once. */
    private fun ensureBuilt() {
        if (built) return
        built = true
        com.forge.engine.components.registerComponents()
    }

    fun <T : Component> register(codec: ComponentCodec<T>) {
        codecs[codec.typeId] = codec
        classToType[codec.type] = codec.typeId
    }

    fun typeIds(): Set<String> {
        ensureBuilt()
        return codecs.keys.toSet()
    }

    fun has(typeId: String): Boolean {
        ensureBuilt()
        return codecs.containsKey(typeId)
    }

    fun typeIdOf(type: KClass<*>): String? {
        ensureBuilt()
        return classToType[type]
    }

    fun encode(component: Component): JsonObject {
        ensureBuilt()
        val codec = codecs[component.typeId]
            ?: error("No codec registered for component '${component.typeId}'")
        @Suppress("UNCHECKED_CAST")
        return (codec as ComponentCodec<Component>).encode(component)
    }

    fun decode(typeId: String, json: JsonObject): Component? {
        ensureBuilt()
        val codec = codecs[typeId] ?: return null
        @Suppress("UNCHECKED_CAST")
        return (codec as ComponentCodec<Component>).decode(json)
    }

    fun create(typeId: String): Component? {
        ensureBuilt()
        return codecs[typeId]?.create()
    }

    /** Snapshot of every registered component type, used by the editor inspector. */
    fun catalog(): List<String> {
        ensureBuilt()
        return codecs.keys.toList()
    }
}

/**
 * Scene-graph node with a transform, a component set and parent/child links.
 * The hierarchy lets the editor build nested entity trees and lets the runtime
 * propagate world transforms.
 */
class Entity(
    val id: Int,
    var name: String = "Entity"
) {
    @JvmField var position: Vec2 = Vec2()
    @JvmField var rotation: Float = 0f
    @JvmField var scale: Vec2 = Vec2(1f, 1f)

    @JvmField var visible: Boolean = true
    @JvmField var tag: String = ""

    var parent: Entity? = null
        internal set

    val children: MutableList<Entity> = mutableListOf()

    private val componentMap = LinkedHashMap<String, Component>()

    /** Cached world transform, refreshed by the transform system. */
    @JvmField var worldPosition: Vec2 = Vec2()
    @JvmField var worldRotation: Float = 0f
    @JvmField var worldScale: Vec2 = Vec2(1f, 1f)

    val components: Collection<Component> get() = componentMap.values

    fun addComponent(component: Component): Entity {
        componentMap[component.typeId] = component
        return this
    }

    fun removeComponent(typeId: String): Entity {
        componentMap.remove(typeId)
        return this
    }

    fun hasComponent(typeId: String): Boolean = componentMap.containsKey(typeId)

    fun getComponent(typeId: String): Component? = componentMap[typeId]

    inline fun <reified T : Component> component(): T? {
        val typeId = ComponentRegistry.typeIdOf(T::class) ?: return null
        return getComponent(typeId) as T?
    }

    fun addChild(child: Entity): Entity {
        child.parent = this
        children.add(child)
        return this
    }

    fun removeChild(child: Entity): Entity {
        children.remove(child)
        if (child.parent === this) child.parent = null
        return this
    }

    /** Depth-first walk over this entity and all of its descendants. */
    fun forEachInTree(action: (Entity) -> Unit) {
        action(this)
        for (child in children) child.forEachInTree(action)
    }

    fun isDescendantOf(other: Entity): Boolean {
        var p = parent
        while (p != null) {
            if (p === other) return true
            p = p.parent
        }
        return false
    }

    fun root(): Entity {
        var e: Entity = this
        while (e.parent != null) e = e.parent!!
        return e
    }

    override fun toString(): String = "Entity#$id($name)"
}

/**
 * A system processes all entities of a world each frame. Systems run in the
 * order they were added.
 */
interface System {
    val name: String
    fun update(world: World, dt: Float)
}

/** The runtime container of entities, systems and shared world state. */
class World {
    val entities: MutableList<Entity> = mutableListOf()
    val systems: MutableList<System> = mutableListOf()

    @JvmField var time: Float = 0f
    @JvmField var frame: Long = 0L

    private var nextId = 1

    fun createEntity(name: String = "Entity"): Entity {
        val e = Entity(nextId++, name)
        entities.add(e)
        return e
    }

    fun addEntity(entity: Entity): Entity {
        if (!entities.contains(entity)) entities.add(entity)
        return entity
    }

    fun removeEntity(entity: Entity) {
        entity.forEachInTree { e ->
            if (e.parent != null) e.parent!!.children.remove(e)
        }
        entity.parent?.children?.remove(entity)
        entities.remove(entity)
    }

    fun findEntity(id: Int): Entity? = entities.firstOrNull { it.id == id }

    fun addSystem(system: System): System {
        systems.add(system)
        return system
    }

    fun system(name: String): System? = systems.firstOrNull { it.name == name }

    fun update(dt: Float) {
        val clamped = dt.coerceIn(0f, 0.1f)
        time += clamped
        frame++
        for (system in systems) system.update(this, clamped)
    }

    fun clear() {
        entities.clear()
        systems.clear()
        time = 0f
        frame = 0L
    }

    fun jsonOf(component: Component): JsonElement = ComponentRegistry.encode(component)
}
