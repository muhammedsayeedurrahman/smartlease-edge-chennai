package com.smartlease.edge.domain.llm

import android.net.Uri
import android.util.Log
import android.content.Context
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.itextpdf.text.pdf.PdfReader
import com.itextpdf.text.pdf.parser.PdfTextExtractor
import com.smartlease.edge.narration.GemmaModelLocator
import com.smartlease.edge.narration.NarrationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [text] plus an honest account of whether an on-device model wrote it, the same distinction
 * every [com.smartlease.edge.report.ReportSection] already carries -- a report that prints this
 * text as a "Dos & Don'ts" section must be able to credit it correctly rather than implying
 * every lease summary came from the model.
 */
data class DocumentSummary(val text: String, val source: NarrationSource)

/**
 * Extracts the dos and don'ts from an uploaded rental agreement PDF using the same on-device
 * Gemma model, via the same LiteRT-LM runtime and [GemmaModelLocator] lookup, that
 * [com.smartlease.edge.narration.GemmaReportNarrator] uses for report narration.
 *
 * This previously loaded a `.task` file through the deprecated MediaPipe `LlmInference` API
 * from `assets/llm/`, a path that ships nothing (see [GemmaModelLocator]'s own doc comment on
 * why the model is never bundled in the APK) and so always failed silently, returning an error
 * string instead of a summary. Routing through the same locator and engine the narrator uses
 * makes this the second real caller of infrastructure that already works and is already tested,
 * rather than a second, independently-broken path to the same weights.
 *
 * A fresh [Engine] is created and closed per call rather than shared with the narrator: the two
 * run at different times (document upload vs. report generation) and neither needs the other's
 * conversation state, so independence avoids a shared-engine lifecycle bug where closing one
 * for a report mid-upload would kill the other's in-flight generation.
 */
class DocumentAnalyzer(private val context: Context) {

    private val TAG = "DocumentAnalyzer"

    suspend fun extractDosAndDonts(pdfUriString: String): DocumentSummary = withContext(Dispatchers.IO) {
        val extractedText = extractPdfText(pdfUriString)

        val location = GemmaModelLocator.locate(context)
        val modelFile = when (location) {
            is GemmaModelLocator.Location.Missing -> {
                Log.i(TAG, "No Gemma model found in ${location.searched}")
                return@withContext noModelFallback(extractedText, "no on-device model found")
            }
            is GemmaModelLocator.Location.Found -> location.file
        }

        val engine = loadEngine(modelFile)
            ?: return@withContext noModelFallback(extractedText, "${modelFile.name} failed to load")

        try {
            val prompt = """
                Extract the dos and don'ts from this rental agreement.
                Output exactly one summary paragraph, followed by a bulleted list.

                Agreement Text:
                $extractedText
            """.trimIndent()

            val config = ConversationConfig(
                samplerConfig = SamplerConfig(topK = 1, topP = 1.0, temperature = 0.0, seed = 0),
                maxOutputToken = 512
            )
            val response = engine.createConversation(config).use { conversation ->
                conversation.sendMessage(prompt).toString()
            }

            val trimmed = response.trim()
            if (trimmed.isBlank()) {
                noModelFallback(extractedText, "model returned no output")
            } else {
                DocumentSummary(trimmed, NarrationSource.GEMMA)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Gemma generation failed", e)
            noModelFallback(extractedText, e.message ?: e.javaClass.simpleName)
        } finally {
            runCatching { engine.close() }
        }
    }

    /** Never an error string standing in for prose: the raw lease text is always the fallback. */
    private fun noModelFallback(extractedText: String, reason: String): DocumentSummary =
        DocumentSummary(
            "On-device AI summary unavailable ($reason). Extracted lease text:\n\n$extractedText",
            NarrationSource.TEMPLATE
        )

    private fun loadEngine(modelFile: java.io.File): Engine? {
        for (backend in listOf(Backend.GPU(), Backend.CPU())) {
            val engine = runCatching {
                Engine(
                    EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = backend,
                        cacheDir = context.cacheDir.path
                    )
                ).also { it.initialize() }
            }.onFailure {
                Log.w(TAG, "LiteRT-LM init failed for ${modelFile.name}", it)
            }.getOrNull()
            if (engine != null) return engine
        }
        return null
    }

    private fun extractPdfText(pdfUriString: String): String {
        var extractedText = "No text could be extracted from this document."
        try {
            val uri = Uri.parse(pdfUriString)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val reader = PdfReader(inputStream)
                val builder = StringBuilder()
                for (i in 1..reader.numberOfPages) {
                    builder.append(PdfTextExtractor.getTextFromPage(reader, i))
                }
                extractedText = builder.toString()
                reader.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read PDF: ${e.message}")
        }
        return extractedText
    }
}
