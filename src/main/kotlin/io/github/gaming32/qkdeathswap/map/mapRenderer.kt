package io.github.gaming32.qkdeathswap.map

import it.unimi.dsi.fastutil.ints.Int2ByteOpenHashMap
import net.minecraft.world.level.material.MaterialColor
import net.minecraft.world.level.saveddata.maps.MapItemSavedData
import java.awt.Color
import java.awt.Image
import java.awt.image.BufferedImage

fun Image.toBufferedImage(
    x: Int = 0, y: Int = 0,
    w: Int = getWidth(null), h: Int = getHeight(null)
) = if (this is BufferedImage && type == BufferedImage.TYPE_INT_ARGB) this else {
    val result = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
    val g = result.createGraphics()
    g.drawImage(this, x, y, null)
    g.dispose()
    result
}

fun BufferedImage.toMapPixels(): ByteArray {
    val rgbPixels = IntArray(width * height).also { getRGB(0, 0, width, height, it, 0, width) }
    return ByteArray(width * height) {
        val pixel = rgbPixels[it]
        if (pixel in exactColors) {
            exactColors[pixel]
        } else {
            Color(pixel, true).matchToMapColor()
        }
    }
}

private val exactColors = Int2ByteOpenHashMap(62 * 4).apply {
    for (i in 3 until 62 * 4) {
        put(MaterialColor.getColorFromPackedId(i) or 0xff000000.toInt(), i.toByte())
    }
}

fun Color.matchToMapColor() =
    if (alpha < 128) {
        0.toByte()
    } else {
        var index = 0
        var best = -1.0
        for (i in 3 until 62 * 4) {
            val distance = colorDistance(this, Color(MaterialColor.getColorFromPackedId(i)))
            if (distance < best || best == -1.0) {
                best = distance
                index = i
            }
        }
        if (index < 128) {
            index
        } else {
            -129 + (index - 127)
        }.toByte()
    }

private fun colorDistance(c1: Color, c2: Color): Double {
    val rmean = (c1.red + c2.red) / 2.0
    val r = (c1.red - c2.red).toDouble()
    val g = (c1.green - c2.green).toDouble()
    val b = c1.blue - c2.blue
    val weightR = 2 + rmean / 256.0
    val weightG = 4.0
    val weightB = 2 + (255 - rmean) / 256.0
    return weightR * r * r + weightG * g * g + weightB * b * b
}

fun MapItemSavedData.drawImage(
    image: Image,
    x: Int = 0, y: Int = 0,
    u: Int = 0, v: Int = 0,
    w: Int = image.getWidth(null), h: Int = image.getHeight(null),
    scale: Int = 1
) {
    val buffered = image.toBufferedImage(u, v, w, h).toMapPixels()
    repeat(w) { x2 ->
        val xBase = x + x2 * scale
        repeat(h) { y2 ->
            val yBase = y + y2 * scale
            repeat(scale) { xOffset ->
                repeat(scale) { yOffset ->
                    setColor(xBase + xOffset, yBase + yOffset, buffered[y2 * w + x2])
                }
            }
        }
    }
}
