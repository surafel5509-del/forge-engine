package com.forge.editor.core

import com.forge.engine.ecs.World
import com.forge.engine.input.InputState
import com.forge.engine.io.DemoAssets
import com.forge.engine.io.DemoProject
import com.forge.engine.io.ProjectCodec
import com.forge.engine.io.ProjectData
import com.forge.engine.io.TileLayerData
import com.forge.engine.render.AssetProvider
import com.forge.engine.render.Camera2D
import com.forge.engine.render.MapAssetProvider
import com.forge.engine.render.SceneRenderer
import com.forge.engine.scene.SceneBuilder
import com.forge.engine.systems.ParticleSystem
import com.forge.engine.tilemap.TileMap
import com.forge.engine.tilemap.Tileset
import java.io.File

/**
 * Central editor state: the document, the command stack, the selection, the
 * viewport camera and the live runtime world used for previews.
 */
class EditorSession(val project: ProjectData = DemoProject.create()) {

    val document = EditorDocument(project)
    val commands = CommandStack().also { it.bind(document) }
    val selection: MutableSet<Int> = linkedSetOf()
    val camera = Camera2D()

    var tool: EditorTool = SelectTool()
    var activeLayerIndex: Int = 0
    var currentTileGid: Int = 1
    var spawnAssetId: String = DemoProject.ASSET_PLAYER
    var snapToGrid: Boolean = false
    var gridSize: Float = 32f

    var isPlaying: Boolean = false
        private set

    var world: World = World()
        private set

    var tilemap: TileMap = TileMap()
        private set

    var assets: MapAssetProvider = DemoAssets.providerFor(project)
        private set

    var sceneRenderer: SceneRenderer = SceneRenderer(assets)
        private set

    /** Set when the document changed and the runtime world must be rebuilt. */
    var needsRebuild: Boolean = true

    var fps: Float = 0f
        private set

    var lastError: String? = null

    init {
        DemoProject.warmUp()
        rebuild()
    }

    // ------------------------------------------------------------------ world

    fun rebuild() {
        assets = DemoAssets.providerFor(project)
        sceneRenderer = SceneRenderer(assets)
        world = SceneBuilder.buildWorld(document.scene, assets)
        tilemap = buildTileMap(document.scene.tileLayers)
        needsRebuild = false
    }

    private fun buildTileMap(layers: List<TileLayerData>): TileMap {
        val map = TileMap(
            name = document.scene.name,
            width = document.scene.width,
            height = document.scene.height,
            tileSize = document.scene.tileSize
        )
        val tilesetTexture = DemoAssets.textureFor(DemoProject.ASSET_TILES)
        if (tilesetTexture != null) {
            map.tilesets.add(
                Tileset("Demo Tiles", tilesetTexture, document.scene.tileSize, document.scene.tileSize, 4, 1)
            )
        }
        for (layer in layers) {
            val runtimeLayer = com.forge.engine.tilemap.TileLayer(
                layer.name, map.width, map.height,
                layer.tiles.toMutableList(), layer.visible, layer.opacity, layer.locked
            )
            map.layers.add(runtimeLayer)
        }
        return map
    }

    fun update(dt: Float) {
        if (needsRebuild) rebuild()

        if (isPlaying) {
            world.update(dt)
            updatePlayCamera(dt)
        } else {
            world.system("transform")?.update(world, dt)
            world.system("animation")?.update(world, dt)
        }
        fps = if (dt > 0f) (1f / dt).coerceAtMost(240f) else fps
    }

    private fun updatePlayCamera(dt: Float) {
        val cameraEntity = world.entities.firstOrNull { it.hasComponent("camera") }
        if (cameraEntity == null) return
        val component = cameraEntity.getComponent("camera") as? com.forge.engine.components.CameraComponent
            ?: return
        if (component.followTargetId < 0) return
        val target = world.findEntity(component.followTargetId) ?: return
        val smoothing = component.smoothFollow
        if (smoothing <= 0f) {
            camera.position.set(target.worldPosition)
        } else {
            val t = (smoothing * dt).coerceIn(0f, 1f)
            camera.position.x += (target.worldPosition.x - camera.position.x) * t
            camera.position.y += (target.worldPosition.y - camera.position.y) * t
        }
        camera.zoom = component.zoom
        camera.rotation = component.rotation
    }

