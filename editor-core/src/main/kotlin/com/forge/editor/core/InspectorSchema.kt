package com.forge.editor.core

import com.forge.engine.ecs.ComponentRegistry
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

enum class PropertyType { FLOAT, INT, BOOL, STRING, COLOR, ASSET }

/** Declarative description of one editable property. */
data class PropertySpec(
    val key: String,
    val label: String,
    val type: PropertyType,
    val default: JsonElement,
    val min: Float = -100000f,
    val max: Float = 100000f,
    val step: Float = 0.01f
)

/**
 * Schema describing every component property the inspector can edit. The UI is
 * generated from this table, so adding a component property is a one-liner.
 */
object InspectorSchema {

    private val float = { v: Float -> JsonPrimitive(v) }
    private val int = { v: Int -> JsonPrimitive(v) }
    private val bool = { v: Boolean -> JsonPrimitive(v) }
    private val string = { v: String -> JsonPrimitive(v) }

    private val specs = linkedMapOf<String, List<PropertySpec>>(
        "sprite" to listOf(
            PropertySpec("assetId", "Texture", PropertyType.ASSET, string("")),
            PropertySpec("color", "Tint", PropertyType.COLOR, buildJsonObject {
                put("r", 1f); put("g", 1f); put("b", 1f); put("a", 1f)
            }),
            PropertySpec("layer", "Layer", PropertyType.INT, int(0)),
            PropertySpec("zIndex", "Z Index", PropertyType.FLOAT, float(0f)),
            PropertySpec("flipX", "Flip X", PropertyType.BOOL, bool(false)),
            PropertySpec("flipY", "Flip Y", PropertyType.BOOL, bool(false))
        ),
        "default_anim" to listOf(
            PropertySpec("sheetAssetId", "Sheet", PropertyType.ASSET, string("")),
            PropertySpec("clip", "Clip", PropertyType.STRING, string("default")),
            PropertySpec("playing", "Playing", PropertyType.BOOL, bool(true)),
            PropertySpec("speed", "Speed", PropertyType.FLOAT, float(1f), 0f, 10f),
            PropertySpec("loop", "Loop", PropertyType.BOOL, bool(true))
        ),
        "rigidbody" to listOf(
            PropertySpec("width", "Width", PropertyType.FLOAT, float(32f), 0f, 512f),
            PropertySpec("height", "Height", PropertyType.FLOAT, float(32f), 0f, 512f),
            PropertySpec("mass", "Mass", PropertyType.FLOAT, float(1f), 0.01f, 100f),
            PropertySpec("velocityX", "Velocity X", PropertyType.FLOAT, float(0f), -2000f, 2000f),
            PropertySpec("velocityY", "Velocity Y", PropertyType.FLOAT, float(0f), -2000f, 2000f),
            PropertySpec("restitution", "Bounce", PropertyType.FLOAT, float(0.2f), 0f, 1f),
            PropertySpec("friction", "Friction", PropertyType.FLOAT, float(0.6f), 0f, 1f),
            PropertySpec("gravityScale", "Gravity", PropertyType.FLOAT, float(1f), -5f, 5f),
            PropertySpec("drag", "Drag", PropertyType.FLOAT, float(0f), 0f, 20f),
            PropertySpec("useGravity", "Use Gravity", PropertyType.BOOL, bool(true)),
            PropertySpec("isStatic", "Static", PropertyType.BOOL, bool(false)),
            PropertySpec("isTrigger", "Trigger", PropertyType.BOOL, bool(false))
        ),
        "script" to listOf(
            PropertySpec("scriptId", "Script", PropertyType.STRING, string("")),
            PropertySpec("enabled", "Enabled", PropertyType.BOOL, bool(true))
        ),
        "audio" to listOf(
            PropertySpec("assetId", "Clip", PropertyType.ASSET, string("")),
            PropertySpec("volume", "Volume", PropertyType.FLOAT, float(1f), 0f, 4f),
            PropertySpec("pitch", "Pitch", PropertyType.FLOAT, float(1f), 0.1f, 4f),
            PropertySpec("loop", "Loop", PropertyType.BOOL, bool(false)),
            PropertySpec("playing", "Playing", PropertyType.BOOL, bool(false))
        ),
        "camera" to listOf(
            PropertySpec("zoom", "Zoom", PropertyType.FLOAT, float(1f), 0.1f, 8f),
            PropertySpec("rotation", "Rotation", PropertyType.FLOAT, float(0f), -180f, 180f),
            PropertySpec("followTargetId", "Follow", PropertyType.INT, int(-1)),
            PropertySpec("smoothFollow", "Smoothing", PropertyType.FLOAT, float(0f), 0f, 20f)
        ),
        "particle" to listOf(
            PropertySpec("count", "Count", PropertyType.INT, int(24), 0f, 500f),
            PropertySpec("lifetime", "Lifetime", PropertyType.FLOAT, float(1.2f), 0.1f, 10f),
            PropertySpec("speed", "Speed", PropertyType.FLOAT, float(90f), 0f, 600f),
            PropertySpec("spread", "Spread", PropertyType.FLOAT, float(360f), 0f, 360f),
            PropertySpec("gravity", "Gravity", PropertyType.FLOAT, float(120f), -400f, 400f),
            PropertySpec("size", "Size", PropertyType.FLOAT, float(4f), 1f, 64f),
            PropertySpec("color", "Color", PropertyType.COLOR, buildJsonObject {
                put("r", 1f); put("g", 0.8f); put("b", 0.2f); put("a", 1f)
            })
        )
    )

    /** Friendly display names for component types. */
    private val titles = mapOf(
        "sprite" to "Sprite",
        "default_anim" to "Animation",
        "rigidbody" to "Rigid Body 2D",
        "script" to "Script",
        "audio" to "Audio Source",
        "camera" to "Camera",
        "particle" to "Particle Emitter"
    )

    fun titleOf(typeId: String): String = titles[typeId] ?: typeId

    fun specsFor(typeId: String): List<PropertySpec> = specs[typeId] ?: emptyList()

    fun all(): Map<String, List<PropertySpec>> = specs

    fun knownTypeIds(): List<String> = specs.keys.toList()

    /** Default JSON payload for a component type, taken from the component registry. */
    fun defaultComponent(typeId: String): JsonObject? {
        val component = ComponentRegistry.create(typeId) ?: return null
        return ComponentRegistry.encode(component)
    }

    /** Adds a component to an entity payload with schema defaults. */
    fun createComponent(typeId: String): JsonObject? = defaultComponent(typeId)

    /** Reads a color payload in the inspector's colour format. */
    fun readColor(component: JsonObject, key: String): com.forge.engine.render.Color {
        val obj = component[key] as? JsonObject ?: return com.forge.engine.render.Color.WHITE
        fun read(name: String, fallback: Float) =
            (obj[name] as? JsonPrimitive)?.content?.toFloatOrNull() ?: fallback
        return com.forge.engine.render.Color(read("r", 1f), read("g", 1f), read("b", 1f), read("a", 1f))
    }

    /** Writes a colour back into the inspector's colour format. */
    fun writeColor(component: JsonObject, key: String, color: com.forge.engine.render.Color): JsonObject {
        val previous = component[key] as? JsonObject ?: buildJsonObject { }
        return JsonObject(
            component + (key to buildJsonObject {
                put("r", color.r); put("g", color.g); put("b", color.b); put("a", color.a)
            })
        )
    }
}
