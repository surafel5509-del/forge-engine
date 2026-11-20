package com.forge.engine.components

import com.forge.engine.ecs.Component
import com.forge.engine.ecs.ComponentCodec
import com.forge.engine.ecs.ComponentRegistry
import com.forge.engine.render.Color
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Draws a textured quad for its entity. */
data class SpriteComponent(
    var assetId: String = "",
    var color: Color = Color.WHITE,
    var regionX: Int = 0,
    var regionY: Int = 0,
    var regionW: Int = -1,
    var regionH: Int = -1,
    var layer: Int = 0,
    var zIndex: Float = 0f,
    var flipX: Boolean = false,
    var flipY: Boolean = false
) : Component {
    override val typeId: String get() = "sprite"
}

/** Plays a clip from a sprite sheet. */
data class AnimationComponent(
    var sheetAssetId: String = "",
    var clip: String = "default",
    var playing: Boolean = true,
    var speed: Float = 1f,
    var loop: Boolean = true,
    var frameWidth: Int = 32,
    var frameHeight: Int = 32,
    var frameCount: Int = 1
) : Component {
    override val typeId: String get() = "default_anim"
}

/** Rigid body for the built-in AABB physics solver. */
data class RigidBodyComponent(
    var width: Float = 32f,
    var height: Float = 32f,
    var mass: Float = 1f,
    var velocityX: Float = 0f,
    var velocityY: Float = 0f,
    var restitution: Float = 0.2f,
    var friction: Float = 0.6f,
    var gravityScale: Float = 1f,
    var useGravity: Boolean = true,
    var drag: Float = 0f,
    var isStatic: Boolean = false,
    var isTrigger: Boolean = false
) : Component {
    override val typeId: String get() = "rigidbody"
}

/** Binds a behaviour script to the entity. */
data class ScriptComponent(
    var scriptId: String = "",
    var enabled: Boolean = true,
    var properties: MutableMap<String, String> = linkedMapOf()
) : Component {
    override val typeId: String get() = "script"
}

/** Plays an audio asset, optionally following the entity. */
data class AudioComponent(
    var assetId: String = "",
    var volume: Float = 1f,
    var pitch: Float = 1f,
    var loop: Boolean = false,
    var playing: Boolean = false
) : Component {
    override val typeId: String get() = "audio"
}

/** Marks the entity as the active scene camera. */
data class CameraComponent(
    var zoom: Float = 1f,
    var rotation: Float = 0f,
    var followTargetId: Int = -1,
    var smoothFollow: Float = 0f
) : Component {
    override val typeId: String get() = "camera"
}

/** Emits simple procedural particles. */
data class ParticleComponent(
    var count: Int = 24,
    var lifetime: Float = 1.2f,
    var speed: Float = 90f,
    var spread: Float = 360f,
    var gravity: Float = 120f,
    var size: Float = 4f,
    var color: Color = Color(1f, 0.8f, 0.2f)
) : Component {
    override val typeId: String get() = "particle"
}

private fun Color.toJson() = buildJsonObject {
    put("r", r); put("g", g); put("b", b); put("a", a)
}

