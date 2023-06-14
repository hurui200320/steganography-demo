package info.skyblond.steganography

import info.skyblond.steganography.prng.RandomStream
import info.skyblond.steganography.prng.SHA1PRNGStream
import info.skyblond.steganography.prng.VMPCStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.security.SecureRandom

/**
 * Turn [RandomStream]'s output into [BufferedImage].
 * */
object PRNGDemo {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        val seed = SecureRandom().generateSeed(20)
        val streams = listOf(
            "SHA1PRNG" to { SHA1PRNGStream(seed) },
            "VMPC" to { VMPCStream(seed) },
        )
        val generators = listOf(
            "binary" to this@PRNGDemo::binaryGenerator,
            "3cBinary" to this@PRNGDemo::multiChannelBinaryGenerator,
            "rgb" to this@PRNGDemo::trueColorGenerator
        )
        val outputDir = File("./output/prng_test").apply { mkdirs() }


        delay(5000) // make sure folder created
        streams.forEach { (streamName, randomStream) ->
            generators.forEach { (generatorName, generator) ->
                launch {
                    foo(randomStream(), generator, File(outputDir, "${streamName}_${generatorName}.png"))
                }
            }
        }
    }

    /**
     * When applying LSB to a single channel.
     * */
    private fun binaryGenerator(randomStream: RandomStream): Color =
        if (randomStream.read() % 2 == 0) Color.WHITE else Color.BLACK

    /**
     * When applying LSB to multiple channels randomly.
     * */
    private fun multiChannelBinaryGenerator(randomStream: RandomStream): Color =
        when (randomStream.read() % 4) {
            0 -> Color.BLUE
            1 -> Color.GREEN
            2 -> Color.RED
            else -> Color.BLACK
        }

    /**
     * Just a show-off.
     * */
    private fun trueColorGenerator(randomStream: RandomStream): Color =
        Color(randomStream.read(), randomStream.read(), randomStream.read())

    private suspend fun foo(
        randomStream: RandomStream, colorGenerator: (RandomStream) -> Color, output: File
    ) {
        val result = BufferedImage(768, 768, BufferedImage.TYPE_4BYTE_ABGR)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val color = colorGenerator(randomStream)
                result.setRGB(x, y, color.rgb)
            }
        }
        result.writePNG(output)
    }
}
