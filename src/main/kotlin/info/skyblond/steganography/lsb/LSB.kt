package info.skyblond.steganography.lsb

import info.skyblond.steganography.message.MessageStream
import info.skyblond.steganography.prng.RandomStream
import java.awt.image.BufferedImage
import java.io.InputStream

/**
 * The interface for LSB steganography.
 * */
interface LSB {
    fun encode(
        image: BufferedImage, messageStream: MessageStream, randomStream: RandomStream
    ): BufferedImage

    fun decode(image: BufferedImage, randomStream: RandomStream): InputStream
}
