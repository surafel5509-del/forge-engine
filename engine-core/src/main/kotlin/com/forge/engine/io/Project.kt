package com.forge.engine.io

import com.forge.engine.ecs.ComponentRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** Asset entry stored in the project manifest. */
data class AssetData(
    var id: String = "",
    var type: String = "texture",
    var path: String = "",
    var width: Int = 0,
    var height: Int = 0
)

/** Persisted entity: transform plus serialized components keyed by component type. */
data class EntityData(
    var id: Int = 0,
    var name: String = "Entity",
    var parentId: Int = -1,
    var x: Float = 0f,
    var y: Float = 0f,
    var rotation: Float = 0f,
    var scaleX: Float = 1f,
    var scaleY: Float = 1f,
    var visible: Boolean = true,
    var tag: String = "",
    var components: MutableMap<String, JsonObject> = linkedMapOf()
)

/** Persisted tile layer. */
data class TileLayerData(
    var name: String = "Layer",
    var visible: Boolean = true,
    var opacity: Float = 1f,
    var locked: Boolean = false,
    var tiles: MutableList<Int> = mutableListOf()
)

/** Persisted scene. */
data class SceneData(
    var name: String = "Scene",
    var width: Int = 40,
    var height: Int = 30,
    var tileSize: Int = 32,
    var entities: MutableList<EntityData> = mutableListOf(),
    var tileLayers: MutableList<TileLayerData> = mutableListOf(),
    var nextEntityId: Int = 1
)

/** The complete project file. */
data class ProjectData(
    var name: String = "Untitled",
    var version: Int = 1,
    var activeScene: Int = 0,
    var scenes: MutableList<SceneData> = mutableListOf(),
    var assets: MutableList<AssetData> = mutableListOf()
)

/**
 * Reads and writes the project manifest. The format is stable, human readable
 * JSON so projects can be version controlled and diffed.
 */
object ProjectCodec {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun encode(project: ProjectData): String = json.encodeToString(
        JsonObject.serializer(),
        projectToJson(project)
    )

    fun decode(text: String): ProjectData = jsonToProject(
        json.parseToJsonElement(text).jsonObject
    )

    fun projectToJson(project: ProjectData): JsonObject = buildJsonObject {
        put("name", project.name)
        put("version", project.version)
        put("activeScene", project.activeScene)
        put("assets", buildJsonArray {
            for (asset in project.assets) {
                add(buildJsonObject {
                    put("id", asset.id)
                    put("type", asset.type)
                    put("path", asset.path)
                    put("width", asset.width)
                    put("height", asset.height)
                })
            }
        })
        put("scenes", buildJsonArray {
            for (scene in project.scenes) add(sceneToJson(scene))
        })
    }

    fun jsonToProject(root: JsonObject): ProjectData {
        val project = ProjectData()
        project.name = root["name"]?.jsonPrimitive?.content ?: "Untitled"
        project.version = root["version"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        project.activeScene = root["activeScene"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        project.assets = root["assets"]?.jsonArray?.map { element ->
            val obj = element.jsonObject
            AssetData(
                id = obj["id"]?.jsonPrimitive?.content ?: "",
                type = obj["type"]?.jsonPrimitive?.content ?: "texture",
                path = obj["path"]?.jsonPrimitive?.content ?: "",
                width = obj["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                height = obj["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            )
        }?.toMutableList() ?: mutableListOf()
        project.scenes = root["scenes"]?.jsonArray?.map { element ->
            jsonToScene(element.jsonObject)
        }?.toMutableList() ?: mutableListOf()
        return project
    }

    fun sceneToJson(scene: SceneData): JsonObject = buildJsonObject {
        put("name", scene.name)
        put("width", scene.width)
        put("height", scene.height)
        put("tileSize", scene.tileSize)
        put("nextEntityId", scene.nextEntityId)
        put("entities", buildJsonArray {
            for (entity in scene.entities) add(entityToJson(entity))
        })
        put("tileLayers", buildJsonArray {
            for (layer in scene.tileLayers) {
                add(buildJsonObject {
                    put("name", layer.name)
                    put("visible", layer.visible)
                    put("opacity", layer.opacity)
                    put("locked", layer.locked)
                    put("tiles", buildJsonArray { for (t in layer.tiles) add(JsonPrimitive(t)) })
                })
            }
        })
    }

    fun jsonToScene(obj: JsonObject): SceneData {
        val scene = SceneData()
        scene.name = obj["name"]?.jsonPrimitive?.content ?: "Scene"
        scene.width = obj["width"]?.jsonPrimitive?.content?.toIntOrNull() ?: 40
        scene.height = obj["height"]?.jsonPrimitive?.content?.toIntOrNull() ?: 30
        scene.tileSize = obj["tileSize"]?.jsonPrimitive?.content?.toIntOrNull() ?: 32
        scene.nextEntityId = obj["nextEntityId"]?.jsonPrimitive?.content?.toIntOrNull() ?: 1
        scene.entities = obj["entities"]?.jsonArray?.map { element ->
            jsonToEntity(element.jsonObject)
        }?.toMutableList() ?: mutableListOf()
        scene.tileLayers = obj["tileLayers"]?.jsonArray?.map { element ->
            val lo = element.jsonObject
            TileLayerData(
                name = lo["name"]?.jsonPrimitive?.content ?: "Layer",
                visible = lo["visible"]?.jsonPrimitive?.content?.toBoolean() ?: true,
                opacity = lo["opacity"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f,
                locked = lo["locked"]?.jsonPrimitive?.content?.toBoolean() ?: false,
                tiles = lo["tiles"]?.jsonArray?.map { it.jsonPrimitive.content.toIntOrNull() ?: 0 }?.toMutableList()
                    ?: mutableListOf()
            )
        }?.toMutableList() ?: mutableListOf()
        return scene
    }

    fun entityToJson(entity: EntityData): JsonObject = buildJsonObject {
        put("id", entity.id)
        put("name", entity.name)
        put("parentId", entity.parentId)
        put("x", entity.x); put("y", entity.y)
        put("rotation", entity.rotation)
        put("scaleX", entity.scaleX); put("scaleY", entity.scaleY)
        put("visible", entity.visible)
        put("tag", entity.tag)
        put("components", buildJsonObject {
            for ((typeId, component) in entity.components) {
                put(typeId, component)
            }
        })
    }

    fun jsonToEntity(obj: JsonObject): EntityData {
        val entity = EntityData()
        entity.id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        entity.name = obj["name"]?.jsonPrimitive?.content ?: "Entity"
        entity.parentId = obj["parentId"]?.jsonPrimitive?.content?.toIntOrNull() ?: -1
        entity.x = obj["x"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        entity.y = obj["y"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        entity.rotation = obj["rotation"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f
        entity.scaleX = obj["scaleX"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f
        entity.scaleY = obj["scaleY"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 1f
        entity.visible = obj["visible"]?.jsonPrimitive?.content?.toBoolean() ?: true
        entity.tag = obj["tag"]?.jsonPrimitive?.content ?: ""
        entity.components = linkedMapOf()
        obj["components"]?.jsonObject?.forEach { (typeId, value) ->
            entity.components[typeId] = value.jsonObject
        }
        return entity
    }

    /** Converts a persisted entity into a runtime component map. */
    fun decodeComponents(entity: EntityData): Map<String, com.forge.engine.ecs.Component> {
        val result = linkedMapOf<String, com.forge.engine.ecs.Component>()
        for ((typeId, json) in entity.components) {
            ComponentRegistry.decode(typeId, json)?.let { result[typeId] = it }
        }
        return result
    }
}
