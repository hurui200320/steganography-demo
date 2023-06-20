package info.skyblond.steganography

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.apache.commons.math3.complex.Complex
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.security.MessageDigest
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageTypeSpecifier
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream

fun ByteArray.toHexString(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

fun String.toHexBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

fun Int.toByteArray(): ByteArray {
    val buffer = ByteArray(Int.SIZE_BYTES)
    for (i in 0 until Int.SIZE_BYTES) buffer[i] = (this shr (i * 8)).toByte()
    return buffer
}

fun ByteArray.toInt(): Int {
    require(this.size == Int.SIZE_BYTES)
    return (this[3].toInt() shl 24) or
            (this[2].toInt() and 0xff shl 16) or
            (this[1].toInt() and 0xff shl 8) or
            (this[0].toInt() and 0xff)
}

fun Int.toBitString(): String = this.toUInt().toString(2)
    .let { "0".repeat(32 - it.length) + it }
    .chunked(8).joinToString("_")

/**
 * Write PNG with the highest compression. Take more time, but result in smaller file.
 *
 * 0.0 means highest compression, which takes a lot of time.
 * 1.0 means no compression.
 * */
suspend fun BufferedImage.writePNG(target: File, compressionQuality: Double = 0.5): Unit = coroutineScope {
    FileImageOutputStream(target).use { out ->
        val type = ImageTypeSpecifier.createFromRenderedImage(this@writePNG)
        val writer = ImageIO.getImageWriters(type, "png").next()
        val param = writer.defaultWriteParam
        require(param.canWriteCompressed())
        param.compressionMode = ImageWriteParam.MODE_EXPLICIT
        param.compressionQuality = compressionQuality.toFloat()
        writer.output = out
        withContext(Dispatchers.IO) {
            writer.write(null, IIOImage(this@writePNG, null, null), param)
            writer.dispose()
        }
    }
}

fun String.md5(): String {
    val digest = MessageDigest.getInstance("MD5")
    digest.update(this.encodeToByteArray())
    return digest.digest().toHexString()
}


operator fun Complex.plus(complex: Complex): Complex = this.add(complex)
operator fun Complex.minus(complex: Complex): Complex = this.add(complex.negate())
operator fun Complex.times(complex: Complex): Complex = this.multiply(complex)
operator fun Complex.times(double: Double): Complex = this.multiply(double)
operator fun Complex.div(complex: Complex): Complex = this.divide(complex)
operator fun Complex.div(double: Double): Complex = this.divide(double)
operator fun Complex.unaryMinus(): Complex = this.negate()

/**
 * std::vector<T,Allocator>::insert.
 *
 * Insert [count] copies of the [value] before [pos]
 * */
fun <T> MutableList<T>.add(pos: Int, count: Int, value: T) {
    repeat(count){
        this.add(pos, value)
    }
}

fun Color(r: Double, g: Double, b: Double): Color = Color(r.toFloat(), g.toFloat(), b.toFloat())
