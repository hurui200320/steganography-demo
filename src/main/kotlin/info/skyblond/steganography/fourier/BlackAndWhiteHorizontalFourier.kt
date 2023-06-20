package info.skyblond.steganography.fourier

import java.awt.image.BufferedImage

/**
 * Encode the data horizontally, aka using full width and half of the height.
 * */
class BlackAndWhiteHorizontalFourier(targetAmp: Double) : BlackAndWhiteHalfFourier(targetAmp) {
    override fun checkInputSize(width: Int, height: Int, mask: BufferedImage) {
        require(mask.height < height / 2) { "Mask too big" }
    }

    override fun calculateBox(width: Int, height: Int, mask: BufferedImage): Pair<Pair<Int, Int>, Pair<Int, Int>> {
        // top left
        val x0 = width / 2 - mask.width / 2
        val y0 = height / 2 - mask.height
        // bottom right
        val x1 = width / 2 + mask.width / 2
        val y1 = height / 2 + mask.height
        return (x0 to y0) to (x1 to y1)
    }
}
