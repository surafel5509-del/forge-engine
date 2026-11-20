package com.forge.editor.core

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/** A reversible mutation of the editor document. */
interface Command {
    val name: String
    fun execute(document: EditorDocument)
    fun undo(document: EditorDocument)
}

/** Undo/redo stack with a change notification so the UI can refresh. */
class CommandStack {

    private val undoStack = ArrayDeque<Command>()
    private val redoStack = ArrayDeque<Command>()

    var onChanged: (() -> Unit)? = null

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val undoLabel: String? get() = undoStack.lastOrNull()?.name
    val redoLabel: String? get() = redoStack.lastOrNull()?.name
    val depth: Int get() = undoStack.size

    fun execute(command: Command) {
        command.execute(currentDocument ?: return)
        undoStack.addLast(command)
        redoStack.clear()
        onChanged?.invoke()
    }

    fun undo() {
        val command = undoStack.removeLastOrNull() ?: return
        currentDocument?.let { command.undo(it) }
        redoStack.addLast(command)
        onChanged?.invoke()
    }

    fun redo() {
        val command = redoStack.removeLastOrNull() ?: return
        currentDocument?.let { command.execute(it) }
        undoStack.addLast(command)
        onChanged?.invoke()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        onChanged?.invoke()
    }

    /**
     * The document the stack operates on. Commands are executed by the session,
     * which supplies the document through [bind].
     */
    var currentDocument: EditorDocument? = null

    fun bind(document: EditorDocument) {
        currentDocument = document
    }
}

/** Adds an entity to the scene (optionally under a parent). */
class AddEntityCommand(
    private val entity: com.forge.engine.io.EntityData,
    private val parentId: Int = -1
) : Command {
    override val name: String get() = "Add ${entity.name}"

    override fun execute(document: EditorDocument) {
        document.addEntity(entity.copy(), parentId)
    }

    override fun undo(document: EditorDocument) {
        document.removeEntities(setOf(entity.id))
    }
}

/** Deletes an entity and its descendants, remembering them for undo. */
class DeleteEntityCommand(
    private val rootIds: Set<Int>
) : Command {
    private var removed: List<com.forge.engine.io.EntityData> = emptyList()
    private var removedIndices: Map<Int, Int> = emptyMap()

    override val name: String get() = "Delete"

    override fun execute(document: EditorDocument) {
        val ids = rootIds.flatMap { document.subtreeOf(it) }.toSet()
        removed = document.entities().filter { it.id in ids }.map { it.copy() }
        removedIndices = removed.withIndex().associate { (index, data) -> data.id to index }
        document.removeEntities(ids)
    }

    override fun undo(document: EditorDocument) {
        for (data in removed.sortedBy { removedIndices[it.id] ?: 0 }) {
            val copy = data.copy()
            document.addEntity(copy, copy.parentId)
        }
    }
}

/** Changes the transform of an entity. */
class TransformCommand(
    private val entityId: Int,
    private val before: FloatArray,
    private val after: FloatArray
) : Command {
    override val name: String get() = "Transform"

    override fun execute(document: EditorDocument) = apply(document, after)

    override fun undo(document: EditorDocument) = apply(document, before)

    private fun apply(document: EditorDocument, values: FloatArray) {
        val entity = document.entity(entityId) ?: return
        entity.x = values[0]
        entity.y = values[1]
        entity.rotation = values[2]
        entity.scaleX = values[3]
        entity.scaleY = values[4]
    }

    companion object {
        fun capture(entity: com.forge.engine.io.EntityData): FloatArray = floatArrayOf(
            entity.x, entity.y, entity.rotation, entity.scaleX, entity.scaleY
        )

        fun of(entity: com.forge.engine.io.EntityData): FloatArray = capture(entity)
    }
}

/** Applies transform changes to several entities as one undoable command. */
class MultiTransformCommand(
    private val before: Map<Int, FloatArray>,
    private val after: Map<Int, FloatArray>
) : Command {
    override val name: String get() = "Transform"

    override fun execute(document: EditorDocument) = apply(document, after)

    override fun undo(document: EditorDocument) = apply(document, before)

    private fun apply(document: EditorDocument, values: Map<Int, FloatArray>) {
        for ((id, transform) in values) {
            val entity = document.entity(id) ?: continue
            entity.x = transform[0]
            entity.y = transform[1]
            entity.rotation = transform[2]
            entity.scaleX = transform[3]
            entity.scaleY = transform[4]
        }
    }
}

/** Renames an entity. */
class RenameEntityCommand(
    private val entityId: Int,
    private val before: String,
    private val after: String
) : Command {
    override val name: String get() = "Rename"

    override fun execute(document: EditorDocument) {
        document.entity(entityId)?.name = after
    }

    override fun undo(document: EditorDocument) {
        document.entity(entityId)?.name = before
    }
}

/**
 * Generic property edit on a component's JSON payload. Works for any field
 * declared in the inspector schema without needing a dedicated command class.
 */
