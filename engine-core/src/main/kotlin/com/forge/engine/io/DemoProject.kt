package com.forge.engine.io

import com.forge.engine.components.AudioComponent
import com.forge.engine.components.AnimationComponent
import com.forge.engine.components.CameraComponent
import com.forge.engine.components.ParticleComponent
import com.forge.engine.components.RigidBodyComponent
import com.forge.engine.components.ScriptComponent
import com.forge.engine.components.SpriteComponent
import com.forge.engine.render.Color
import com.forge.engine.scripting.registerBuiltinBehaviors
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Builds a ready to edit demo project. The scene is a small platformer that
 * exercises every component type the engine ships with.
 */
object DemoProject {

    const val ASSET_PLAYER = "player"
    const val ASSET_ENEMY = "enemy"
    const val ASSET_COIN = "coin"
    const val ASSET_GLOW = "glow"
    const val ASSET_TILES = "tiles"

    fun create(): ProjectData {
        val project = ProjectData(name = "Forge Demo")
        project.assets = mutableListOf(
            AssetData(ASSET_PLAYER, "texture", "procedural://player", 96, 32),
            AssetData(ASSET_ENEMY, "texture", "procedural://enemy", 32, 32),
            AssetData(ASSET_COIN, "texture", "procedural://coin", 24, 24),
            AssetData(ASSET_GLOW, "texture", "procedural://glow", 32, 32),
            AssetData(ASSET_TILES, "tileset", "procedural://tiles", 128, 64)
        )

        val scene = SceneData(name = "Level 1", width = 40, height = 22, tileSize = 32)
        val layer = TileLayerData(name = "Terrain", visible = true, opacity = 1f)
        val tiles = MutableList(scene.width * scene.height) { 0 }

        for (x in 0 until scene.width) {
            for (y in 18 until scene.height) tiles[y * scene.width + x] = 1   // grass
            for (y in 20 until scene.height) tiles[y * scene.width + x] = 2   // dirt
        }
        for (x in 6..10) tiles[16 * scene.width + x] = 3                      // floating platform
        for (x in 15..18) tiles[13 * scene.width + x] = 3
        for (x in 25..29) tiles[15 * scene.width + x] = 3
        for (x in 32..35) tiles[11 * scene.width + x] = 3
        layer.tiles = tiles
        scene.tileLayers.add(layer)

        val decor = TileLayerData(name = "Decor", visible = true, opacity = 1f)
        decor.tiles = MutableList(scene.width * scene.height) { 0 }
        for (x in 3..36 step 5) decor.tiles[17 * scene.width + x] = 8
        scene.tileLayers.add(decor)

        var nextId = 1
        fun addEntity(
            name: String,
            x: Float,
            y: Float,
            assetId: String,
            extra: MutableMap<String, JsonObject> = linkedMapOf()
        ): Int {
            val id = nextId++
            val components = linkedMapOf<String, JsonObject>()
            components["sprite"] = buildJsonObject {
                put("assetId", assetId)
                put("color", buildJsonObject {
                    put("r", 1f); put("g", 1f); put("b", 1f); put("a", 1f)
                })
                put("regionX", 0); put("regionY", 0); put("regionW", -1); put("regionH", -1)
                put("layer", 1); put("zIndex", 0f)
                put("flipX", false); put("flipY", false)
            }
            components.putAll(extra)
            scene.entities.add(
                EntityData(
                    id = id, name = name, parentId = -1,
                    x = x, y = y, rotation = 0f, scaleX = 1f, scaleY = 1f,
                    visible = true, tag = "", components = components
                )
            )
            return id
        }

        addEntity(
            "Player", 3f * 32f, 16f * 32f, ASSET_PLAYER,
            linkedMapOf(
                "sprite" to buildJsonObject {
                    put("assetId", ASSET_PLAYER)
                    put("color", buildJsonObject {
                        put("r", 1f); put("g", 1f); put("b", 1f); put("a", 1f)
                    })
                    put("regionX", 0); put("regionY", 0); put("regionW", 32); put("regionH", 32)
                    put("layer", 1); put("zIndex", 0f)
                    put("flipX", false); put("flipY", false)
                },
                "rigidbody" to buildJsonObject {
                    put("width", 24f); put("height", 30f)
                    put("mass", 1f); put("velocityX", 0f); put("velocityY", 0f)
                    put("restitution", 0f); put("friction", 0.1f)
                    put("gravityScale", 1f); put("useGravity", true)
                    put("drag", 0f); put("isStatic", false); put("isTrigger", false)
                },
                "default_anim" to buildJsonObject {
                    put("sheetAssetId", ASSET_PLAYER)
                    put("clip", "walk"); put("playing", true); put("speed", 1f); put("loop", true)
                    put("frameWidth", 32); put("frameHeight", 32); put("frameCount", 4)
                },
                "script" to buildJsonObject {
                    put("scriptId", "bob"); put("enabled", true)
                    put("properties", buildJsonObject { put("amplitude", "2"); put("frequency", "3") })
                }
            )
        )

        addEntity(
            "Enemy", 12f * 32f, 17f * 32f, ASSET_ENEMY,
            linkedMapOf(
                "rigidbody" to buildJsonObject {
                    put("width", 28f); put("height", 28f)
                    put("mass", 1f); put("velocityX", 0f); put("velocityY", 0f)
                    put("restitution", 0f); put("friction", 0.2f)
                    put("gravityScale", 1f); put("useGravity", true)
                    put("drag", 0f); put("isStatic", false); put("isTrigger", false)
                },
                "script" to buildJsonObject {
                    put("scriptId", "patrol"); put("enabled", true)
                    put("properties", buildJsonObject { put("range", "120"); put("speed", "45") })
                }
            )
        )

        addEntity(
            "Coin", 8f * 32f + 8f, 15f * 32f, ASSET_COIN,
            linkedMapOf(
                "script" to buildJsonObject {
                    put("scriptId", "spin"); put("enabled", true)
                    put("properties", buildJsonObject { put("speed", "120") })
                }
            )
        )

        addEntity(
            "Coin", 16f * 32f + 4f, 12f * 32f, ASSET_COIN,
            linkedMapOf(
                "script" to buildJsonObject {
                    put("scriptId", "spin"); put("enabled", true)
                    put("properties", buildJsonObject { put("speed", "-140") })
                }
            )
        )

        addEntity(
            "Torch", 26f * 32f, 14f * 32f, ASSET_GLOW,
            linkedMapOf(
                "particle" to buildJsonObject {
                    put("count", 24); put("lifetime", 1.2f); put("speed", 60f)
                    put("spread", 360f); put("gravity", -20f); put("size", 6f)
                    put("color", buildJsonObject {
                        put("r", 1f); put("g", 0.75f); put("b", 0.25f); put("a", 1f)
                    })
                }
            )
        )

        addEntity(
            "Main Camera", 0f, 0f, "",
            linkedMapOf(
                "camera" to buildJsonObject {
                    put("zoom", 1f); put("rotation", 0f)
                    put("followTargetId", 1); put("smoothFollow", 4f)
                }
            )
        )

        scene.nextEntityId = nextId
        project.scenes.add(scene)
        project.activeScene = 0
        return project
    }

    /** Registers the built-in component codecs; safe to call repeatedly. */
    fun warmUp() {
        com.forge.engine.ecs.ComponentRegistry.catalog()
        registerBuiltinBehaviors()
    }
}
