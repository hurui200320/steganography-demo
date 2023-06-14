package info.skyblond.steganography

import info.skyblond.steganography.lsb.RGBFixedBit0LSB
import info.skyblond.steganography.lsb.RGBRandomBit01LSB
import info.skyblond.steganography.lsb.RGBRandomBit0LSB
import info.skyblond.steganography.message.EncryptedMessageStream
import info.skyblond.steganography.message.PlainMessageStream
import info.skyblond.steganography.prng.VMPCStream
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

object LSBDemo {
    //    private val seed = SecureRandom().generateSeed(20)
    private val seed = "1fee6296ec123747413e2d114f2554737144bc56".toHexBytes()

    //    private val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }
    private val key = "5bb73f88b1d68b1192a0af66df2cfa6ea8b50e482d58f1dc7d232ba3ae4a7440".toHexBytes()

    private val message = "This is a test message. 这是一个测试消息".encodeToByteArray()

    // write png file with high compression rate will cost a lot of CPU usage
    // use channels like a message queue to schedule the writing
    private val imageWriteChannel = Channel<Pair<BufferedImage, File>>(128)
    private val scope = CoroutineScope(Job() + Dispatchers.Default)
    private val writers = (0 until Runtime.getRuntime().availableProcessors() - 1).map {
        scope.async {
            for ((image, file) in imageWriteChannel) {
                image.writePNG(file, 0.5)
            }
        }
    }

    init {
        println("Using seed: ${seed.toHexString()}")
        println("Using key: ${key.toHexString()}")
        println("Message size: ${message.size}")
    }

    private val lsbList = listOf(
        "fixed0lsb" to RGBFixedBit0LSB(),
        "random0lsb" to RGBRandomBit0LSB(),
        "random01lsb" to RGBRandomBit01LSB()
    )

    // use vmpc for better performance
    // in the PRNGDemo, SHA1PRNG and vmpc both give a decent randomness
    private val randomStreamList = listOf(
        "vmpc" to { VMPCStream(seed) },
    )
    private val messageStreamList = listOf(
        "plain" to { PlainMessageStream(message) },
        "encrypted" to { EncryptedMessageStream(message, key) }
    )

    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        File("./pic").listFiles()!!
            .filter { it.isFile && it.extension in listOf("jpg", "png") }
            .forEach {
                val md5 = it.path.md5()
                println("Found file: ${it.path}")
                val image = ImageIO.read(it)
                val outputDir = File("./output/lsb_demo/$md5").apply { mkdirs() }
                println("Output dir: ${outputDir.path}")
                lsbList.indices.forEach { lsbIndex ->
                    randomStreamList.indices.forEach { randomIndex ->
                        messageStreamList.indices.forEach { messageIndex ->
                            foo(image, outputDir, lsbIndex, randomIndex, messageIndex)
                        }
                    }
                }
            }
        imageWriteChannel.close()
        println("Waiting for image write...")
        writers.awaitAll()
        scope.cancel()
        println("Done. Exit...")
    }


    private suspend fun foo(
        image: BufferedImage, outputDir: File,
        lsbIndex: Int, randomIndex: Int, messageIndex: Int
    ): Unit = coroutineScope {
        val (lsbName, lsb) = lsbList[lsbIndex]
        val (randomName, random) = randomStreamList[randomIndex]
        val (messageName, message) = messageStreamList[messageIndex]
        println("$lsbName $randomName $messageName")
        // apply lsb steganography
        val result = lsb.encode(image, message(), random())

        launch { analyzeAsync("ori", image, outputDir) }
        launch { analyzeAsync("${lsbName}_${randomName}_${messageName}", result, outputDir) }
        imageWriteChannel.send(image to File(outputDir, "ori_copy.png"))
        imageWriteChannel.send(result to File(outputDir, "${lsbName}_${randomName}_${messageName}_result.png"))
    }

    /**
     * Apply all methods to a give image and save the result to file.
     * */
    private suspend fun analyzeAsync(
        name: String, image: BufferedImage, outputDir: File
    ) {
        repeat(3) {
            imageWriteChannel.send(
                ImageAnalyzer.randomColorMap(image) to File(
                    outputDir,
                    "${name}_random_color_map_${it + 1}.png"
                )
            )
        }
        ImageAnalyzer.bitPlaneAnalyze(image).mapIndexed { i, bufferedImage ->
            val fileName = when (i) {
                in 0 until 8 -> "${name}_blue_plane_${i}.png"
                in 8 until 16 -> "${name}_green_plane_${i - 8}.png"
                in 16 until 24 -> "${name}_red_plane_${i - 16}.png"
                else -> error("Unknown bit $i")
            }
            imageWriteChannel.send(bufferedImage to File(outputDir, fileName))
        }
    }
}
