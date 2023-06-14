package info.skyblond.steganography.prng

/**
 * A placeholder random stream.
 * */
object NopStream : RandomStream {
    override fun read(): Int = 0
}
