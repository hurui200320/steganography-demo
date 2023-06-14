package info.skyblond.steganography.message

import java.io.ByteArrayInputStream
import java.io.InputStream

class PlainMessageStream(
    message: ByteArray
) : DelegatedMessageStream() {
    override val payloadSize: Int = message.size
    override val extraSize: Int = 0
    private var delegatedStream = ByteArrayInputStream(message)
    override fun readByte(): Int = delegatedStream.read()

    override fun resetStream() = delegatedStream.reset()

    companion object {
        fun decode(
            inputStream: InputStream,
            messageSize: Int
        ): Sequence<ByteArray> = sequence {
            while (true) {
                // read nonce
                val message = inputStream.readNBytes(messageSize)
                if (message.size != messageSize) break
                yield(message)
            }
        }
    }
}
