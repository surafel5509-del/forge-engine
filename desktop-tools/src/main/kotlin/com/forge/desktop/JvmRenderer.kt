package com.forge.desktop

import com.forge.engine.math.Mat2D
import com.forge.engine.math.Rect
import com.forge.engine.render.Color
import com.forge.engine.render.PixelTexture
import com.forge.engine.render.Renderer
import com.forge.engine.render.Texture
import com.forge.engine.render.TextureRegion
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.RenderingHints
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage

/**
 * Desktop implementation of the engine [Renderer] contract, backed by
 * java.awt. It renders exactly the same calls the Android renderer receives,
 * which makes it useful for automated screenshots and regression checks.
 */
class JvmRenderer(
    private var width: Int = 1280,
    private var height: Int = 720
) : Renderer {

    var image: BufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        private set

    private var graphics: java.awt.Graphics2D? = null
    private val textureCache = HashMap<String, BufferedImage>()
    private val transformStack = ArrayDeque<com.forge.engine.math.Mat2D>()
    private var currentMatrix = com.forge.engine.math.Mat2D()

    fun resize(newWidth: Int, newHeight: Int) {
        width = newWidth
        height = newHeight
        image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    }

    override fun beginFrame(width: Int, height: Int, clearColor: Color) {
        resize(width, height)
        val g = image.createGraphics()
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
        g.composite = AlphaComposite.Src
        g.color = java.awt.Color(clearColor.r, clearColor.g, clearColor.b, clearColor.a)
        g.fillRect(0, 0, width, height)
        g.composite = AlphaComposite.SrcOver
        graphics = g
        transformStack.clear()
        currentMatrix = com.forge.engine.math.Mat2D()
    }

    override fun endFrame() {
        graphics?.dispose()
        graphics = null
    }

    override fun pushTransform(matrix: Mat2D) {
        val g = graphics ?: return
        transformStack.addLast(currentMatrix.copy())
        // Mat2D.multiply post-multiplies, matching the engine convention (M * m).
        currentMatrix.multiply(matrix)
        g.setTransform(toAffine(currentMatrix))
    }

    override fun popTransform() {
        val g = graphics ?: return
        val previous = transformStack.removeLastOrNull() ?: return
        currentMatrix = previous
        g.setTransform(toAffine(currentMatrix))
    }

    private fun toAffine(m: Mat2D) = AffineTransform(
        m.a.toDouble(), m.b.toDouble(),
        m.c.toDouble(), m.d.toDouble(),
        m.tx.toDouble(), m.ty.toDouble()
    )

    override fun pushClip(rect: Rect) {
        val g = graphics ?: return
        g.clipRect(rect.x.toInt(), rect.y.toInt(), rect.width.toInt(), rect.height.toInt())
    }

    override fun popClip() = Unit

    override fun drawSprite(
        texture: Texture,
        region: TextureRegion,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        rotation: Float,
        color: Color,
        flipX: Boolean,
        flipY: Boolean
    ) {
        val g = graphics ?: return
        val source = toBufferedImage(texture) ?: return
        val sx = region.x
        val sy = region.y
        val sw = region.width
        val sh = region.height

        val local = Mat2D()
        local.translate(x + width / 2f, y + height / 2f)
        if (rotation != 0f) local.rotate(rotation)
        if (flipX) local.scale(-1f, 1f)
        if (flipY) local.scale(1f, -1f)
        local.translate(-width / 2f, -height / 2f)

        val combined = currentMatrix.copy().multiply(local)
        g.setTransform(toAffine(combined))
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.a.coerceIn(0f, 1f))
        // NOTE: the 10 argument overload of drawImage takes the destination
        // rectangle first and the source rectangle second.
        g.drawImage(
            source,
            0, 0, width.toInt(), height.toInt(),
            sx, sy, sx + sw, sy + sh,
            null
        )
        g.composite = AlphaComposite.SrcOver
        g.setTransform(toAffine(currentMatrix))
    }

    override fun drawRect(rect: Rect, color: Color) {
        val g = graphics ?: return
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.a.coerceIn(0f, 1f))
        g.color = java.awt.Color(color.r, color.g, color.b, color.a)
        g.fillRect(rect.x.toInt(), rect.y.toInt(), rect.width.toInt(), rect.height.toInt())
        g.composite = AlphaComposite.SrcOver
    }

    override fun drawRectOutline(rect: Rect, color: Color, thickness: Float) {
        val g = graphics ?: return
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.a.coerceIn(0f, 1f))
        g.color = java.awt.Color(color.r, color.g, color.b, color.a)
        g.stroke = BasicStroke(thickness.coerceAtLeast(0.5f))
        g.drawRect(
            rect.x.toInt(),
            rect.y.toInt(),
            rect.width.toInt(),
            rect.height.toInt()
        )
        g.composite = AlphaComposite.SrcOver
    }

    override fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, color: Color, thickness: Float) {
        val g = graphics ?: return
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.a.coerceIn(0f, 1f))
        g.color = java.awt.Color(color.r, color.g, color.b, color.a)
        g.stroke = BasicStroke(thickness.coerceAtLeast(0.5f))
        g.drawLine(x1.toInt(), y1.toInt(), x2.toInt(), y2.toInt())
        g.composite = AlphaComposite.SrcOver
    }

    override fun drawCircle(x: Float, y: Float, radius: Float, color: Color) {
        val g = graphics ?: return
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.a.coerceIn(0f, 1f))
        g.color = java.awt.Color(color.r, color.g, color.b, color.a)
        val d = (radius * 2).toInt()
        g.fillOval((x - radius).toInt(), (y - radius).toInt(), d, d)
        g.composite = AlphaComposite.SrcOver
    }

    override fun drawTexture(texture: Texture, region: TextureRegion?, dest: Rect, color: Color) {
        val source = toBufferedImage(texture) ?: return
        val g = graphics ?: return
        val sx = region?.x ?: 0
        val sy = region?.y ?: 0
        val sw = region?.width ?: texture.width
        val sh = region?.height ?: texture.height
        g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.a.coerceIn(0f, 1f))
        g.drawImage(
            source,
            dest.x.toInt(), dest.y.toInt(), dest.right.toInt(), dest.bottom.toInt(),
            sx, sy, sx + sw, sy + sh,
            null
        )
        g.composite = AlphaComposite.SrcOver
    }

    private fun toBufferedImage(texture: Texture): BufferedImage? {
        textureCache[texture.id]?.let { return it }
        val pixels = (texture as? PixelTexture)?.pixels ?: return null
        val img = BufferedImage(texture.width, texture.height, BufferedImage.TYPE_INT_ARGB)
        img.setRGB(0, 0, texture.width, texture.height, pixels, 0, texture.width)
        textureCache[texture.id] = img
        return img
    }
}