    fun play() {
        if (isPlaying) return
        rebuild()
        isPlaying = true
    }

    fun stop() {
        if (!isPlaying) return
        isPlaying = false
        // Play mode may have moved entities: restore the authored document.
        rebuild()
    }

    fun togglePlay() = if (isPlaying) stop() else play()

    // ------------------------------------------------------------- selection

    fun select(id: Int, additive: Boolean = false) {
        if (!additive) selection.clear()
        if (id >= 0) selection.add(id)
    }

    fun clearSelection() = selection.clear()

    fun selectedEntity(): com.forge.engine.io.EntityData? =
        selection.firstOrNull()?.let { document.entity(it) }

    /** Finds the topmost entity under a world position using sprite bounds. */
    fun pickEntity(worldX: Float, worldY: Float): com.forge.engine.io.EntityData? {
        val candidates = document.entities()
            .filter { it.visible }
            .sortedByDescending { it.components["sprite"]?.let { s -> JsonValues.readInt(s, "layer", 0) } ?: 0 }
        for (data in candidates) {
            val sprite = data.components["sprite"] ?: continue
            val assetId = JsonValues.readString(sprite, "assetId", "")
            if (assetId.isEmpty()) continue
            val texture = assets.texture(assetId) ?: continue
            val w = texture.width.toFloat()
            val h = texture.height.toFloat()
            if (worldX >= data.x && worldX <= data.x + w && worldY >= data.y && worldY <= data.y + h) {
                return data
            }
        }
        return null
    }

    // --------------------------------------------------------------- editing

    fun addEntityAt(worldX: Float, worldY: Float, assetId: String, name: String = "Entity"): Int {
        val id = document.allocateEntityId()
        val components = linkedMapOf<String, kotlinx.serialization.json.JsonObject>()
        val sprite = InspectorSchema.defaultComponent("sprite")
        if (sprite != null) {
            components["sprite"] = kotlinx.serialization.json.JsonObject(
                sprite + ("assetId" to kotlinx.serialization.json.JsonPrimitive(assetId))
            )
        }
        val data = com.forge.engine.io.EntityData(
            id = id, name = name, parentId = -1,
            x = worldX, y = worldY, components = components
        )
        commands.execute(AddEntityCommand(data))
        selection.clear()
        selection.add(id)
        return id
    }

    fun deleteSelection() {
        if (selection.isEmpty()) return
        commands.execute(DeleteEntityCommand(selection.toSet()))
        selection.clear()
    }

    fun renameEntity(id: Int, name: String) {
        val entity = document.entity(id) ?: return
        if (entity.name == name) return
        commands.execute(RenameEntityCommand(id, entity.name, name))
    }

    /** Commits a batch of transform changes as a single undoable command. */
    fun commitTransforms(before: Map<Int, FloatArray>, after: Map<Int, FloatArray>) {
        if (before.isEmpty()) return
        commands.execute(MultiTransformCommand(before, after))
    }

    /** Commits a full transform for one entity. */
    fun setEntityTransform(
        entityId: Int,
        x: Float,
        y: Float,
        rotation: Float,
        scaleX: Float,
        scaleY: Float
    ) {
        val entity = document.entity(entityId) ?: return
        val before = TransformCommand.capture(entity)
        val after = floatArrayOf(x, y, rotation, scaleX, scaleY)
        commands.execute(TransformCommand(entityId, before, after))
    }

