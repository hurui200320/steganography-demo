package info.skyblond.steganography

import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.random.Random

/**
 * Useful process to visualize an image.
 * */
object ImageAnalyzer {

    /**
     * Map each color to a new random and unique color.
     * LSB steganography will make some chunk noisy.
     * */
    fun randomColorMap(image: BufferedImage): BufferedImage {
        val result = BufferedImage(image.width, image.height, image.type)
        val colorMap = HashMap<Int, Int>()
        val mappedColor = HashSet<Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val key = image.getRGB(x, y)
                val mapped = colorMap.getOrPut(key) {
                    var c: Int
                    do {
                        c = Random.nextInt() or 0xFF000000.toInt()
                    } while (mappedColor.contains(c))
                    mappedColor.add(c)
                    c
                }
                result.setRGB(x, y, mapped)
            }
        }
        return result
    }

    private val channelMask = IntArray(24) { 0x01 shl it }

    /**
     * Strip one bit into an image.
     * Normally an image will be smooth, but LSB steganography will make the bit plane noisy.
     *
     * return: Array of bit plane. Index 0 is the bit 0, aka blue LSB, index 23 is bit 23, aka red MSB.
     * */
    fun bitPlaneAnalyze(image: BufferedImage): Array<BufferedImage> {
        val result = Array(24) { BufferedImage(image.width, image.height, image.type) }
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val data = image.getRGB(x, y)
                for (i in 0 until 24) {
                    val c = if (data and channelMask[i] != 0) Color.WHITE else Color.BLACK
                    result[i].setRGB(x, y, c.rgb)
                }
            }
        }
        return result
    }
}
