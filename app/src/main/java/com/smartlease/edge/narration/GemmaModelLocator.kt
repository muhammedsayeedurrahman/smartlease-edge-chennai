package com.smartlease.edge.narration

import android.content.Context
import java.io.File

/**
 * Finds the on-device Gemma weights, or reports plainly that they are not there.
 *
 * The model is not in the APK and will not be. The smallest usable Gemma 3 build is around
 * half a gigabyte and the 3n E2B/E4B builds run to several; shipping one inside the app would
 * put the install well past what a hackathon judge will wait for, and Google's Gemma terms
 * govern redistribution besides. So it is pushed to the device separately and looked up here.
 *
 * Lookup is ordered from most to least specific, and every candidate is a path the app can
 * read without a storage permission:
 *
 *  1. `filesDir/llm/` -- where a future in-app download would land.
 *  2. `getExternalFilesDir("llm")` -- app-private external storage, which `adb push` can
 *     write to directly on a userdebug or production build without root. This is the one the
 *     setup instructions use.
 *
 * `/data/local/tmp` is deliberately NOT searched even though it is where adb push is easiest.
 * It is world-readable scratch space; treating it as a model source would mean any process on
 * the device could swap the weights that write sentences onto a legal document.
 */
object GemmaModelLocator {

    /** Subdirectory used under both roots. */
    const val DIRECTORY = "llm"

    /**
     * Model container extensions the app can load. `.litertlm` is the LiteRT-LM container that
     * [GemmaReportNarrator] now loads (Gemma 4 / Gemma 3n); `.task` is the older MediaPipe
     * container. Both are accepted so a device set up with either build is found.
     */
    private val EXTENSIONS = listOf(".litertlm", ".task", ".bin")

    sealed interface Location {
        data class Found(val file: File) : Location
        data class Missing(val searched: List<String>) : Location
    }

    fun locate(context: Context): Location {
        val roots = listOfNotNull(
            File(context.filesDir, DIRECTORY),
            context.getExternalFilesDir(DIRECTORY)
        )

        roots.forEach { root ->
            val match = root.takeIf { it.isDirectory }
                ?.listFiles()
                ?.filter { it.isFile && EXTENSIONS.any { ext -> it.name.endsWith(ext, ignoreCase = true) } }
                // Deterministic pick when someone has pushed more than one build: largest
                // file wins, because among Gemma quantisations the larger is the better one.
                // Arbitrary-but-stable beats listFiles() order, which is filesystem-dependent
                // and would silently change which model narrates between two runs.
                ?.maxByOrNull { it.length() }
            if (match != null) return Location.Found(match)
        }

        return Location.Missing(roots.map { it.absolutePath })
    }
}
