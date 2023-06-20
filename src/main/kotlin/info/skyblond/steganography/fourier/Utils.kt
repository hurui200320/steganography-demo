package info.skyblond.steganography.fourier

import info.skyblond.steganography.Color
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.commons.math3.complex.Complex
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.log10

typealias ComplexChannel = Array<Array<Complex>>
typealias ComplexImage = Triple<ComplexChannel, ComplexChannel, ComplexChannel>

/**
 * Extract the RGB channel into Complex matrix.
 * The real part is normalized pixel value (0~1), the imag part is 0.
 * */
fun BufferedImage.toComplexMatrix(): ComplexImage {
    val red = Array(this.height) { Array(this.width) { Complex.ZERO } }
    val green = Array(this.height) { Array(this.width) { Complex.ZERO } }
    val blue = Array(this.height) { Array(this.width) { Complex.ZERO } }

    for (y in 0 until this.height) {
        for (x in 0 until this.width) {
            val rgb = this.getRGB(x, y)
            val color = Color(rgb)
            red[y][x] = Complex(color.red / 255.0, 0.0)
            green[y][x] = Complex(color.green / 255.0, 0.0)
            blue[y][x] = Complex(color.blue / 255.0, 0.0)
        }
    }

    return Triple(red, green, blue)
}

suspend fun ComplexChannel.fft2d(): ComplexChannel = coroutineScope {
    // result: [height, width]
    val rowTransformed = this@fft2d.indices.map { y ->
        // feed each horizontal line into fft
        async { FFT.fft(this@fft2d[y]) }
    }.awaitAll()

    // here we calculate based on columns
    // aka the output shape is [width, height]
    val columnTransformed = this@fft2d[0].indices.map { x ->
        // we need to collect each vertical line
        async {
            val columnData = Array(this@fft2d.size) { y -> rowTransformed[y][x] }
            FFT.fft(columnData)
        }
    }.awaitAll()

    // recover the structure back to [height, weight]
    Array(this@fft2d.size) { y ->
        Array(this@fft2d[0].size) { x ->
            columnTransformed[x][y]
        }
    }
}

suspend fun ComplexChannel.ifft2d(): ComplexChannel = coroutineScope {
    // result: [height, width]
    val rowTransformed = this@ifft2d.indices.map { y ->
        // feed each horizontal line into fft
        async { FFT.ifft(this@ifft2d[y]) }
    }.awaitAll()

    // here we calculate based on columns
    // aka the output shape is [width, height]
    val columnTransformed = this@ifft2d[0].indices.map { x ->
        // we need to collect each vertical line
        async {
            val columnData = Array(this@ifft2d.size) { y -> rowTransformed[y][x] }
            FFT.ifft(columnData)
        }
    }.awaitAll()

    // recover the structure back to [height, weight]
    Array(this@ifft2d.size) { y ->
        Array(this@ifft2d[0].size) { x ->
            columnTransformed[x][y]
        }
    }
}

suspend fun ComplexImage.fft2d(): ComplexImage =
    Triple(this.first.fft2d(), this.second.fft2d(), this.third.fft2d())

suspend fun ComplexImage.ifft2d(): ComplexImage =
    Triple(this.first.ifft2d(), this.second.ifft2d(), this.third.ifft2d())

suspend fun BufferedImage.fft2d(): ComplexImage =
    this.toComplexMatrix().fft2d()

/**
 * Turn the ifft2d result back into [BufferedImage].
 *
 * Ignore [Complex.imaginary] since the original input has no imag,
 * and imag has no meaning to an image.
 * */
fun ComplexImage.recoverToImage(): BufferedImage {
    val recovered = BufferedImage(this.first[0].size, this.first.size, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until recovered.height) {
        for (x in 0 until recovered.width) {
            // since the input has no imag,
            // thus we ignore the imag when turning back to image
            val c = Color(
                this.first[x, y].real.coerceIn(0.0, 1.0).toFloat(),
                this.second[x, y].real.coerceIn(0.0, 1.0).toFloat(),
                this.third[x, y].real.coerceIn(0.0, 1.0).toFloat()
            )
            recovered.setRGB(x, y, c.rgb)
        }
    }
    return recovered
}

/**
 * shift the (0,0), aka the top left, to the center (w/2, h/2):
 * Google FFT shift for more info.
 * */
fun fftShift(width: Int, height: Int, x: Int, y: Int): Pair<Int, Int> {
    val newX = (x + width / 2) % width
    val newY = (y + height / 2) % height
    return newX to newY
}

/**
 * Get a complex number in the channel, but use the [fftShift]ed coordinates.
 * */
fun ComplexChannel.getShifted(x: Int, y: Int): Complex =
    fftShift(this[0].size, this.size, x, y).let { (newX, newY) ->
        this[newY][newX]
    }

/**
 * Get a complex number in the channel, but use the normal coordinates.
 * */
operator fun ComplexChannel.get(x: Int, y: Int): Complex = this[y][x]

/**
 * Set a complex number in the channel, but use the normal coordinates.
 * */
operator fun ComplexChannel.set(x: Int, y: Int, value: Complex) {
    this[y][x] = value
}

fun Triple<BufferedImage, BufferedImage, BufferedImage>.toNamedList(): List<Pair<BufferedImage, String>> =
    listOf(
        this.first to "red",
        this.second to "green",
        this.third to "blue"
    )

/**
 * As FFT result, the [Complex.abs] represent the energy of this freq.
 * Using [log10] will turn it into a more pleasant visual representation.
 * Formula: log(1 + [k] * abs)
 * */
fun Complex.energyLog(k: Double): Double = log10(this.abs() * k + 1)

/**
 * Turn a [ComplexChannel] (fft result) into a visual representation.
 * The [transform] control what part to visualize, the result will be
 * clamped into 0.0 ~ 1.0.
 *
 * [shifted] will shift the (0,0) into the center:
 * [[1,2],[3,4]] will become [[4,3],[2,1]]
 * */
fun ComplexChannel.visualize(
    shifted: Boolean = false,
    transform: (Complex) -> Double
): BufferedImage {
    val visual = BufferedImage(this[0].size, this.size, BufferedImage.TYPE_INT_ARGB)
    for (y in 0 until visual.height) {
        for (x in 0 until visual.width) {
            val complex = if (shifted) this.getShifted(x, y) else this[x, y]
            val v = transform(complex).coerceIn(0.0, 1.0)
            visual.setRGB(x, y, Color(v, v, v).rgb)
        }
    }
    return visual
}