    /** Commits a component property change. */
    fun setProperty(entityId: Int, typeId: String, key: String, value: kotlinx.serialization.json.JsonElement) {
        val entity = document.entity(entityId) ?: return
        val component = entity.components[typeId] ?: return
        val previous = component[key] ?: kotlinx.serialization.json.JsonPrimitive("")
        if (previous == value) return
        commands.execute(PropertyCommand(entityId, typeId, key, previous, value))
    }

    /** Writes a colour property (stored as a nested object). */
    fun setColorProperty(entityId: Int, typeId: String, key: String, color: com.forge.engine.render.Color) {
        val entity = document.entity(entityId) ?: return
        val component = entity.components[typeId] ?: return
        val previous = component[key]
        val updated = InspectorSchema.writeColor(component, key, color)
        if (previous == updated[key]) return
        commands.execute(ComponentCommand(entityId, typeId, component, updated))
    }

    fun addComponent(entityId: Int, typeId: String) {
        val entity = document.entity(entityId) ?: return
        if (entity.components.containsKey(typeId)) return
        val payload = InspectorSchema.createComponent(typeId) ?: return
        commands.execute(ComponentCommand(entityId, typeId, null, payload))
    }

    fun removeComponent(entityId: Int, typeId: String) {
        val entity = document.entity(entityId) ?: return
        val payload = entity.components[typeId] ?: return
        commands.execute(ComponentCommand(entityId, typeId, payload, null))
    }

    fun setEntityVisible(entityId: Int, visible: Boolean) {
        val entity = document.entity(entityId) ?: return
        if (entity.visible == visible) return
        entity.visible = visible
        needsRebuild = true
    }

    // -------------------------------------------------------------- tile map

    fun activeLayer(): TileLayerData? = document.scene.tileLayers.getOrNull(activeLayerIndex)

    fun paintTiles(cells: List<Triple<Int, Int, Int>>, originals: Map<Int, Int> = emptyMap()) {
        val layer = activeLayer() ?: return
        if (cells.isEmpty()) return
        commands.execute(TileStrokeCommand(layer.name, cells, originals))
    }

    fun addLayer() {
        val name = "Layer ${document.scene.tileLayers.size + 1}"
        commands.execute(AddLayerCommand(name))
        activeLayerIndex = document.layerIndex(name)
    }

    fun removeActiveLayer() {
        if (document.scene.tileLayers.size <= 1) return
        commands.execute(RemoveLayerCommand(activeLayerIndex))
        activeLayerIndex = activeLayerIndex.coerceAtMost(document.scene.tileLayers.size - 1)
    }

    // --------------------------------------------------------------- storage

    fun serialize(): String = ProjectCodec.encode(project)

    fun saveTo(file: File): Boolean = try {
        file.parentFile?.mkdirs()
        file.writeText(serialize())
        true
    } catch (e: Exception) {
        lastError = e.message
        false
    }

    fun loadFrom(file: File): Boolean = try {
        val loaded = ProjectCodec.decode(file.readText())
        replaceProject(loaded)
        true
    } catch (e: Exception) {
        lastError = e.message
        false
    }

    private fun replaceProject(loaded: ProjectData) {
        project.name = loaded.name
        project.version = loaded.version
        project.activeScene = loaded.activeScene
        project.scenes = loaded.scenes
        project.assets = loaded.assets
        document.activeSceneIndex = loaded.activeScene.coerceIn(0, maxOf(0, loaded.scenes.size - 1))
        selection.clear()
        commands.clear()
        rebuild()
    }

    fun newProject(): EditorSession = EditorSession(DemoProject.create())

    // ------------------------------------------------------------- utilities

    fun snap(value: Float): Float =
        if (snapToGrid && gridSize > 0f) Math.round(value / gridSize) * gridSize else value

    fun screenToWorld(screenX: Float, screenY: Float) = camera.screenToWorld(screenX, screenY)

    fun worldToScreen(worldX: Float, worldY: Float) = camera.worldToScreen(worldX, worldY)

    fun clearParticles() {
        (world.system("particle") as? ParticleSystem)?.clear()
    }

    fun assetProvider(): AssetProvider = assets
}