private fun colorFrom(json: kotlinx.serialization.json.JsonObject): Color = Color(
    json["r"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
    json["g"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
    json["b"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
    json["a"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f
)

internal fun registerComponents() {
    ComponentRegistry.register(
        ComponentCodec(
            "sprite",
            SpriteComponent::class,
            { SpriteComponent() },
            { c ->
                buildJsonObject {
                    put("assetId", c.assetId)
                    put("color", c.color.toJson())
                    put("regionX", c.regionX); put("regionY", c.regionY)
                    put("regionW", c.regionW); put("regionH", c.regionH)
                    put("layer", c.layer); put("zIndex", c.zIndex)
                    put("flipX", c.flipX); put("flipY", c.flipY)
                }
            },
            { j ->
                SpriteComponent(
                    assetId = j["assetId"]?.jsonPrimitive?.content ?: "",
                    color = j["color"]?.let { colorFrom(it.jsonObject) } ?: Color.WHITE,
                    regionX = j["regionX"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    regionY = j["regionY"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    regionW = j["regionW"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                    regionH = j["regionH"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                    layer = j["layer"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    zIndex = j["zIndex"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    flipX = j["flipX"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    flipY = j["flipY"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )
            }
        )
    )

    ComponentRegistry.register(
        ComponentCodec(
            "default_anim",
            AnimationComponent::class,
            { AnimationComponent() },
            { c ->
                buildJsonObject {
                    put("sheetAssetId", c.sheetAssetId)
                    put("clip", c.clip)
                    put("playing", c.playing)
                    put("speed", c.speed)
                    put("loop", c.loop)
                    put("frameWidth", c.frameWidth)
                    put("frameHeight", c.frameHeight)
                    put("frameCount", c.frameCount)
                }
            },
            { j ->
                AnimationComponent(
                    sheetAssetId = j["sheetAssetId"]?.jsonPrimitive?.content ?: "",
                    clip = j["clip"]?.jsonPrimitive?.content ?: "default",
                    playing = j["playing"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                    speed = j["speed"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                    loop = j["loop"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                    frameWidth = j["frameWidth"]?.jsonPrimitive?.content?.toIntOrNull() ?: 32,
                    frameHeight = j["frameHeight"]?.jsonPrimitive?.content?.toIntOrNull() ?: 32,
                    frameCount = j["frameCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
                )
            }
        )
    )

    ComponentRegistry.register(
        ComponentCodec(
            "rigidbody",
            RigidBodyComponent::class,
            { RigidBodyComponent() },
            { c ->
                buildJsonObject {
                    put("width", c.width); put("height", c.height)
                    put("mass", c.mass)
                    put("velocityX", c.velocityX); put("velocityY", c.velocityY)
                    put("restitution", c.restitution); put("friction", c.friction)
                    put("gravityScale", c.gravityScale); put("useGravity", c.useGravity)
                    put("drag", c.drag); put("isStatic", c.isStatic); put("isTrigger", c.isTrigger)
                }
            },
            { j ->
                RigidBodyComponent(
                    width = j["width"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 32f,
                    height = j["height"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 32f,
                    mass = j["mass"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                    velocityX = j["velocityX"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    velocityY = j["velocityY"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    restitution = j["restitution"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.2f,
                    friction = j["friction"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0.6f,
                    gravityScale = j["gravityScale"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                    useGravity = j["useGravity"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                    drag = j["drag"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    isStatic = j["isStatic"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    isTrigger = j["isTrigger"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )
            }
        )
    )

    ComponentRegistry.register(
        ComponentCodec(
            "script",
            ScriptComponent::class,
            { ScriptComponent() },
            { c ->
                buildJsonObject {
                    put("scriptId", c.scriptId)
                    put("enabled", c.enabled)
                    put("properties", buildJsonObject {
                        for ((k, v) in c.properties) put(k, v)
                    })
                }
            },
            { j ->
                ScriptComponent(
                    scriptId = j["scriptId"]?.jsonPrimitive?.content ?: "",
                    enabled = j["enabled"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                    properties = linkedMapOf<String, String>().apply {
                        j["properties"]?.jsonObject?.forEach { (k, v) -> put(k, v.jsonPrimitive.content) }
                    }
                )
            }
        )
    )

    ComponentRegistry.register(
        ComponentCodec(
            "audio",
            AudioComponent::class,
            { AudioComponent() },
            { c ->
                buildJsonObject {
                    put("assetId", c.assetId); put("volume", c.volume); put("pitch", c.pitch)
                    put("loop", c.loop); put("playing", c.playing)
                }
            },
            { j ->
                AudioComponent(
                    assetId = j["assetId"]?.jsonPrimitive?.content ?: "",
                    volume = j["volume"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                    pitch = j["pitch"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                    loop = j["loop"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                    playing = j["playing"]?.jsonPrimitive?.content?.toBoolean() ?: false
                )
            }
        )
    )

    ComponentRegistry.register(
        ComponentCodec(
            "camera",
            CameraComponent::class,
            { CameraComponent() },
            { c ->
                buildJsonObject {
                    put("zoom", c.zoom); put("rotation", c.rotation)
                    put("followTargetId", c.followTargetId); put("smoothFollow", c.smoothFollow)
                }
            },
            { j ->
                CameraComponent(
                    zoom = j["zoom"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                    rotation = j["rotation"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
                    followTargetId = j["followTargetId"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1,
                    smoothFollow = j["smoothFollow"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
                )
            }
        )
    )

    ComponentRegistry.register(
        ComponentCodec(
            "particle",
            ParticleComponent::class,
            { ParticleComponent() },
            { c ->
                buildJsonObject {
                    put("count", c.count); put("lifetime", c.lifetime); put("speed", c.speed)
                    put("spread", c.spread); put("gravity", c.gravity); put("size", c.size)
                    put("color", c.color.toJson())
                }
            },
            { j ->
                ParticleComponent(
                    count = j["count"]?.jsonPrimitive?.content?.toIntOrNull() ?: 24,
                    lifetime = j["lifetime"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1.2f,
                    speed = j["speed"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 90f,
                    spread = j["spread"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 360f,
                    gravity = j["gravity"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 120f,
                    size = j["size"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 4f,
                    color = j["color"]?.let { colorFrom(it.jsonObject) } ?: Color.YELLOW
                )
            }
        )
    )
}
