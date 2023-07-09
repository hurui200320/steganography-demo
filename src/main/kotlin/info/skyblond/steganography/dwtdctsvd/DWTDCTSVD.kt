package info.skyblond.steganography.dwtdctsvd

import info.skyblond.steganography.yuvToRGB
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.apache.commons.math3.linear.RealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition
import java.awt.image.BufferedImage

class DWTDCTSVD(
    private val sourceImage: BufferedImage
) {
    private val yDecompositions: List<Decomposition>
    private val uDecompositions: List<Decomposition>
    private val vDecompositions: List<Decomposition>

    private val ySVD: Array<Array<SingularValueDecomposition>>
    private val uSVD: Array<Array<SingularValueDecomposition>>
    private val vSVD: Array<Array<SingularValueDecomposition>>

    private val llWidth: Int
    private val llHeight: Int

    init {
        // RGB -> YUV -> Haar DWT
        val (yDWT, uDWT, vDWT) = runBlocking {
            decomposeImageYUVHaarDWT(sourceImage)
        }
        val yLL = yDWT.let { yDecompositions = it.second; it.first }
        val uLL = uDWT.let { uDecompositions = it.second; it.first }
        val vLL = vDWT.let { vDecompositions = it.second; it.first }
        // yuv should share the same size
        llWidth = yLL.width
        llHeight = yLL.height
        // LL -> chunk to 4x4
        val yChunks = yLL.chunked4x4()
        val uChunks = uLL.chunked4x4()
        val vChunks = vLL.chunked4x4()
        // Chunk -> DCT -> SVD
        ySVD = runBlocking { yChunks.applyDCTSVD() }
        uSVD = runBlocking { uChunks.applyDCTSVD() }
        vSVD = runBlocking { vChunks.applyDCTSVD() }
    }

    private suspend fun applyISVDIDCT(
        svdChannel: Array<Array<SingularValueDecomposition>>,
        modifiedS: Array<Array<RealMatrix>>
    ): Array<Array<Chunk>> = coroutineScope {
        svdChannel.mapIndexed { y, row ->
            val rowS = modifiedS[y]
            async {
                row.mapIndexed { x, svd ->
                    async {
                        val recovered = svd.u.multiply(rowS[x]).multiply(svd.vt)
                        // copy to chunk and apply idct
                        Chunk(Array(4) { y -> DoubleArray(4) { x -> recovered.getEntry(y, x) } })
                            .idct2d()
                    }
                }.awaitAll().toTypedArray()
            }
        }.awaitAll().toTypedArray()
    }

    private fun copyMatrixS(): Triple<Array<Array<RealMatrix>>, Array<Array<RealMatrix>>, Array<Array<RealMatrix>>> {
        val yS = ySVD.let {
            Array(it.size) { y -> Array(it[y].size) { x -> it[y][x].s.copy() } }
        }
        val uS = uSVD.let {
            Array(it.size) { y -> Array(it[y].size) { x -> it[y][x].s.copy() } }
        }
        val vS = vSVD.let {
            Array(it.size) { y -> Array(it[y].size) { x -> it[y][x].s.copy() } }
        }
        return Triple(yS, uS, vS)
    }

    suspend fun steganography(action: (Array<Array<RealMatrix>>, Array<Array<RealMatrix>>, Array<Array<RealMatrix>>) -> Unit): BufferedImage =
        coroutineScope {
            // each time we create a copy of SVD matrix, thus we can reuse
            val (yS, uS, vS) = copyMatrixS()
            action(yS, uS, vS)
            // then iSVD -> iDCT -> Chunk
            val (yLLChanged, uLLChanged, vLLChanged) = listOf(
                async { applyISVDIDCT(ySVD, yS).flatten4x4(llWidth, llHeight) },
                async { applyISVDIDCT(uSVD, uS).flatten4x4(llWidth, llHeight) },
                async { applyISVDIDCT(vSVD, vS).flatten4x4(llWidth, llHeight) },
            ).awaitAll()
            // LL -> YUV Channel
            val yIDWT = idwt2d(yLLChanged, yDecompositions, Wavelet.haar)
            val uIDWT = idwt2d(uLLChanged, uDecompositions, Wavelet.haar)
            val vIDWT = idwt2d(vLLChanged, vDecompositions, Wavelet.haar)
            val recoveredImage = BufferedImage(sourceImage.width, sourceImage.height, sourceImage.type)
            for (y in 0 until recoveredImage.height) {
                for (x in 0 until recoveredImage.width) {
                    val cy = yIDWT[y][x] + 128
                    val cu = uIDWT[y][x]
                    val cv = vIDWT[y][x]

                    val color = yuvToRGB(cy, cu, cv)
                    recoveredImage.setRGB(x, y, color.rgb)
                }
            }
            recoveredImage
        }
}
