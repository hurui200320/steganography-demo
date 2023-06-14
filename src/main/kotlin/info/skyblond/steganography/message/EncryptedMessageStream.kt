package info.skyblond.steganography.message

import info.skyblond.steganography.toByteArray
import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMSIVBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.security.SecureRandom

/**
 * Encrypt the [rawMessage] using AES-256-GCM-SIV.
 * The nonce is 12 bytes and the mac is 128 bit.
 * The [key] size must be 32 bytes (256bits).
 *
 * In each loop/repeat, the content will change to prevent pattern analysis.
 * */
class EncryptedMessageStream(
    private val rawMessage: ByteArray,
    private val key: ByteArray,
) : DelegatedMessageStream() {

    // Each loop has different count, thus the result is different
    private var loopCount = 0
    private var delegatedStream: InputStream

    override val extraSize: Int
    override val payloadSize: Int

    init {
        require(key.size == 32)
        val (nonce, payload) = encryptMessage(key, loopCount.toByteArray() + rawMessage)
        delegatedStream = ByteArrayInputStream(nonce + payload)
        extraSize = nonce.size
        payloadSize = payload.size
    }

    override fun readByte(): Int = delegatedStream.read()

    override fun resetStream() {
        loopCount++
        val (nonce, payload) = encryptMessage(key, loopCount.toByteArray() + rawMessage)
        check(nonce.size == extraSize)
        check(payload.size == payloadSize)
        delegatedStream = ByteArrayInputStream(nonce + payload)
    }

    private fun encryptMessage(key: ByteArray, message: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = GCMSIVBlockCipher(AESEngine())
        // mac size is ranged from 32~128 bits, for message integrity check
        // nonce can be any size, but must not be reused
        // the SIV will have more security when nonce is reused,
        // but the max message it can handle is 2^31 bytes
        val nonce = ByteArray(12)
        SecureRandom().nextBytes(nonce)
        cipher.init(true, AEADParameters(KeyParameter(key), 128, nonce))
        val result = ByteArray(cipher.getOutputSize(message.size))
        val resultSize = cipher.processBytes(message, 0, message.size, result, 0)
        cipher.doFinal(result, resultSize)
        return nonce to result
    }

    companion object {
        fun decode(
            inputStream: InputStream,
            key: ByteArray,
            nonceSize: Int, payloadSize: Int
        ): Sequence<ByteArray> = sequence {
            while (true) {
                // read nonce
                val nonce = inputStream.readNBytes(nonceSize)
                if (nonce.size != nonceSize) break
                val payload = inputStream.readNBytes(payloadSize)
                if (payload.size != payloadSize) break
                try {
                    // skip the 4 bytes loop counter
                    yield(decryptMessage(key, payload, nonce).let { it.copyOfRange(4, it.size) })
                } catch (t: Throwable) {
                    // bad block, skip
                }
            }
        }

        private fun decryptMessage(key: ByteArray, encryptedMessage: ByteArray, nonce: ByteArray): ByteArray {
            val cipher = GCMSIVBlockCipher(AESEngine())
            cipher.init(false, AEADParameters(KeyParameter(key), 128, nonce))
            val result = ByteArray(cipher.getOutputSize(encryptedMessage.size))
            val resultSize = cipher.processBytes(encryptedMessage, 0, encryptedMessage.size, result, 0)
            cipher.doFinal(result, resultSize)
            return result
        }
    }
}
