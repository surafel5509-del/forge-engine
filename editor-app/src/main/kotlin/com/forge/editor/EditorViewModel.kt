package com.forge.editor

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.forge.editor.core.EditorSession
import com.forge.editor.core.EditorTool
import com.forge.editor.core.InspectorSchema
import com.forge.editor.core.PropertyType
import com.forge.editor.core.ToolId
import com.forge.editor.platform.ProjectStorage
import com.forge.editor.viewport.BitmapTexture
import com.forge.engine.io.AssetData
import com.forge.engine.io.DemoAssets
import com.forge.engine.io.EntityData
import com.forge.engine.io.SceneData
import com.forge.engine.render.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/** One row in the hierarchy tree. */
data class HierarchyNode(
    val id: Int,
    val name: String,
    val depth: Int,
    val selected: Boolean,
    val visible: Boolean
)

/** One editable property inside a component. */
data class PropertyUi(
    val key: String,
    val label: String,
    val type: PropertyType,
    val floatValue: Float = 0f,
    val intValue: Int = 0,
    val boolValue: Boolean = false,
    val stringValue: String = "",
    val color: Color = Color.WHITE,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0.01f
)

/** One component block in the inspector. */
data class ComponentUi(
    val typeId: String,
    val title: String,
    val properties: List<PropertyUi>
)

/** Transform values shown in the inspector. */
data class TransformUi(
    val x: Float = 0f,
    val y: Float = 0f,
    val rotation: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f
)

data class LayerUi(val name: String, val visible: Boolean, val opacity: Float, val locked: Boolean)

data class ToolUi(val id: ToolId, val title: String, val shortcut: String)

data class AssetUi(val id: String, val type: String, val path: String)

