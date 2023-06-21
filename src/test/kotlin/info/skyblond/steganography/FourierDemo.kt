package info.skyblond.steganography

import info.skyblond.steganography.fourier.BlackAndWhiteHorizontalFourier
import info.skyblond.steganography.fourier.BlackAndWhiteVerticalFourier
import info.skyblond.steganography.fourier.Fourier
import info.skyblond.steganography.fourier.toNamedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object FourierDemo {
    private const val amp = 0.2

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        File("./pic").listFiles()!!
            .filter { it.isFile && it.extension in listOf("jpg", "png") }
            .forEach {
                println("Found file: ${it.path}")
                val image = ImageIO.read(it)
                val outputDir = File("./output/fft_demo/${it.name.replace(".", "_")}")
                println("Output dir: ${outputDir.path}")

                val (fourier, mask) = getFourierAndMask(image)
                testFourier(fourier, image, mask, outputDir)
            }
    }

    private val vertical = BlackAndWhiteVerticalFourier(amp) to ImageIO.read(File("./pic/fft_mask/mask2_v.png"))
    private val horizontal = BlackAndWhiteHorizontalFourier(amp) to ImageIO.read(File("./pic/fft_mask/mask2_h.png"))
//    private val vertical = BlackAndWhiteVerticalFourier(amp) to ImageIO.read(File("./pic/fft_mask/mask.png"))
//    private val horizontal = BlackAndWhiteHorizontalFourier(amp) to ImageIO.read(File("./pic/fft_mask/mask.png"))

    /**
     * Get Fourier based on the ratio.
     * */
    private fun getFourierAndMask(image: BufferedImage): Pair<Fourier, BufferedImage> =
        if (image.width.toDouble() / image.height > 1.0) vertical else horizontal

    private suspend fun testFourier(
        fourier: Fourier, image: BufferedImage,
        mask: BufferedImage, outputDir: File
    ) = runCatching {
        println("Applying steganography...")
        // here we use the same mask for both RGB
        val resultImage = fourier.encode(image, mask, mask, mask)
        outputDir.mkdirs()
        image.writePNG(File(outputDir, "ori_copy.png"))
        resultImage.writePNG(File(outputDir, "fft_result.png"))

        println("Decoding recovered image...")
        val images = fourier.decode(resultImage, amp)
        images.toNamedList().forEach { (image, channelName) ->
            val filename = "fft_recovered_${channelName}_energyLog_${amp}.png"
            image.writePNG(File(outputDir, filename))
        }

        println("Analyzing image...")
        analyze("ori", image, outputDir)
        analyze("fft", resultImage, outputDir)
    }.onFailure { it.printStackTrace(); println("Failed to encode, skip to next one...") }

    private suspend fun analyze(name: String, image: BufferedImage, outputDir: File) {
        repeat(3) {
            ImageAnalyzer.randomColorMap(image).writePNG(File(outputDir, "${name}_random_color_map_${it + 1}.png"))
        }
        ImageAnalyzer.bitPlaneAnalyze(image).mapIndexed { i, bufferedImage ->
            val fileName = when (i) {
                in 0 until 8 -> "${name}_blue_plane_${i}.png"
                in 8 until 16 -> "${name}_green_plane_${i - 8}.png"
                in 16 until 24 -> "${name}_red_plane_${i - 16}.png"
                else -> error("Unknown bit $i")
            }
            bufferedImage.writePNG(File(outputDir, fileName))
        }
    }
}
