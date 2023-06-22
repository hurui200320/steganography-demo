package info.skyblond.steganography.cosine

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import kotlin.math.roundToInt

/**
 * 8x8 chunk for DCT operation
 * */
typealias DCTChunk = Array<DoubleArray>
/**
 * Each channel is a 2d chunk array
 * */
typealias DCTChannel = Array<Array<DCTChunk>>

/**
 * Each pic has 3 channel (R, G, B)
 * */
data class DCTImage(
    val width: Int, val height: Int,
    val redChannel: DCTChannel,
    val greenChannel: DCTChannel,
    val blueChannel: DCTChannel,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is DCTImage) return false

        if (!redChannel.contentDeepEquals(other.redChannel)) return false
        if (!greenChannel.contentDeepEquals(other.greenChannel)) return false
        return blueChannel.contentDeepEquals(other.blueChannel)
    }

    override fun hashCode(): Int {
        var result = redChannel.contentDeepHashCode()
        result = 31 * result + greenChannel.contentDeepHashCode()
        result = 31 * result + blueChannel.contentDeepHashCode()
        return result
    }
}

fun DCTChannel.getChunk(xIndex: Int, yIndex: Int): DCTChunk =
    this[yIndex][xIndex]

operator fun DCTChannel.get(x: Int, y: Int): Double =
    this.getChunk(x / 8, y / 8)[y % 8][x % 8]

fun DCTChannel.getInt(x: Int, y: Int): Int = this[x, y].roundToInt()

operator fun DCTChannel.set(x: Int, y: Int, value: Double) {
    this.getChunk(x / 8, y / 8)[y % 8][x % 8] = value
}

operator fun DCTChannel.set(x: Int, y: Int, value: Int) {
    this[x, y] = value.toDouble()
}

private fun DCTChunk(): DCTChunk = Array(8) { DoubleArray(8) }
private fun DCTChannel(chunkWidth: Int, chunkHeight: Int): DCTChannel =
    Array(chunkHeight) { Array(chunkWidth) { DCTChunk() } }

/**
 * Turn [BufferedImage] into DCT representation.
 * Each pixel is ranged from -128..127.
 * */
fun DCTImage(image: BufferedImage): DCTImage {
    val chunkWidth = image.width / 8 + if (image.width % 8 != 0) 1 else 0
    val chunkHeight = image.height / 8 + if (image.height % 8 != 0) 1 else 0

    val red = DCTChannel(chunkWidth, chunkHeight)
    val green = DCTChannel(chunkWidth, chunkHeight)
    val blue = DCTChannel(chunkWidth, chunkHeight)

    for (y in 0 until image.height) {
        for (x in 0 until image.width) {
            val rgb = image.getRGB(x, y)
            val color = Color(rgb)
            red[x, y] = color.red - 128
            green[x, y] = color.green - 128
            blue[x, y] = color.blue - 128
        }
    }
    return DCTImage(image.width, image.height, red, green, blue)
}

fun DCTChunk.dct2d(): DCTChunk {
    // result: [height, width]
    val rowTransformed = this.indices.map { y ->
        // feed row
        DCT8.dct8(this[y])
    }

    // output shape is [width, height]
    val columnTransformed = this[0].indices.map { x ->
        // feed columns
        val columnData = DoubleArray(this.size) { y -> rowTransformed[y][x] }
        DCT8.dct8(columnData)
    }

    // recover the structure back to [height, weight]
    return Array(this.size) { y ->
        DoubleArray(this[0].size) { x -> columnTransformed[x][y] }
    }
}

fun DCTChunk.idct2d(): DCTChunk {
    // result: [height, width]
    val rowTransformed = this.indices.map { y ->
        // feed row
        DCT8.idct8(this[y])
    }

    // output shape is [width, height]
    val columnTransformed = this[0].indices.map { x ->
        // feed columns
        val columnData = DoubleArray(this.size) { y -> rowTransformed[y][x] }
        DCT8.idct8(columnData)
    }

    // recover the structure back to [height, weight]
    return Array(this.size) { y ->
        DoubleArray(this[0].size) { x -> columnTransformed[x][y] }
    }
}

suspend fun DCTChannel.dct2d(): DCTChannel = coroutineScope {
    this@dct2d.map { row ->
        row.map { async { it.dct2d() } }.awaitAll().toTypedArray()
    }.toTypedArray()
}

suspend fun DCTChannel.idct2d(): DCTChannel = coroutineScope {
    this@idct2d.map { row ->
        row.map { async { it.idct2d() } }.awaitAll().toTypedArray()
    }.toTypedArray()
}

suspend fun DCTImage.dct2d(): DCTImage = DCTImage(
    this.width, this.height,
    this.redChannel.dct2d(),
    this.greenChannel.dct2d(),
    this.blueChannel.dct2d()
)

suspend fun DCTImage.idct2d(): DCTImage = DCTImage(
    this.width, this.height,
    this.redChannel.idct2d(),
    this.greenChannel.idct2d(),
    this.blueChannel.idct2d()
)

fun DCTChannel.dumpToCSV(file: File) = file.printWriter().use {writer ->
    val paddedHeight = this.size * 8
    val paddedWidth = this[0].size * 8
    for (y in 0 until paddedHeight) {
        for (x in 0 until paddedWidth) {
            val value = this.getInt(x, y)
            writer.print(value)
            writer.print(", ")
        }
        writer.println()
    }
    writer.close()
}
