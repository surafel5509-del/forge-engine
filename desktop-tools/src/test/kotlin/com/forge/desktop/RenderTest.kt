package com.forge.desktop

import com.forge.engine.io.DemoAssets
import com.forge.engine.io.DemoProject
import com.forge.engine.render.Color
import com.forge.engine.render.Camera2D
import com.forge.engine.math.Rect
import com.forge.engine.tilemap.TileLayer
import com.forge.engine.tilemap.TileMap
import com.forge.engine.tilemap.Tileset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RenderTest {

    private fun pixel(renderer: JvmRenderer, x: Int, y: Int): Int =
        renderer.image.getRGB(x, y)

    @Test
    fun filledRectIsRendered() {
        val renderer = JvmRenderer(100, 100)
        renderer.beginFrame(100, 100, Color.BLACK)
        renderer.drawRect(Rect(10f, 10f, 20f, 20f), Color.WHITE)
        renderer.endFrame()

        assertEquals(0xFFFFFFFF.toInt(), pixel(renderer, 15, 15))
        assertEquals(0xFF000000.toInt(), pixel(renderer, 50, 50))
    }

    @Test
    fun spriteIsDrawnAtWorldPosition() {
        val texture = DemoAssets.textureFor(DemoProject.ASSET_COIN)!!
        val renderer = JvmRenderer(200, 200)
        val camera = Camera2D(com.forge.engine.math.Vec2(0f, 0f), zoom = 1f)
        camera.viewportWidth = 200
        camera.viewportHeight = 200

        renderer.beginFrame(200, 200, Color.BLACK)
        renderer.pushTransform(camera.viewMatrix())
        renderer.drawSprite(
            texture,
            com.forge.engine.render.TextureRegion(texture),
            0f, 0f, 24f, 24f
        )
        renderer.popTransform()
        renderer.endFrame()

        // Camera centre (100,100) maps to screen centre (100,100).
        assertTrue(pixel(renderer, 112, 112) != 0xFF000000.toInt(), "sprite should cover the screen centre")
    }

    @Test
    fun tileMapRendersEveryFilledCell() {
        val tilesetTexture = DemoAssets.textureFor(DemoProject.ASSET_TILES)!!
        val map = TileMap("Test", width = 4, height = 4, tileSize = 32)
        map.tilesets.add(Tileset("Tiles", tilesetTexture, 32, 32, columns = 4, firstGid = 1))
        val layer = TileLayer("L", 4, 4)
        layer.set(0, 0, 1)
        layer.set(1, 0, 2)
        layer.set(2, 0, 3)
        layer.set(3, 0, 4)
        map.layers.add(layer)

        val renderer = JvmRenderer(128, 64)
        val camera = Camera2D(com.forge.engine.math.Vec2(64f, 16f), zoom = 1f)
        camera.viewportWidth = 128
        camera.viewportHeight = 64

        renderer.beginFrame(128, 64, Color.BLACK)
        renderer.pushTransform(camera.viewMatrix())
        map.render(renderer)
        renderer.popTransform()
        renderer.endFrame()

        // The camera maps world (0,0) to the viewport centre (64,32).
        val colors = (0 until 4).map { pixel(renderer, it * 32 + 16, 32) }
        assertTrue(colors.all { it != 0xFF000000.toInt() }, "every filled tile should be visible: $colors")
        assertEquals(4, colors.distinct().size, "tiles should use different tile art: $colors")
    }

    @Test
    fun demoSceneRendersContent() {
        val session = com.forge.editor.core.EditorSession()
        session.camera.viewportWidth = 1280
        session.camera.viewportHeight = 720
        session.camera.position.set(640f, 352f)
        session.sceneRenderer.showGrid = false

        val renderer = JvmRenderer(1280, 720)
        renderer.beginFrame(1280, 720, Color(0.08f, 0.09f, 0.12f, 1f))
        session.sceneRenderer.render(session.world, renderer, session.camera, session.tilemap)
        renderer.endFrame()

        var nonBackground = 0
        var distinct = HashSet<Int>()
        for (y in 0 until 720 step 4) {
            for (x in 0 until 1280 step 4) {
                val p = pixel(renderer, x, y)
                distinct.add(p)
                if (p != 0xFF171E26.toInt()) nonBackground++
            }
        }
        assertTrue(nonBackground > 500, "the demo scene should paint a lot of pixels, got $nonBackground")
        assertTrue(distinct.size > 8, "the demo scene should contain many colours, got ${distinct.size}")
    }
}
