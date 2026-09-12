package com.smartlease.edge.narration

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.deduction.FindingDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes a report section with an on-device Gemma model via MediaPipe's LLM Inference task.
 *
 * Everything here is local: the weights are a file on this phone, inference runs on this
 * phone's CPU/GPU, and no text is sent anywhere. That matters beyond privacy theatre -- an
 * inspection happens in someone's flat, often in a basement or a stairwell, and a narrator
 * that needed a server would fail exactly where the app is used.
 *
 * Three rules this class follows, all of which exist because the output lands on a document
 * that prices someone's deposit:
 *
 *  1. **It never fabricates a fallback.** If the model is missing, fails to load, times out,
 *     or writes something [NarrationAudit] rejects, this returns [TemplateReportNarrator]'s
 *     text tagged [NarrationSource.TEMPLATE] -- never Gemma-tagged text, and never an error
 *     string printed where prose should be.
 *  2. **It is given the findings, not the room.** The prompt contains only the structured
 *     findings for the section. The model is not asked to infer, estimate, price, or advise.
 *  3. **Its output is audited, not trusted.** See [NarrationAudit].
 *
 * Construct through [ReportNarratorFactory], which handles the "no model on this device" case
 * without ever loading the MediaPipe classes.
 */
class GemmaReportNarrator private constructor(
    private val engine: LlmInference,
    private val modelName: String,
    private val maxTokens: Int = MAX_TOKENS
) : ReportNarrator {

    override suspend fun narrate(
        sectionTitle: String,
        findings: List<InspectionEntity>
    ): Narration {
        val fallback = TemplateReportNarrator.compose(findings)

        val raw = withContext(Dispatchers.Default) {
            runCatching { engine.generateResponse(prompt(sectionTitle, findings)) }
                .onFailure { Log.w(TAG, "Gemma generation failed; using template", it) }
                .getOrNull()
        } ?: return Narration(fallback, NarrationSource.TEMPLATE, "generation failed ($modelName)")

        val text = raw.trim()
        return when (val verdict = NarrationAudit.check(text, findings)) {
            is NarrationAudit.Result.Accepted -> Narration(text, NarrationSource.GEMMA)
            is NarrationAudit.Result.Rejected -> {
                Log.w(TAG, "Gemma output rejected: ${verdict.reason}")
                Narration(fallback, NarrationSource.TEMPLATE, "rejected: ${verdict.reason}")
            }
        }
    }

    /**
     * The findings, rendered as a list, with instructions that close off the ways a summary of
     * an inspection can become a claim about one. The model is told what it may not do in the
     * same breath as what it should, because a prompt that only asks for "a professional
     * summary" reliably produces recommendations, cost estimates, and blame.
     */
    private fun prompt(sectionTitle: String, findings: List<InspectionEntity>): String {
        val lines = findings.joinToString("\n") { finding ->
            val detail = runCatching { FindingDetail.fromJson(finding.detailJson) }.getOrNull()
            val extra = (detail as? FindingDetail.VisualDefect)?.let {
                " (%.2f sq ft, %d%% confidence)".format(it.areaSqFt, (it.confidence * 100).toInt())
            }.orEmpty()
            "- ${finding.label}$extra [severity: ${finding.severity}]"
        }

        return """
            You are writing one paragraph of a rental property inspection report.

            Section: $sectionTitle
            Findings recorded by the inspection (${findings.size} total):
            $lines

            Write a single plain paragraph, 2 to 4 sentences, summarising these findings for a
            landlord and tenant to read together.

            Rules:
            - Describe only the findings listed above. Do not add any finding, location, cause,
              or observation that is not in the list.
            - Do not estimate or mention any cost, price, or rupee amount.
            - Do not recommend repairs, assign blame, or say who should pay.
            - If you state a number of findings, it must be exactly ${findings.size}.
            - Write prose only. No headings, no bullet points, no markdown.
        """.trimIndent()
    }

    override fun close() {
        runCatching { engine.close() }
    }

    companion object {
        private const val TAG = "GemmaNarrator"

        /**
         * Token budget. Small on purpose: the task is one paragraph, and a large window costs
         * both memory and the first-token latency a user waits through after tapping
         * "Generate report".
         */
        private const val MAX_TOKENS = 640

        /**
         * Loads the model at [file]. Returns null and logs rather than throwing -- a phone
         * that cannot run the model must still produce reports, so failure here is an expected
         * state, not an error condition.
         */
        /**
         * @param maxTokens overrides [MAX_TOKENS] when the load balancer decides a smaller
         *   budget is needed for a large model or a low-memory device.
         */
        fun tryCreate(
            context: Context,
            file: File,
            maxTokens: Int = MAX_TOKENS
        ): GemmaReportNarrator? = runCatching {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(maxTokens)
                // Greedy decoding: two runs over the same findings should produce the same
                // paragraph. A report that reworded itself on every regeneration would make
                // the findings digest look like the only stable thing on the page, and would
                // make "we regenerated it and it says something else" a real conversation.
                .setMaxTopK(1)
                .build()
            GemmaReportNarrator(
                LlmInference.createFromOptions(context, options), file.name, maxTokens
            )
        }.onFailure {
            Log.w(TAG, "Could not load Gemma model at ${file.absolutePath}", it)
        }.getOrNull()
    }
}
