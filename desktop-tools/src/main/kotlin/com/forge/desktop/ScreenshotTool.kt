package com.forge.desktop

import com.forge.editor.core.EditorSession
import com.forge.engine.render.Color
import com.forge.engine.render.Gizmos
import com.forge.engine.math.Rect
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders the demo project off-screen and writes PNG snapshots. Used by the
 * build to produce preview images of the engine without an Android device.
 */
object ScreenshotTool {

    fun renderSession(session: EditorSession, width: Int, height: Int): BufferedImage {
        val renderer = JvmRenderer(width, height)
        session.camera.viewportWidth = width
        session.camera.viewportHeight = height

        renderer.beginFrame(width, height, Color(0.08f, 0.09f, 0.12f, 1f))
        session.sceneRenderer.showGrid = true
        session.sceneRenderer.gridSize = session.gridSize
        session.sceneRenderer.render(session.world, renderer, session.camera, session.tilemap)

        // Editor overlays: selection box for the first selected entity.
        val selected = session.selectedEntity()
        if (selected != null) {
            val texture = session.assets.texture(
                selected.components["sprite"]?.let {
                    (it["assetId"] as? kotlinx.serialization.json.JsonPrimitive)?.content
                } ?: ""
            )
            val w = texture?.width?.toFloat() ?: 32f
            val h = texture?.height?.toFloat() ?: 32f
            val bounds = Rect(selected.x, selected.y, w, h)
            Gizmos.drawSelection(renderer, bounds, selected.rotation)
            Gizmos.drawHandles(renderer, bounds, selected.rotation)
        }
        renderer.endFrame()
        return renderer.image
    }

    fun renderScenePreview(outputDir: File, width: Int = 1280, height: Int = 720): File {
        outputDir.mkdirs()
        val session = EditorSession()
        session.camera.position.set(640f, 352f)
        session.camera.zoom = 1f
        session.select(session.document.entities().first { it.name == "Player" }.id)

        val image = renderSession(session, width, height)
        val file = File(outputDir, "scene-preview.png")
        ImageIO.write(image, "png", file)
        return file
    }

    /** Simulates a few seconds of gameplay and captures the result. */
    fun renderPlayPreview(outputDir: File, width: Int = 1280, height: Int = 720): File {
        outputDir.mkdirs()
        val session = EditorSession()
        session.camera.position.set(640f, 352f)
        session.play()
        val dt = 1f / 60f
        repeat(150) { session.update(dt) }
        session.camera.position.set(640f, 352f)

        val renderer = JvmRenderer(width, height)
        session.camera.viewportWidth = width
        session.camera.viewportHeight = height
        renderer.beginFrame(width, height, Color(0.08f, 0.09f, 0.12f, 1f))
        session.sceneRenderer.render(session.world, renderer, session.camera, session.tilemap)
        renderer.endFrame()

        val file = File(outputDir, "play-preview.png")
        ImageIO.write(renderer.image, "png", file)
        return file
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val outDir = File(args.firstOrNull() ?: "build/screenshots")
        println("Wrote " + renderScenePreview(outDir).absolutePath)
        println("Wrote " + renderPlayPreview(outDir).absolutePath)
    }
}
