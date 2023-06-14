package info.skyblond.steganography.lsb

import info.skyblond.steganography.message.EncryptedMessageStream
import info.skyblond.steganography.message.PlainMessageStream
import info.skyblond.steganography.prng.SHA1PRNGStream
import info.skyblond.steganography.prng.VMPCStream
import org.junit.jupiter.api.Test
import java.awt.Color
import java.awt.image.BufferedImage
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.test.assertContentEquals

/**
 * Make sure LSB encodings are working properly.
 * */
class LSBTest {
    private val message = Random.nextBytes(199)
    private val seed = SecureRandom().generateSeed(20)
    private val key = ByteArray(32).apply { SecureRandom().nextBytes(this) }

    @Test
    fun testRGBRandomBit01LSB() {
        testLSBPlain(RGBRandomBit01LSB())
        testLSBEncrypted(RGBRandomBit01LSB())
    }

    @Test
    fun testRGBRandomBit0LSB() {
        testLSBPlain(RGBRandomBit0LSB())
        testLSBEncrypted(RGBRandomBit0LSB())
    }

    @Test
    fun testRGBFixedBit0LSB() {
        testLSBPlain(RGBFixedBit0LSB())
        testLSBEncrypted(RGBFixedBit0LSB())
    }

    private fun testLSBEncrypted(lsb: LSB) {
        val sourcePic = generateRandomSourcePic()
        val messageStream = EncryptedMessageStream(message, key)
        val messageSize = messageStream.payloadSize
        val nonceSize = messageStream.extraSize

        val encoded = lsb.encode(sourcePic, messageStream, VMPCStream(seed))
        val decoded = lsb.decode(encoded, VMPCStream(seed))

        val decodedMessage = EncryptedMessageStream.decode(
            decoded, key, nonceSize, messageSize
        ).first()
        assertContentEquals(message, decodedMessage)
    }

    private fun testLSBPlain(lsb: LSB) {
        val sourcePic = generateRandomSourcePic()
        val messageStream = PlainMessageStream(message)
        val messageSize = messageStream.payloadSize

        val encoded = lsb.encode(sourcePic, messageStream, SHA1PRNGStream(seed))
        val decoded = lsb.decode(encoded, SHA1PRNGStream(seed))

        val decodedMessage = decoded.readNBytes(messageSize)
        assertContentEquals(message, decodedMessage)
    }

    private fun generateRandomSourcePic(): BufferedImage {
        val random = SHA1PRNGStream(SecureRandom().generateSeed(20))
        val result = BufferedImage(768, 768, BufferedImage.TYPE_4BYTE_ABGR)
        for (y in 0 until result.height) {
            for (x in 0 until result.width) {
                val color = Color(random.read(), random.read(), random.read())
                result.setRGB(x, y, color.rgb)
            }
        }
        return result
    }
}
