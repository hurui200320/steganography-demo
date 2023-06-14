package info.skyblond.steganography

import info.skyblond.steganography.message.EncryptedMessageStream
import info.skyblond.steganography.message.MessageStream
import info.skyblond.steganography.message.PlainMessageStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import java.security.SecureRandom

/**
 * Turn [MessageStreamDemo]'s output into [BufferedImage].
 * */
object MessageStreamDemo {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking(Dispatchers.Default) {
        val random = SecureRandom()
        val message = ByteArray(515)
        random.nextBytes(message)
        val streams = listOf(
            "PLAIN" to { PlainMessageStream(message) },
            "AES256" to { EncryptedMessageStream(message, random.generateSeed(32)) },
        )
        val outputDir = File("./output/msgs_test").apply { mkdirs() }

        delay(5000) // make sure folder created
        streams.forEach { (streamName, messageStream) ->
            launch {
                foo(messageStream(), File(outputDir, "${streamName}.png"))
            }
        }
    }

    private suspend fun foo(
        messageStream: MessageStream, output: File
    ) {
        val result = BufferedImage(768, 768, BufferedImage.TYPE_4BYTE_ABGR)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val color = if (messageStream.nextBit()) Color.WHITE else Color.BLACK
                result.setRGB(x, y, color.rgb)
            }
        }
        result.writePNG(output)
    }
}
