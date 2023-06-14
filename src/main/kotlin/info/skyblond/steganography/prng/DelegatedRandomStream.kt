package info.skyblond.steganography.prng

abstract class DelegatedRandomStream(
    protected val buffer: ByteArray
) : RandomStream {
    private var bufferPosition: Int = buffer.size

    protected abstract fun reloadBuffer()
    final override fun read(): Int {
        if (bufferPosition >= buffer.size) {
            reloadBuffer()
            bufferPosition = 0
        }
        return buffer[bufferPosition++].toUByte().toInt()
    }
}
