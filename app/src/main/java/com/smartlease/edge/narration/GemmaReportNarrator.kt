package com.smartlease.edge.narration

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.deduction.FindingDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Writes a report section with an on-device Gemma model via Google's LiteRT-LM runtime.
 *
 * This replaces the earlier MediaPipe `tasks-genai` path. MediaPipe reads the `.task`
 * container only; Gemma 4 (and the Gemma 3n builds) ship as `.litertlm`, which MediaPipe
 * rejects at tokenizer-init with a SentencePiece parse error. LiteRT-LM
 * (`com.google.ai.edge.litertlm`) is Google's official runtime for the `.litertlm` container
 * and loads those weights directly, with a CPU/GPU/NPU backend choice.
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
 * without ever loading the LiteRT-LM classes.
 */
class GemmaReportNarrator private constructor(
    private val engine: Engine,
    private val modelName: String,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS
) : ReportNarrator {

    override suspend fun narrate(
        sectionTitle: String,
        findings: List<InspectionEntity>
    ): Narration {
        val fallback = TemplateReportNarrator.compose(findings)

        val raw = withContext(Dispatchers.Default) {
            runCatching {
                // A fresh conversation per section keeps each paragraph independent -- there
                // is no chat history to carry, and a stale KV cache would only pin memory.
                engine.createConversation(conversationConfig()).use { conversation ->
                    conversation.sendMessage(prompt(sectionTitle, findings)).toString()
                }
            }
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
     * Greedy decoding: two runs over the same findings should produce the same paragraph. A
     * report that reworded itself on every regeneration would make the findings digest look
     * like the only stable thing on the page, and would make "we regenerated it and it says
     * something else" a real conversation to have in front of a judge. `topK = 1` picks the
     * single most likely token every step, which is the LiteRT-LM equivalent of the old
     * MediaPipe `setMaxTopK(1)`.
     */
    private fun conversationConfig(): ConversationConfig =
        ConversationConfig(
            // topK = 1 alone is greedy, but LiteRT-LM has no defaults on this type, so the
            // remaining knobs are set to values that cannot reintroduce randomness: a zero
            // temperature and a fixed seed. topP is inert once topK is 1.
            samplerConfig = SamplerConfig(
                topK = 1,
                topP = 1.0,
                temperature = 0.0,
                seed = 0
            ),
            maxOutputToken = maxTokens
        )

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

    override suspend fun narrateDocumentVerification(
        findings: List<InspectionEntity>,
        leaseDosAndDonts: String
    ): Narration {
        val fallback = TemplateReportNarrator.composeDocumentVerification(findings, leaseDosAndDonts)

        val raw = withContext(Dispatchers.Default) {
            runCatching {
                engine.createConversation(conversationConfig()).use { conversation ->
                    conversation.sendMessage(
                        documentVerificationPrompt(findings, leaseDosAndDonts)
                    ).toString()
                }
            }
                .onFailure { Log.w(TAG, "Gemma document verification failed; using template", it) }
                .getOrNull()
        } ?: return Narration(fallback, NarrationSource.TEMPLATE, "generation failed ($modelName)")

        val text = raw.trim()
        return when (val verdict = NarrationAudit.check(text, findings)) {
            is NarrationAudit.Result.Accepted -> Narration(text, NarrationSource.GEMMA)
            is NarrationAudit.Result.Rejected -> {
                Log.w(TAG, "Gemma document verification rejected: ${verdict.reason}")
                Narration(fallback, NarrationSource.TEMPLATE, "rejected: ${verdict.reason}")
            }
        }
    }

    /**
     * Unlike [prompt], this crosses two documents -- the recorded findings and the lease's own
     * dos and don'ts -- so the rules repeat what [NarrationAudit] already enforces (no money,
     * no invented findings) and add the one specific to this section: no verdict on legal
     * compliance, only a plain comparison. The model has no authority to decide a dispute; it
     * can only point out where the two documents agree or disagree.
     */
    private fun documentVerificationPrompt(findings: List<InspectionEntity>, leaseDosAndDonts: String): String {
        val lines = if (findings.isEmpty()) {
            "(none recorded)"
        } else {
            findings.joinToString("\n") { "- ${it.label} [severity: ${it.severity}]" }
        }

        return """
            You are comparing a rental property inspection against that property's lease.

            Findings recorded by the inspection (${findings.size} total):
            $lines

            Lease dos and don'ts (extracted from the uploaded agreement):
            $leaseDosAndDonts

            Write a single plain paragraph, 2 to 4 sentences, noting where the findings above
            relate to the lease's dos and don'ts -- for example, a finding that matches
            something the lease says not to do, or findings that do not relate to any lease
            term.

            Rules:
            - Only reference findings from the list above and terms from the lease text above.
              Do not invent a finding, a lease term, or a location not present in either.
            - Do not estimate or mention any cost, price, or rupee amount.
            - Do not decide fault, liability, or legal compliance. Describe what relates to
              what; do not conclude who is right or who must pay.
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
         * Default token budget when the caller has no [ModelLoadBalancer] recommendation.
         * Small on purpose: the task is one paragraph, and a large window costs both memory
         * and the first-token latency a user waits through after tapping "Generate report".
         */
        const val DEFAULT_MAX_TOKENS = 640

        /**
         * Loads the model at [file]. Returns null and logs rather than throwing -- a phone
         * that cannot run the model must still produce reports, so failure here is an expected
         * state, not an error condition.
         *
         * Backend choice is GPU-first with a CPU fallback: the demo handset (iQOO 15, SM8850)
         * has an OpenCL driver and the GPU backend is markedly faster, but a device without one
         * would fail GPU init, and we would rather narrate slowly on CPU than fall back to the
         * template. Both are tried before giving up. NPU is not attempted here -- it needs a
         * per-SoC library bundle the app does not ship.
         *
         * @param maxTokens output token budget, normally [ModelLoadBalancer]'s recommendation
         * for the current RAM/thermal state -- constrained on a tight device, [DEFAULT_MAX_TOKENS]
         * otherwise.
         */
        fun tryCreate(context: Context, file: File, maxTokens: Int = DEFAULT_MAX_TOKENS): GemmaReportNarrator? {
            // Named pairs rather than `backend::class.simpleName`: reading the class name
            // reflectively pulls in kotlin-reflect at runtime, and the version LiteRT-LM drags
            // in is incompatible with the project's Kotlin, so touching Reflection here crashed
            // with NoClassDefFoundError before the model ever loaded. A plain label avoids it.
            val backends = listOf("GPU" to Backend.GPU(), "CPU" to Backend.CPU())
            for ((name, backend) in backends) {
                val engine = runCatching {
                    val config = EngineConfig(
                        modelPath = file.absolutePath,
                        backend = backend,
                        // A writable cache dir lets LiteRT-LM persist a compiled model, which
                        // cuts the second load noticeably. App-private, cleared with the app.
                        cacheDir = context.cacheDir.path
                    )
                    Engine(config).also { it.initialize() }
                }.onFailure {
                    Log.w(TAG, "LiteRT-LM $name init failed for ${file.name}", it)
                }.getOrNull()

                if (engine != null) {
                    Log.i(TAG, "Loaded ${file.name} on $name")
                    return GemmaReportNarrator(engine, file.name, maxTokens)
                }
            }
            Log.w(TAG, "Could not load Gemma model at ${file.absolutePath} on any backend")
            return null
        }
    }
}