/** Everything the Compose UI needs to draw one frame. */
data class EditorUiState(
    val projectName: String = "Untitled",
    val scenes: List<String> = emptyList(),
    val activeScene: Int = 0,
    val hierarchy: List<HierarchyNode> = emptyList(),
    val selectedId: Int = -1,
    val selectedName: String = "",
    val transform: TransformUi = TransformUi(),
    val components: List<ComponentUi> = emptyList(),
    val layers: List<LayerUi> = emptyList(),
    val activeLayer: Int = 0,
    val tileIds: List<Int> = (1..8).toList(),
    val currentTile: Int = 1,
    val tools: List<ToolUi> = emptyList(),
    val activeTool: ToolId = ToolId.SELECT,
    val assets: List<AssetUi> = emptyList(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isPlaying: Boolean = false,
    val fps: Float = 0f,
    val zoom: Float = 1f,
    val status: String = "Ready",
    val savedProjects: List<String> = emptyList(),
    val snapToGrid: Boolean = false
)

/**
 * Owns the editor session and exposes an immutable snapshot to the UI.
 * Every mutation funnels through the session so undo/redo stays consistent.
 */
class EditorViewModel(app: Application) : AndroidViewModel(app) {

    val session = EditorSession()
    val storage = ProjectStorage(app)

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh(status: String? = null) {
        _state.value = buildState(status)
    }

    private fun buildState(status: String? = null): EditorUiState {
        val s = session
        val document = s.document
        val selected = s.selectedEntity()

        val hierarchy = buildHierarchy(s)
        val components = selected?.let { entity ->
            InspectorSchema.knownTypeIds().mapNotNull { typeId ->
                val payload = entity.components[typeId] ?: return@mapNotNull null
                val specs = InspectorSchema.specsFor(typeId)
                if (specs.isEmpty()) return@mapNotNull null
                ComponentUi(
                    typeId = typeId,
                    title = InspectorSchema.titleOf(typeId),
                    properties = specs.map { spec -> readProperty(spec, payload) }
                )
            }
        } ?: emptyList()

        return EditorUiState(
            projectName = s.project.name,
            scenes = document.project.scenes.map { it.name },
            activeScene = document.activeSceneIndex,
            hierarchy = hierarchy,
            selectedId = s.selection.firstOrNull() ?: -1,
            selectedName = selected?.name ?: "",
            transform = selected?.let {
                TransformUi(it.x, it.y, it.rotation, it.scaleX, it.scaleY)
            } ?: TransformUi(),
            components = components,
            layers = document.scene.tileLayers.map {
                LayerUi(it.name, it.visible, it.opacity, it.locked)
            },
            activeLayer = s.activeLayerIndex,
            tileIds = (1..8).toList(),
            currentTile = s.currentTileGid,
            tools = com.forge.editor.core.EditorTools.all.map { ToolUi(it.id, it.title, it.shortcut) },
            activeTool = s.tool.id,
            assets = document.project.assets.map { AssetUi(it.id, it.type, it.path) },
            canUndo = s.commands.canUndo,
            canRedo = s.commands.canRedo,
            isPlaying = s.isPlaying,
            fps = s.fps,
            zoom = s.camera.zoom,
            status = status ?: s.lastError ?: "Ready",
            savedProjects = storage.listProjects(),
            snapToGrid = s.snapToGrid
        )
    }

    private fun buildHierarchy(s: EditorSession): List<HierarchyNode> {
        val document = s.document
        val result = mutableListOf<HierarchyNode>()
        val visited = mutableSetOf<Int>()

        fun walk(parentId: Int, depth: Int) {
            for (data in document.entities().filter { it.parentId == parentId }) {
                if (!visited.add(data.id)) continue
                result.add(
                    HierarchyNode(
                        id = data.id,
                        name = data.name,
                        depth = depth,
                        selected = s.selection.contains(data.id),
                        visible = data.visible
                    )
                )
                walk(data.id, depth + 1)
            }
        }
        walk(-1, 0)
        // Orphans (parent removed) still show up so nothing disappears silently.
        for (data in document.entities()) {
            if (visited.add(data.id)) {
                result.add(HierarchyNode(data.id, data.name, 0, s.selection.contains(data.id), data.visible))
            }
        }
        return result
    }

    private fun readProperty(
        spec: com.forge.editor.core.PropertySpec,
        payload: kotlinx.serialization.json.JsonObject
    ): PropertyUi {
        val element: JsonElement? = payload[spec.key]
        return when (spec.type) {
            PropertyType.FLOAT -> PropertyUi(
                spec.key, spec.label, spec.type,
                floatValue = (element as? JsonPrimitive)?.content?.toFloatOrNull() ?: 0f,
                min = spec.min, max = spec.max, step = spec.step
            )
            PropertyType.INT -> PropertyUi(
                spec.key, spec.label, spec.type,
                intValue = (element as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
            )
            PropertyType.BOOL -> PropertyUi(
                spec.key, spec.label, spec.type,
                boolValue = (element as? JsonPrimitive)?.content?.toBooleanStrictOrNull() ?: false
            )
            PropertyType.STRING -> PropertyUi(
                spec.key, spec.label, spec.type,
                stringValue = (element as? JsonPrimitive)?.content ?: ""
            )
            PropertyType.ASSET -> PropertyUi(
                spec.key, spec.label, spec.type,
                stringValue = (element as? JsonPrimitive)?.content ?: ""
            )
            PropertyType.COLOR -> PropertyUi(
                spec.key, spec.label, spec.type,
                color = InspectorSchema.readColor(payload, spec.key)
            )
        }
    }

    // ------------------------------------------------------------- commands

    fun undo() {
        session.commands.undo()
        refresh("Undo")
    }

    fun redo() {
        session.commands.redo()
        refresh("Redo")
    }

    fun selectTool(tool: EditorTool) {
        session.tool = tool
        refresh("Tool: ${tool.title}")
    }

    fun selectEntity(id: Int, additive: Boolean) {
        session.select(id, additive)
        refresh()
    }

    fun deleteSelected() {
        session.deleteSelection()
        refresh("Deleted entity")
    }

    fun renameEntity(id: Int, name: String) {
        session.renameEntity(id, name)
        refresh("Renamed entity")
    }

    fun setTransform(id: Int, transform: TransformUi) {
        session.setEntityTransform(id, transform.x, transform.y, transform.rotation, transform.scaleX, transform.scaleY)
        refresh()
    }

    fun setProperty(entityId: Int, typeId: String, key: String, value: JsonElement) {
        session.setProperty(entityId, typeId, key, value)
        refresh()
    }

    fun setColorProperty(entityId: Int, typeId: String, key: String, color: Color) {
        session.setColorProperty(entityId, typeId, key, color)
        refresh()
    }

    fun addComponent(entityId: Int, typeId: String) {
        session.addComponent(entityId, typeId)
        refresh("Added ${InspectorSchema.titleOf(typeId)}")
    }

    fun removeComponent(entityId: Int, typeId: String) {
        session.removeComponent(entityId, typeId)
        refresh("Removed component")
    }

    fun toggleVisibility(id: Int) {
        val entity = session.document.entity(id) ?: return
        session.setEntityVisible(id, !entity.visible)
        refresh()
    }

    fun setActiveScene(index: Int) {
        session.document.selectScene(index)
        session.clearSelection()
        refresh("Scene: ${session.document.scene.name}")
    }

    fun addScene() {
        val scene = SceneData(name = "Scene ${session.document.project.scenes.size + 1}")
        session.document.project.scenes.add(scene)
        session.document.selectScene(session.document.project.scenes.size - 1)
        refresh("Scene added")
    }

    fun addLayer() {
        session.addLayer()
        refresh("Layer added")
    }

    fun removeLayer() {
        session.removeActiveLayer()
        refresh("Layer removed")
    }

    fun selectLayer(index: Int) {
        session.activeLayerIndex = index.coerceAtMost((session.document.scene.tileLayers.size - 1).coerceAtLeast(0))
        refresh()
    }

    fun toggleLayerVisibility(index: Int) {
        val layer = session.document.scene.tileLayers.getOrNull(index) ?: return
        layer.visible = !layer.visible
        session.needsRebuild = true
        refresh()
    }

    fun selectTile(gid: Int) {
        session.currentTileGid = gid
        refresh()
    }

    fun toggleSnap() {
        session.snapToGrid = !session.snapToGrid
        refresh(if (session.snapToGrid) "Snap to grid on" else "Snap to grid off")
    }

    fun togglePlay() {
        session.togglePlay()
        refresh(if (session.isPlaying) "Playing" else "Stopped")
    }

    fun zoomIn() {
        session.camera.zoom = (session.camera.zoom * 1.2f).coerceAtMost(16f)
        refresh()
    }

    fun zoomOut() {
        session.camera.zoom = (session.camera.zoom / 1.2f).coerceAtLeast(0.1f)
        refresh()
    }

    fun focusSelection() {
        val entity = session.selectedEntity() ?: return
        session.camera.position.set(entity.x + 16f, entity.y + 16f)
        refresh("Focused selection")
    }

    // -------------------------------------------------------------- storage

    fun saveProject(): Boolean {
        val ok = storage.save(session.project.name, session.serialize())
        refresh(if (ok) "Saved ${session.project.name}" else "Save failed")
        return ok
    }

    fun loadProject(name: String) {
        val text = storage.read(name)
        if (text == null) {
            refresh("Could not open $name")
            return
        }
        try {
            com.forge.engine.io.ProjectCodec.decode(text)
            session.loadFrom(storage.fileFor(name))
            refresh("Opened $name")
        } catch (e: Exception) {
            refresh("Error opening $name: ${e.message}")
        }
    }

    fun newProject() {
        session.newProject()
        refresh("New project")
    }

    /** Registers an imported bitmap as a project asset so the renderer can use it. */
    fun importBitmap(uri: Uri, displayName: String): String? {
        val resolver = getApplication<Application>().contentResolver
        val bitmap = try {
            resolver.openInputStream(uri).use { stream ->
                android.graphics.BitmapFactory.decodeStream(stream)
            }
        } catch (e: Exception) {
            null
        } ?: return null

        val id = displayName.substringBeforeLast('.').ifEmpty { "texture" }
        val path = storage.importTexture(id, uri)
        session.project.assets.add(AssetData(id, "texture", path, bitmap.width, bitmap.height))
        session.assets.put(id, BitmapTexture(id, bitmap))
        refresh("Imported $id")
        return id
    }

    /** Deletes the currently selected entity. */
    fun deleteSelectedEntity() = deleteSelected()

    /** Available texture asset ids for the inspector dropdown. */
    fun textureAssetIds(): List<String> = DemoAssets.textureAssetIds(session.project)

    /** Entity data of the current selection (for tests and tooling). */
    fun selectedEntityData(): EntityData? = session.selectedEntity()
}
