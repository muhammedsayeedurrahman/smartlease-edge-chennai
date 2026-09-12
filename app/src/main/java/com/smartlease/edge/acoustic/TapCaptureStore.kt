package com.smartlease.edge.acoustic

import android.content.Context
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Writes training-data captures to disk as uncompressed WAV.
 *
 * One file per SPOT, not one file per tap, and that is the whole design.
 * `ml/acoustic/export_for_android.py:61` groups LeaveOneGroupOut by `os.path.basename(path)`
 * — the filename is the group. So:
 *
 *  - many taps in one file  -> one group, and sibling taps from the same spot correctly
 *    stay on the same side of every fold, which is what stops the model memorising a spot
 *    and scoring itself on a near-duplicate of what it trained on;
 *  - one tap per file       -> taps from the same physical spot land in *different* folds,
 *    which reintroduces exactly the leakage the grouping exists to prevent.
 *
 * So the capture UI records a continuous window per spot while the user strikes it several
 * times, and `segment_taps()` on the Python side splits the transients back out. The number
 * of distinct spots, not the number of taps, is what sets the fold count and therefore the
 * width of the confidence interval — see RECORDING_PROTOCOL.md.
 */
object TapCaptureStore {

    const val ROOT_DIR = "taps"
    val LABELS = listOf("solid", "hollow")

    fun labelDir(context: Context, label: String): File =
        File(File(context.filesDir, ROOT_DIR), label).apply { mkdirs() }

    /** Spot files recorded so far for [label]. */
    fun count(context: Context, label: String): Int =
        labelDir(context, label).listFiles { f -> f.extension == "wav" }?.size ?: 0

    /** Distinct spots across both labels — the figure that actually drives the CI width. */
    fun totalSpots(context: Context): Int = LABELS.sumOf { count(context, it) }

    /**
     * @param surface short slug the user picked, e.g. "tile_floor". Goes in the filename so
     *        the surface mix is auditable afterwards without opening the audio.
     * @return the written file
     */
    fun writeSpot(
        context: Context,
        label: String,
        surface: String,
        pcm: ShortArray,
        sampleRate: Int
    ): File {
        val safeSurface = surface.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .ifEmpty { "unspecified" }
        val name = "${label}_${safeSurface}_${System.currentTimeMillis()}.wav"
        val out = File(labelDir(context, label), name)
        FileOutputStream(out).use { writeWav(it, pcm, sampleRate) }
        return out
    }

    fun deleteLast(context: Context, label: String): File? {
        val last = labelDir(context, label)
            .listFiles { f -> f.extension == "wav" }
            ?.maxByOrNull { it.lastModified() } ?: return null
        return if (last.delete()) last else null
    }

    /** Zip the whole taps/ tree into filesDir/exports so it can leave via the share sheet. */
    fun exportZip(context: Context): File {
        val exports = File(context.filesDir, "exports").apply { mkdirs() }
        val zip = File(exports, "taps-${System.currentTimeMillis()}.zip")
        val root = File(context.filesDir, ROOT_DIR)
        ZipOutputStream(FileOutputStream(zip).buffered()).use { zos ->
            root.walkTopDown().filter { it.isFile }.forEach { f ->
                zos.putNextEntry(ZipEntry(f.toRelativeString(root).replace(File.separatorChar, '/')))
                f.inputStream().use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
        return zip
    }

    /**
     * Canonical 44-byte RIFF/WAVE header + little-endian PCM16. Written by hand rather than
     * through MediaRecorder on purpose: MediaRecorder would apply a codec, and the point of
     * this whole screen is that the training audio and the inference audio are the same bytes.
     */
    private fun writeWav(out: FileOutputStream, pcm: ShortArray, sampleRate: Int) {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val dataBytes = pcm.size * 2
        val header = java.nio.ByteBuffer.allocate(44).order(java.nio.ByteOrder.LITTLE_ENDIAN)

        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(36 + dataBytes)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                                   // PCM fmt chunk size
        header.putShort(1)                                  // audio format = PCM
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())  // block align
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataBytes)
        out.write(header.array())

        val body = java.nio.ByteBuffer.allocate(dataBytes).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for (s in pcm) body.putShort(s)
        out.write(body.array())
    }
}
