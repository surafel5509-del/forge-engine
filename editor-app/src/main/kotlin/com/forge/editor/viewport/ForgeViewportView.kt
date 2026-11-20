package com.forge.editor.viewport

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.view.Choreographer
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.forge.editor.core.EditorSession
import com.forge.editor.core.EditorTool
import com.forge.editor.core.PointerInfo
import com.forge.editor.core.ToolContext
import com.forge.engine.components.RigidBodyComponent
import com.forge.engine.input.MouseButton
import com.forge.engine.math.Rect
import com.forge.engine.render.Color as EngineColor
import com.forge.engine.render.Gizmos

/**
 * The editor viewport. Renders the live scene with the shared engine renderer
 * and translates touch input into editor tool events.
 */
class ForgeViewportView(context: Context) : View(context) {

    var session: EditorSession? = null
        set(value) {
            field = value
            invalidate()
        }

    var onStatus: ((String) -> Unit)? = null
    var onPlayInput: ((Float) -> Unit)? = null
    var onFrame: ((Float) -> Unit)? = null

    private val renderer = AndroidRenderer()
    private var lastFrameNanos = 0L
    private var pointerId = -1
    private var activeButton = MouseButton.LEFT

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val s = session ?: return false
            s.camera.zoomAt(detector.focusX, detector.focusY, detector.scaleFactor)
            return true
        }
    })

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isAttachedToWindow) return
            if (lastFrameNanos == 0L) lastFrameNanos = frameTimeNanos
            invalidate()
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        setWillNotDraw(false)
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frameCallback)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        Choreographer.getInstance().removeFrameCallback(frameCallback)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        session?.let {
            it.camera.viewportWidth = w
            it.camera.viewportHeight = h
        }
    }

    override fun onDraw(canvas: Canvas) {
        val s = session ?: run {
            canvas.drawColor(Color.rgb(14, 17, 22))
            return
        }

        val now = System.nanoTime()
        val dt = if (lastFrameNanos == 0L) 0.016f else ((now - lastFrameNanos) / 1_000_000_000.0).toFloat()
        lastFrameNanos = now

        s.camera.viewportWidth = width
        s.camera.viewportHeight = height
        s.update(dt.coerceIn(0f, 0.05f))
        onFrame?.invoke(s.fps)

        canvas.drawColor(Color.rgb(14, 17, 22))
        renderer.bind(canvas)
        renderer.beginFrame(width, height, EngineColor.TRANSPARENT)

        s.sceneRenderer.showGrid = true
        s.sceneRenderer.gridSize = s.gridSize
        s.sceneRenderer.render(s.world, renderer, s.camera, s.tilemap)

        if (!s.isPlaying) drawEditorOverlay(s)

        renderer.endFrame()
    }

    private fun drawEditorOverlay(s: EditorSession) {
        for (id in s.selection) {
            val data = s.document.entity(id) ?: continue
            val assetId = data.components["sprite"]?.let {
                (it["assetId"] as? kotlinx.serialization.json.JsonPrimitive)?.content
            } ?: ""
            val texture = s.assets.texture(assetId)
            val w = texture?.width?.toFloat() ?: 32f
            val h = texture?.height?.toFloat() ?: 32f
            val bounds = Rect(data.x, data.y, w, h)
            Gizmos.drawSelection(renderer, bounds, data.rotation)
            Gizmos.drawHandles(renderer, bounds, data.rotation)
        }

        // World origin marker.
        renderer.drawCircle(0f, 0f, 6f, EngineColor(1f, 0.55f, 0.1f, 0.9f))
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val s = session ?: return false

        if (s.isPlaying) {
            handlePlayTouch(event, s)
            return true
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pointerId = event.getPointerId(0)
                activeButton = if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) MouseButton.RIGHT else MouseButton.LEFT
                dispatch(s, MotionType.DOWN, event)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                dispatch(s, MotionType.MOVE, event)
                return true
            }

            MotionEvent.ACTION_UP -> {
                dispatch(s, MotionType.UP, event)
                pointerId = -1
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                dispatch(s, MotionType.CANCEL, event)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private enum class MotionType { DOWN, MOVE, UP, CANCEL }

    private fun dispatch(s: EditorSession, type: MotionType, event: MotionEvent) {
        val index = event.findPointerIndex(pointerId)
        if (index < 0 && type != MotionType.CANCEL) return
        val px = if (index >= 0) event.getX(index) else 0f
        val py = if (index >= 0) event.getY(index) else 0f
        val world = s.camera.screenToWorld(px, py)
        val info = PointerInfo(world.x, world.y, px, py, activeButton)
        val context = ToolContext(s, info)
        val tool: EditorTool = s.tool
        when (type) {
            MotionType.DOWN -> tool.onPointerDown(context)
            MotionType.MOVE -> tool.onPointerMove(context)
            MotionType.UP -> tool.onPointerUp(context)
            MotionType.CANCEL -> tool.onCancel(context)
        }
    }

    /** Play mode input: dragging left/right drives the player entity. */
    private fun handlePlayTouch(event: MotionEvent, s: EditorSession) {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val world = s.camera.screenToWorld(event.x, event.y)
                playerEntity(s)?.let { entity ->
                    val body = entity.getComponent("rigidbody") as? RigidBodyComponent
                    body?.velocityX = ((world.x - entity.worldPosition.x) * 2f).coerceIn(-320f, 320f)
                }
            }
            MotionEvent.ACTION_UP -> {
                playerEntity(s)?.let { entity ->
                    val body = entity.getComponent("rigidbody") as? RigidBodyComponent
                    body?.velocityX = 0f
                }
                onPlayInput?.invoke(0f)
            }
        }
    }

    private fun playerEntity(s: EditorSession) =
        s.world.entities.firstOrNull { it.name == "Player" && it.hasComponent("rigidbody") }
            ?: s.world.entities.firstOrNull { it.hasComponent("rigidbody") && it.hasComponent("sprite") }

    /** Applies a jump impulse to the player during play mode. */
    fun jump() {
        val s = session ?: return
        val entity = playerEntity(s) ?: return
        val body = entity.getComponent("rigidbody") as? RigidBodyComponent ?: return
        body.velocityY = -520f
    }

    /** Sets horizontal movement for on-screen play controls. */
    fun setMoveDirection(direction: Float) {
        val s = session ?: return
        val entity = playerEntity(s) ?: return
        val body = entity.getComponent("rigidbody") as? RigidBodyComponent ?: return
        body.velocityX = direction * 240f
    }
}
