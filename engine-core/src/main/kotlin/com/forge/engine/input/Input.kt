package com.forge.engine.input

enum class MouseButton { LEFT, RIGHT, MIDDLE }

data class PointerState(
    val id: Int,
    var x: Float = 0f,
    var y: Float = 0f,
    var down: Boolean = false,
    var button: MouseButton = MouseButton.LEFT,
    var justPressed: Boolean = false,
    var justReleased: Boolean = false,
    var dragging: Boolean = false
)

/**
 * Platform independent input snapshot. Android backends translate MotionEvent
 * data into this structure; the editor and runtime only ever read it.
 */
class InputState {
    val pointers: MutableList<PointerState> = mutableListOf()

    val keysDown: MutableSet<Int> = linkedSetOf()
    val keysJustPressed: MutableSet<Int> = linkedSetOf()
    val keysJustReleased: MutableSet<Int> = linkedSetOf()

    var scrollX: Float = 0f
    var scrollY: Float = 0f

    fun pointer(id: Int): PointerState? = pointers.firstOrNull { it.id == id }

    fun isKeyDown(keyCode: Int): Boolean = keysDown.contains(keyCode)

    fun wasKeyPressed(keyCode: Int): Boolean = keysJustPressed.contains(keyCode)

    fun wasKeyReleased(keyCode: Int): Boolean = keysJustReleased.contains(keyCode)

    fun isPointerDown(button: MouseButton = MouseButton.LEFT): Boolean =
        pointers.any { it.down && it.button == button }

    fun primaryPointer(): PointerState? = pointers.firstOrNull { it.down } ?: pointers.firstOrNull()

    /** Clears the per-frame edge state. Call at the end of each frame. */
    fun endFrame() {
        keysJustPressed.clear()
        keysJustReleased.clear()
        scrollX = 0f
        scrollY = 0f
        for (p in pointers) {
            p.justPressed = false
            p.justReleased = false
        }
    }
}

/** Maps raw key codes to named actions that gameplay code can rely on. */
class ActionMap {
    private val bindings = linkedMapOf<String, MutableSet<Int>>()

    fun bind(action: String, keyCode: Int): ActionMap {
        bindings.getOrPut(action) { linkedSetOf() }.add(keyCode)
        return this
    }

    fun unbind(action: String, keyCode: Int) {
        bindings[action]?.remove(keyCode)
    }

    fun isPressed(action: String, input: InputState): Boolean =
        bindings[action]?.any { input.isKeyDown(it) } ?: false

    fun wasPressed(action: String, input: InputState): Boolean =
        bindings[action]?.any { input.wasKeyPressed(it) } ?: false
}
