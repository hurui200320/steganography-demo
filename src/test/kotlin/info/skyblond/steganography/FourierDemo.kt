package info.skyblond.steganography

import info.skyblond.steganography.fourier.BlackOnlyHorizontalFourier
import info.skyblond.steganography.fourier.BlackOnlyVerticalFourier
import info.skyblond.steganography.fourier.Fourier
import info.skyblond.steganography.fourier.toNamedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object FourierDemo {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        val energyLogKList = listOf(0.05, 0.1, 0.5, 1.0, 1.5)

        File("./pic").listFiles()!!
            .filter { it.isFile && it.extension in listOf("jpg", "png") }
            .forEach {
                val md5 = it.path.md5()
                println("Found file: ${it.path}")
                val image = ImageIO.read(it)
                val outputDir = File("./output/fft_demo/$md5")
                println("Output dir: ${outputDir.path}")

                val (fourier, mask) = getFourierAndMask(image)
                testFourier(fourier, image, mask, energyLogKList, outputDir)
            }
    }


    private val vertical = BlackOnlyVerticalFourier() to ImageIO.read(File("./pic/fft_mask/mask2_v.png"))
    private val horizontal = BlackOnlyHorizontalFourier() to ImageIO.read(File("./pic/fft_mask/mask2_h.png"))

    /**
     * Get Fourier based on the ratio.
     * */
    private fun getFourierAndMask(image: BufferedImage): Pair<Fourier, BufferedImage> =
        if (image.width.toDouble() / image.height > 1.0) vertical else horizontal

    private suspend fun testFourier(
        fourier: Fourier, image: BufferedImage,
        mask: BufferedImage, energyLogKList: List<Double>,
        outputDir: File
    ) = runCatching {
        println("Applying steganography...")
        // here we use the same mask for both RGB
        val resultImage = fourier.encode(image, mask, mask, mask)
        outputDir.mkdirs()
        image.writePNG(File(outputDir, "ori_copy.png"))
        resultImage.writePNG(File(outputDir, "fft_result.png"))

        println("Analyzing image...")
        analyze("ori", image, outputDir)
        analyze("fft", resultImage, outputDir)

        println("Decoding recovered image...")
        fourier.decode(resultImage, energyLogKList).zip(energyLogKList)
            .forEach { (images, k) ->
                images.toNamedList().forEach { (image, channelName) ->
                    val filename = "fft_recovered_${channelName}_energyLog_${k}.png"
                    image.writePNG(File(outputDir, filename))
                }
            }
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
