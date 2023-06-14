package info.skyblond.steganography.prng

/**
 * A cryptographically secure pseudorandom source
 * */
interface RandomStream {
    /**
     * Return a random byte, ranged from 0 to 255.
     * */
    fun read(): Int
}
