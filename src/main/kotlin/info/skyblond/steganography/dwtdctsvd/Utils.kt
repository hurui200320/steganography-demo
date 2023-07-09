package info.skyblond.steganography.dwtdctsvd

import info.skyblond.steganography.toYUV
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import java.awt.Color
import java.awt.image.BufferedImage
import kotlin.math.ceil

data class Chunk(
    val data: Array<DoubleArray>
) {
    val size: Int
        get() = data.size

    val indices: IntRange
        get() = IntRange(0, size - 1)

    init {
        for (d in data) {
            require(d.size == size)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Chunk) return false

        return data.contentDeepEquals(other.data)
    }

    override fun hashCode(): Int {
        return data.contentDeepHashCode()
    }

    override fun toString(): String {
        return "Chunk(data=${data.contentDeepToString()})"
    }
}

fun Chunk.dct2d(): Chunk {
    val dct = FixedDCT.create(this.size)
    // result: [height, width]
    val rowTransformed = this.indices.map { y ->
        // feed row
        dct.dct(this.data[y])
    }

    // output shape is [width, height]
    val columnTransformed = this.indices.map { x ->
        // feed columns
        val columnData = DoubleArray(this.size) { y -> rowTransformed[y][x] }
        dct.dct(columnData)
    }

    // recover the structure back to [height, weight]
    return Chunk(Array(this.size) { y ->
        DoubleArray(this.size) { x -> columnTransformed[x][y] }
    })
}

fun Chunk.idct2d(): Chunk {
    val dct = FixedDCT.create(this.size)
    // result: [height, width]
    val rowTransformed = this.indices.map { y ->
        // feed row
        dct.idct(this.data[y])
    }

    // output shape is [width, height]
    val columnTransformed = this.indices.map { x ->
        // feed columns
        val columnData = DoubleArray(this.size) { y -> rowTransformed[y][x] }
        dct.idct(columnData)
    }

    // recover the structure back to [height, weight]
    return Chunk(Array(this.size) { y ->
        DoubleArray(this.size) { x -> columnTransformed[x][y] }
    })
}



/**
 * Split YUV DWT result into chunks.
 * */
fun SubRegion.chunked4x4(): Array<Array<Chunk>> =
    Array(ceil(this.height / 4.0).toInt()) { yIndex ->
        Array(ceil(this.width / 4.0).toInt()) { xIndex ->
            val x = xIndex * 4
            val y = yIndex * 4
            Chunk(Array(4) { yOffset ->
                DoubleArray(4) { xOffset ->
                    this[x + xOffset, y + yOffset]
                }
            })
        }
    }

fun Array<Array<Chunk>>.flatten4x4(width: Int, height: Int): SubRegion =
    SubRegion(Array(height) { y ->
        DoubleArray(width) { x ->
            this[y / 4][x / 4].data[y % 4][x % 4]
        }
    })
suspend fun Array<Array<Chunk>>.applyDCTSVD(): Array<Array<SingularValueDecomposition>> = coroutineScope {
    this@applyDCTSVD.map { row ->
        async {
            row.map {
                async {
                    SingularValueDecomposition(
                        Array2DRowRealMatrix(it.dct2d().data)
                    )
                }
            }.awaitAll().toTypedArray()
        }
    }.awaitAll().toTypedArray()
}

/**
 * Pad image length to even number. Convert to YUV and apply level 1 Haar DWT.
 *
 * Return (Y, U, V), each channel is (LL represented by [SubRegion], list of [Decomposition]).
 * The Y values are minus 128 to fit in range [-128, 127], which is required by DCT.
 * */
suspend fun decomposeImageYUVHaarDWT(sourceImage: BufferedImage): Triple<Pair<SubRegion, ArrayList<Decomposition>>, Pair<SubRegion, ArrayList<Decomposition>>, Pair<SubRegion, ArrayList<Decomposition>>> {
    val height = sourceImage.height.let { it + if (it % 2 != 0) 1 else 0 }
    val width = sourceImage.width.let { it + if (it % 2 != 0) 1 else 0 }
    // RGB -> YUV
    val yChannel = Array(height) { DoubleArray(width) }
    val uChannel = Array(height) { DoubleArray(width) }
    val vChannel = Array(height) { DoubleArray(width) }
    for (y in 0 until sourceImage.height) {
        for (x in 0 until sourceImage.width) {
            val (cy, cu, cv) = Color(sourceImage.getRGB(x, y)).toYUV()
            yChannel[y][x] = cy - 128
            uChannel[y][x] = cu
            vChannel[y][x] = cv
        }
    }
    // Haar DWT
    return Triple(
        dwt2d(Wavelet.haar, yChannel, 1),
        dwt2d(Wavelet.haar, uChannel, 1),
        dwt2d(Wavelet.haar, vChannel, 1)
    )
}
