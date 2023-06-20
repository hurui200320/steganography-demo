package info.skyblond.steganography

import info.skyblond.steganography.fourier.BlackAndWhiteHalfFourier
import info.skyblond.steganography.fourier.toNamedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Visualize all pic in FFT to see if they are good to encode data.
 * */
object FourierAnalyze {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        val outputDir = File("./output/fft_analyze").apply { mkdirs() }
        val energyLogKList = listOf(0.05, 0.1, 0.2, 0.5)

        File("./output/fft_input").listFiles()!!
            .filter { it.isFile && it.extension in listOf("jpg", "png") }
            .forEach {
                println("Found file: ${it.path}")
                val image = ImageIO.read(it)
                println("Size: w: ${image.width} H:${image.height}")

                fourier.decode(image, energyLogKList).zip(energyLogKList)
                    .forEach { (images, k) ->
                        images.toNamedList().forEach { (image, channelName) ->
                            val filename = "${it.nameWithoutExtension}_fft_${channelName}_energyLog_${k}.png"
                            image.writePNG(File(outputDir, filename))
                        }
                    }

            }
    }


    // only need for decoding, aka show the FFT result
    private val fourier = object : BlackAndWhiteHalfFourier(0.0) {
        override fun checkInputSize(
            width: Int, height: Int, mask: BufferedImage
        ) = TODO("Not yet implemented")

        override fun calculateBox(
            width: Int, height: Int, mask: BufferedImage
        ): Pair<Pair<Int, Int>, Pair<Int, Int>> = TODO("Not yet implemented")
    }
}
