package info.skyblond.steganography.lsb

import info.skyblond.steganography.message.MessageStream
import info.skyblond.steganography.prng.RandomStream

/**
 * This LSB encoding chooses one of the RGB channels and encodes 1bit info per pixel (bit0).
 * There has a 25% chance for this LSB encoding to skip a pixel.
 * For the rest of 75%, each channel has equal chances to be selected.
 * */
class RGBRandomBit0LSB : LinearLSB() {
    override fun encodeRandom(
        x: Int, y: Int, originalARGB: Int,
        randomStream: RandomStream, messageStream: MessageStream
    ): Int {
        // 0 -> Blue (7-0)
        // 1 -> Green (15-8)
        // 2 -> Red (23-16)
        val channel = randomStream.read() % 4
        // skip if channel is 3
        if (channel == 3) return originalARGB
        return setBit(originalARGB, 0x01 shl channel * 8, messageStream.nextBit())
    }

    override fun decodeRandom(x: Int, y: Int, pixelARGB: Int, randomStream: RandomStream): List<Boolean> {
        val channel = randomStream.read() % 4
        // skip if channel is 3
        if (channel == 3) return emptyList()
        return listOf(pixelARGB and (0x01 shl channel * 8) != 0)
    }
}
