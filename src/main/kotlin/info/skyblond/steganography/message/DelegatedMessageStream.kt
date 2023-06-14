package info.skyblond.steganography.message

/**
 * Delegate the logic to a [java.io.InputStream].
 * */
abstract class DelegatedMessageStream : MessageStream {

    /**
     * The [java.io.InputStream.read]. Return -1 means end of stream.
     * */
    protected abstract fun readByte(): Int

    /**
     * Reset the internal stream
     * */
    protected abstract fun resetStream()

    private var currentByte: Int = 0x00
    private var currentPosition: Int = Byte.SIZE_BITS

    private fun readNextByte() {
        currentByte = readByte()
        if (currentByte == -1) {
            resetStream()
            currentByte = readByte()
        }
    }

    final override fun nextBit(): Boolean {
        if (currentPosition >= Byte.SIZE_BITS) {
            readNextByte()
            currentPosition = 0
        }
        return (currentByte shr (currentPosition++)) and 0x01 != 0
    }
}
