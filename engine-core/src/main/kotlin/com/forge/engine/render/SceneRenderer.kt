package com.forge.engine.render

import com.forge.engine.components.CameraComponent
import com.forge.engine.components.SpriteComponent
import com.forge.engine.ecs.Entity
import com.forge.engine.ecs.World
import com.forge.engine.math.Mat2D
import com.forge.engine.math.Rect
import com.forge.engine.math.Vec2
import com.forge.engine.systems.ParticleSystem
import com.forge.engine.tilemap.TileMap

/** Resolves asset ids to renderable textures. */
interface AssetProvider {
    fun texture(assetId: String): Texture?
}

/** Simple in-memory asset provider. */
class MapAssetProvider(private val textures: MutableMap<String, Texture> = mutableMapOf()) : AssetProvider {
    override fun texture(assetId: String): Texture? = textures[assetId]

    fun put(assetId: String, texture: Texture) {
        textures[assetId] = texture
    }
}

/**
 * Draws a [World] using the platform agnostic [Renderer] contract.
 * Entities are sorted by layer then z index so draw order matches the editor.
 */
class SceneRenderer(private val assets: AssetProvider) {

    /** Optional world space grid, useful while editing. */
    var gridSize: Float = 32f
    var showGrid: Boolean = false
    var gridColor: Color = Color(1f, 1f, 1f, 0.08f)

    fun render(world: World, renderer: Renderer, camera: Camera2D, tilemap: TileMap? = null) {
        renderer.pushTransform(camera.viewMatrix())

        if (showGrid && gridSize > 0f) {
            drawGrid(renderer, camera, gridSize)
        }

        tilemap?.render(renderer)

        val entities = world.entities.filter { it.visible }
            .sortedWith(compareBy({ layerOf(it) }, { zOf(it) }, { it.id }))

        for (entity in entities) {
            val sprite = entity.getComponent("sprite") as? SpriteComponent ?: continue
            val texture = assets.texture(sprite.assetId) ?: continue
            val region = TextureRegion(
                texture,
                sprite.regionX,
                sprite.regionY,
                if (sprite.regionW > 0) sprite.regionW else texture.width,
                if (sprite.regionH > 0) sprite.regionH else texture.height
            )
            renderer.drawSprite(
                texture, region,
                entity.worldPosition.x, entity.worldPosition.y,
                region.width.toFloat(), region.height.toFloat(),
                entity.worldRotation, sprite.color, sprite.flipX, sprite.flipY
            )
        }

        world.system("particle")?.let { system ->
            (system as? ParticleSystem)?.particles?.forEach { p ->
                val alpha = (p.life / p.maxLife).coerceIn(0f, 1f)
                renderer.drawCircle(p.x, p.y, p.size * 0.5f, p.color.withAlpha(alpha))
            }
        }

        renderer.popTransform()
    }

    private fun layerOf(entity: Entity): Int =
        (entity.getComponent("sprite") as? SpriteComponent)?.layer ?: 0

    private fun zOf(entity: Entity): Float =
        (entity.getComponent("sprite") as? SpriteComponent)?.zIndex ?: 0f

    private fun drawGrid(renderer: Renderer, camera: Camera2D, cell: Float) {
        val inv = camera.inverseViewMatrix()
        val topLeft = inv.apply(0f, 0f)
        val bottomRight = inv.apply(camera.viewportWidth.toFloat(), camera.viewportHeight.toFloat())
        val startX = (Math.floor((topLeft.x / cell).toDouble()).toFloat()) * cell
        val startY = (Math.floor((topLeft.y / cell).toDouble()).toFloat()) * cell
        var x = startX
        while (x <= bottomRight.x) {
            renderer.drawLine(x, topLeft.y, x, bottomRight.y, gridColor, 1f)
            x += cell
        }
        var y = startY
        while (y <= bottomRight.y) {
            renderer.drawLine(topLeft.x, y, bottomRight.x, y, gridColor, 1f)
            y += cell
        }
    }

    /** Finds the topmost entity whose bounds contain the given world point. */
    fun pick(world: World, wx: Float, wy: Float): Entity? {
        val candidates = world.entities.filter { it.visible }.sortedWith(
            compareByDescending<Entity> { layerOf(it) }.thenByDescending { zOf(it) }
        )
        for (entity in candidates) {
            val sprite = entity.getComponent("sprite") as? SpriteComponent ?: continue
            val texture = assets.texture(sprite.assetId) ?: continue
            val w = (if (sprite.regionW > 0) sprite.regionW else texture.width).toFloat()
            val h = (if (sprite.regionH > 0) sprite.regionH else texture.height).toFloat()
            val bounds = Rect(entity.worldPosition.x, entity.worldPosition.y, w, h)
            if (bounds.contains(wx, wy)) return entity
        }
        return null
    }
}

/** Editor overlays: selection outline, transform handles and anchor points. */
object Gizmos {
    val SELECTION = Color(0.25f, 0.75f, 1f, 1f)
    val HANDLE = Color(1f, 0.55f, 0.1f, 1f)
    val GRID = Color(1f, 1f, 1f, 0.06f)

    fun drawSelection(renderer: Renderer, bounds: Rect, rotation: Float = 0f) {
        val matrix = Mat2D()
        matrix.translate(bounds.x, bounds.y)
        matrix.rotate(rotation)
        renderer.pushTransform(matrix)
        renderer.drawRectOutline(Rect(0f, 0f, bounds.width, bounds.height), SELECTION, 2f)
        renderer.popTransform()
    }

    fun drawHandles(renderer: Renderer, bounds: Rect, rotation: Float = 0f) {
        val matrix = Mat2D()
        matrix.translate(bounds.x, bounds.y)
        matrix.rotate(rotation)
        renderer.pushTransform(matrix)
        val size = 6f
        val points = listOf(
            0f to 0f,
            bounds.width to 0f,
            0f to bounds.height,
            bounds.width to bounds.height,
            bounds.width * 0.5f to 0f
        )
        for ((px, py) in points) {
            renderer.drawRect(Rect(px - size * 0.5f, py - size * 0.5f, size, size), HANDLE)
        }
        renderer.popTransform()
    }

    fun drawArrow(renderer: Renderer, from: Vec2, to: Vec2, color: Color = HANDLE) {
        renderer.drawLine(from.x, from.y, to.x, to.y, color, 2f)
        val angle = Math.atan2((to.y - from.y).toDouble(), (to.x - from.x).toDouble()).toFloat()
        val head = 10f
        renderer.drawLine(
            to.x, to.y,
            to.x - Math.cos((angle - 0.5f).toDouble()).toFloat() * head,
            to.y - Math.sin((angle - 0.5f).toDouble()).toFloat() * head,
            color, 2f
        )
        renderer.drawLine(
            to.x, to.y,
            to.x - Math.cos((angle + 0.5f).toDouble()).toFloat() * head,
            to.y - Math.sin((angle + 0.5f).toDouble()).toFloat() * head,
            color, 2f
        )
    }
}

/** Camera helpers shared by the runtime and the editor. */
object CameraUtils {
    fun activeCamera(world: World, fallback: Camera2D): Camera2D {
        val entity = world.entities.firstOrNull { it.hasComponent("camera") } ?: return fallback
        val component = entity.getComponent("camera") as CameraComponent
        fallback.position.set(entity.worldPosition)
        fallback.zoom = component.zoom
        fallback.rotation = component.rotation
        return fallback
    }
}