class PropertyCommand(
    private val entityId: Int,
    private val componentTypeId: String,
    private val propertyKey: String,
    private val before: JsonElement,
    private val after: JsonElement
) : Command {
    override val name: String get() = "Edit $propertyKey"

    override fun execute(document: EditorDocument) = apply(document, after)

    override fun undo(document: EditorDocument) = apply(document, before)

    private fun apply(document: EditorDocument, value: JsonElement) {
        val entity = document.entity(entityId) ?: return
        val component = entity.components[componentTypeId] ?: return
        entity.components[componentTypeId] = JsonObject(component + (propertyKey to value))
    }
}

/** Sets a whole component payload (used when a component is added or reset). */
class ComponentCommand(
    private val entityId: Int,
    private val componentTypeId: String,
    private val before: JsonObject?,
    private val after: JsonObject?
) : Command {
    override val name: String get() = "Edit $componentTypeId"

    override fun execute(document: EditorDocument) = apply(document, after)

    override fun undo(document: EditorDocument) = apply(document, before)

    private fun apply(document: EditorDocument, value: JsonObject?) {
        val entity = document.entity(entityId) ?: return
        if (value == null) {
            entity.components.remove(componentTypeId)
        } else {
            entity.components[componentTypeId] = value
        }
    }
}

/** Paints tiles; a whole stroke is recorded as a single undoable command. */
class TileStrokeCommand(
    private val layerName: String,
    private val cells: List<Triple<Int, Int, Int>>,
    /** Values captured before the stroke started, used as the undo baseline. */
    private val originals: Map<Int, Int> = emptyMap()
) : Command {
    private val before = LinkedHashMap<Int, Int>()

    override val name: String get() = "Paint"

    override fun execute(document: EditorDocument) {
        val layer = document.layer(layerName) ?: return
        for ((cx, cy, gid) in cells) {
            val index = cy * document.scene.width + cx
            if (!before.containsKey(index)) {
                before[index] = originals[index] ?: layer.tiles.getOrElse(index) { 0 }
            }
            if (index in layer.tiles.indices) layer.tiles[index] = gid
        }
    }

    override fun undo(document: EditorDocument) {
        val layer = document.layer(layerName) ?: return
        for ((index, value) in before) {
            if (index in layer.tiles.indices) layer.tiles[index] = value
        }
    }
}

/** Adds a tile layer. */
class AddLayerCommand(
    private val layerName: String
) : Command {
    override val name: String get() = "Add Layer"

    override fun execute(document: EditorDocument) {
        if (document.layer(layerName) == null) document.addLayer(layerName)
    }

    override fun undo(document: EditorDocument) {
        val index = document.layerIndex(layerName)
        if (index >= 0) document.removeLayer(index)
    }
}

/** Removes a tile layer but keeps a copy so it can be restored. */
class RemoveLayerCommand(
    private val layerIndex: Int
) : Command {
    private var removed: com.forge.engine.io.TileLayerData? = null

    override val name: String get() = "Remove Layer"

    override fun execute(document: EditorDocument) {
        removed = document.removeLayer(layerIndex)
    }

    override fun undo(document: EditorDocument) {
        removed?.let { document.insertLayer(layerIndex, it) }
    }
}

/** Adds a new scene to the project. */
class AddSceneCommand(
    private val scene: com.forge.engine.io.SceneData,
    private val index: Int
) : Command {
    override val name: String get() = "Add Scene"

    override fun execute(document: EditorDocument) {
        document.project.scenes.add(index.coerceIn(0, document.project.scenes.size), scene)
    }

    override fun undo(document: EditorDocument) {
        document.project.scenes.removeAll { it === scene }
    }
}

/** Utility helpers for reading and writing values inside component payloads. */
object JsonValues {
    fun read(component: JsonObject, key: String): JsonElement? = component[key]

    fun write(component: JsonObject, key: String, value: JsonElement): JsonObject =
        JsonObject(component + (key to value))

    fun ofFloat(value: Float): JsonElement = JsonPrimitive(value)
    fun ofInt(value: Int): JsonElement = JsonPrimitive(value)
    fun ofBool(value: Boolean): JsonElement = JsonPrimitive(value)
    fun ofString(value: String): JsonElement = JsonPrimitive(value)

    fun readFloat(component: JsonObject, key: String, fallback: Float = 0f): Float =
        component[key]?.let {
            (it as? JsonPrimitive)?.content?.toFloatOrNull()
        } ?: fallback

    fun readInt(component: JsonObject, key: String, fallback: Int = 0): Int =
        component[key]?.let {
            (it as? JsonPrimitive)?.content?.toIntOrNull()
        } ?: fallback

    fun readBool(component: JsonObject, key: String, fallback: Boolean = false): Boolean =
        component[key]?.let {
            (it as? JsonPrimitive)?.content?.toBooleanStrictOrNull()
        } ?: fallback

    fun readString(component: JsonObject, key: String, fallback: String = ""): String =
        component[key]?.let {
            (it as? JsonPrimitive)?.content
        } ?: fallback

    fun emptyObject(): JsonObject = buildJsonObject { }
}
