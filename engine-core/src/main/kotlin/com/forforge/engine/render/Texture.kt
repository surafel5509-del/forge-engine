package com.forge.engine.render

import com.forge.engine.math.Rect

/** Platform agnostic handle to a bitmap owned by the renderer backend. */
interface Texture {
    val id: String
    val width: Int
    val height: Int
}

/**
 * A texture whose pixels live in engine memory as ARGB ints. Used for procedural
 * assets and for headless (JVM) rendering, so no image decoder is required.
 */
class PixelTexture(
    override val id: String,
    override val width: Int,
    override val height: Int,
    val pixels: IntArray
) : Texture {
    init {
        require(pixels.size >= width * height) { "Pixel buffer too small for $id" }
    }
}

/** Sub-rectangle of a texture, expressed in pixels. */
data class TextureRegion(
    val texture: Texture,
    @JvmField val x: Int = 0,
    @JvmField val y: Int = 0,
    @JvmField val width: Int = texture.width,
    @JvmField val height: Int = texture.height
) {
    val u0: Float get() = if (texture.width == 0) 0f else x.toFloat() / texture.width
    val v0: Float get() = if (texture.height == 0) 0f else y.toFloat() / texture.height
    val u1: Float get() = if (texture.width == 0) 0f else (x + width).toFloat() / texture.width
    val v1: Float get() = if (texture.height == 0) 0f else (y + height).toFloat() / texture.height

    val bounds: Rect get() = Rect(x.toFloat(), y.toFloat(), width.toFloat(), height.toFloat())

    companion object {
        fun full(texture: Texture): TextureRegion = TextureRegion(texture)
    }
}
