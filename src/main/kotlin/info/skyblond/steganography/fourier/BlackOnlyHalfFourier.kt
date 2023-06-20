package info.skyblond.steganography.fourier

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.apache.commons.math3.complex.Complex
import java.awt.Color
import java.awt.image.BufferedImage

/**
 * For half-encodings, like upper half, or left half, etc.
 * Only encode black info by setting the complex to zero.
 * */
abstract class BlackOnlyHalfFourier : Fourier {
    /**
     * Throw [IllegalArgumentException] if mask size is not good
     * */
    protected abstract fun checkInputSize(width: Int, height: Int, mask: BufferedImage)
    /**
     * Calculate the center box for encoding.
     * Return (x0,y0) and (x1,y1), where point 0 is the top left of the box,
     * and point 1 is bottom right of the box.
     * */
    protected abstract fun calculateBox(
        width: Int, height: Int,
        mask: BufferedImage
    ): Pair<Pair<Int, Int>, Pair<Int, Int>>

    private suspend fun encodeChannel(channel: ComplexChannel, mask: BufferedImage) {
        val height = channel.size
        val width = channel[0].size
        checkInputSize(width, height, mask)
        val (p0 ,p1) = calculateBox(width, height, mask)
        val (x0,y0) = p0
        val (x1,y1) = p1
        // apply encoding
        for (y in 0 until mask.height) {
            for (x in 0 until mask.width) {
                // skip non-black pixel
                if (mask.getRGB(x, y) != Color.BLACK.rgb) continue
                channel[x0 + x, y0 + y] = Complex.ZERO
                channel[x1 - x, y1 - y] = Complex.ZERO
            }
        }
    }

    private suspend fun encodeChannelOrNull(channel: ComplexChannel, mask: BufferedImage?): ComplexChannel =
        channel.also { if (mask != null) encodeChannel(it, mask) }

    final override suspend fun encode(
        source: BufferedImage,
        redMask: BufferedImage?,
        greenMask: BufferedImage?,
        blueMask: BufferedImage?
    ): BufferedImage = coroutineScope {
        val fft = source.fft2d()
        val redChannel = async { encodeChannelOrNull(fft.first, redMask) }
        val greenChannel = async { encodeChannelOrNull(fft.second, greenMask) }
        val blueChannel = async { encodeChannelOrNull(fft.third, blueMask) }

        ComplexImage(
            redChannel.await(), greenChannel.await(), blueChannel.await()
        ).ifft2d().recoverToImage()
    }

    final override suspend fun decode(
        input: BufferedImage,
        energyLogKList: List<Double>
    ): List<Triple<BufferedImage, BufferedImage, BufferedImage>> = coroutineScope {
        val recoveredFFT = input.fft2d()
        energyLogKList.map { k ->
            val red = async { recoveredFFT.first.visualize { it.energyLog(k) } }
            val green = async { recoveredFFT.second.visualize { it.energyLog(k) } }
            val blue = async { recoveredFFT.third.visualize { it.energyLog(k) } }
            Triple(red.await(), green.await(), blue.await())
        }
    }
}
