package info.skyblond.steganography.fourier

import org.apache.commons.math3.complex.Complex
import java.awt.image.BufferedImage

interface Fourier {
    /**
     * Encode the given mask into different channels.
     * */
    suspend fun encode(
        source: BufferedImage,
        redMask: BufferedImage? = null,
        greenMask: BufferedImage? = null,
        blueMask: BufferedImage? = null,
    ): BufferedImage

    /**
     * Decode the input using [Complex.energyLog] visualization.
     * You can supply multiple K values to see which one is the best.
     * */
    suspend fun decode(
        input: BufferedImage,
        energyLogKList: List<Double>
    ): List<Triple<BufferedImage, BufferedImage, BufferedImage>>

    suspend fun decode(
        input: BufferedImage,
        energyLogK: Double
    ): Triple<BufferedImage, BufferedImage, BufferedImage> = decode(input, listOf(energyLogK))[0]
}
