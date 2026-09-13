package com.smartlease.edge.narration

import android.content.Context
import android.util.Log
import com.smartlease.edge.data.InspectionEntity
import com.smartlease.edge.deduction.FindingDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.lang.reflect.Method

/**
 * On-device report narrator driven by Gemma 4 (.litertlm) via the LiteRT GenAI runtime.
 *
 * Parallel to [GemmaReportNarrator] (which drives MediaPipe tasks-genai for .task models):
 *  1. **It is optional and self-contained.** If the model cannot be loaded, [tryCreate]
 *     returns null and [ReportNarratorFactory] falls back to [TemplateReportNarrator].
 *  2. **It summarizes recorded findings only.** The prompt contains only structured inspection
 *     data. The model is forbidden from hallucinating findings, estimating repair costs, or
 *     assigning fault.
 *  3. **Its output is audited.** Every generated paragraph passes through [NarrationAudit].
 *     If accepted, the narrative is labeled [NarrationSource.LITERT_GEMMA] so generated PDFs
 *     honestly acknowledge that LiteRT GenAI wrote the paragraph.
 */
class LiteRtReportNarrator private constructor(
    private val generateFn: suspend (String) -> String,
    private val closeFn: () -> Unit,
    private val modelName: String,
    private val maxTokens: Int = DEFAULT_MAX_TOKENS
) : ReportNarrator {

    override suspend fun narrate(
        sectionTitle: String,
        findings: List<InspectionEntity>
    ): Narration {
        val fallback = TemplateReportNarrator.compose(findings)

        val raw = withContext(Dispatchers.Default) {
            runCatching { generateFn(prompt(sectionTitle, findings)) }
                .onFailure { Log.w(TAG, "LiteRT Gemma generation failed; using template", it) }
                .getOrNull()
        } ?: return Narration(fallback, NarrationSource.TEMPLATE, "generation failed ($modelName)")

        val text = raw.trim()
        return when (val verdict = NarrationAudit.check(text, findings)) {
            is NarrationAudit.Result.Accepted -> Narration(text, NarrationSource.LITERT_GEMMA)
            is NarrationAudit.Result.Rejected -> {
                Log.w(TAG, "LiteRT Gemma output rejected: ${verdict.reason}")
                Narration(fallback, NarrationSource.TEMPLATE, "rejected: ${verdict.reason}")
            }
        }
    }

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
        runCatching { closeFn() }
    }

    companion object {
        private const val TAG = "LiteRtNarrator"
        const val DEFAULT_MAX_TOKENS = 512

        /**
         * Test factory for simulating LiteRT generation without native binary dependencies.
         */
        fun createForTesting(
            modelName: String,
            maxTokens: Int = DEFAULT_MAX_TOKENS,
            generateFn: suspend (String) -> String
        ): LiteRtReportNarrator = LiteRtReportNarrator(
            generateFn = generateFn,
            closeFn = {},
            modelName = modelName,
            maxTokens = maxTokens
        )

        /**
         * Attempts to instantiate a LiteRT narrator for [file].
         *
         * Supports:
         *  1. LiteRT GenAI runtime (com.google.ai.edge.litert.genai / com.google.ai.edge.litert.lm)
         *  2. MediaPipe LlmInference runtime if compatible with the container
         *
         * Returns null if loading fails or native runtime is unavailable.
         */
        fun tryCreate(
            context: Context,
            file: File,
            maxTokens: Int = DEFAULT_MAX_TOKENS
        ): LiteRtReportNarrator? {
            // Attempt 1: Reflection on LiteRT GenAI SDK
            val litertEngine = tryCreateLiteRtEngine(context, file, maxTokens)
            if (litertEngine != null) {
                Log.i(TAG, "Successfully loaded LiteRT GenAI model: ${file.name}")
                return litertEngine
            }

            // Attempt 2: MediaPipe tasks-genai LlmInference
            val mediaPipeEngine = tryCreateMediaPipeEngine(context, file, maxTokens)
            if (mediaPipeEngine != null) {
                Log.i(TAG, "Successfully loaded .litertlm via MediaPipe LlmInference: ${file.name}")
                return mediaPipeEngine
            }

            Log.w(TAG, "Could not load LiteRT model at ${file.absolutePath}")
            return null
        }

        private fun tryCreateLiteRtEngine(
            context: Context,
            file: File,
            maxTokens: Int
        ): LiteRtReportNarrator? = runCatching {
            // Check for LiteRT GenAI classes:
            // "com.google.ai.edge.litert.genai.LlmInference" or "com.google.ai.edge.litert.lm.LlmInference"
            val classCandidates = listOf(
                "com.google.ai.edge.litert.genai.LlmInference",
                "com.google.ai.edge.litert.lm.LlmInference",
                "com.google.ai.edge.litert.LlmInference"
            )
            var engineClass: Class<*>? = null
            for (candidate in classCandidates) {
                try {
                    engineClass = Class.forName(candidate)
                    break
                } catch (_: ClassNotFoundException) {
                }
            }
            if (engineClass == null) return null

            // Builder or createFromOptions
            val optionsBuilderClass = Class.forName("${engineClass.name}\$LlmInferenceOptions")
            val builderMethod: Method = optionsBuilderClass.getMethod("builder")
            val builder = builderMethod.invoke(null)

            builder.javaClass.getMethod("setModelPath", String::class.java).invoke(builder, file.absolutePath)
            runCatching {
                builder.javaClass.getMethod("setMaxTokens", Int::class.javaPrimitiveType).invoke(builder, maxTokens)
            }
            runCatching {
                builder.javaClass.getMethod("setMaxTopK", Int::class.javaPrimitiveType).invoke(builder, 1)
            }
            val options = builder.javaClass.getMethod("build").invoke(builder)

            val createMethod = engineClass.getMethod("createFromOptions", Context::class.java, options.javaClass)
            val instance = createMethod.invoke(null, context, options)

            val generateMethod = instance.javaClass.getMethod("generateResponse", String::class.java)
            val closeMethod = instance.javaClass.getMethod("close")

            LiteRtReportNarrator(
                generateFn = { prompt -> generateMethod.invoke(instance, prompt) as String },
                closeFn = { closeMethod.invoke(instance) },
                modelName = file.name,
                maxTokens = maxTokens
            )
        }.onFailure {
            Log.d(TAG, "LiteRT GenAI runtime not found or failed initialization: ${it.message}")
        }.getOrNull()

        private fun tryCreateMediaPipeEngine(
            context: Context,
            file: File,
            maxTokens: Int
        ): LiteRtReportNarrator? = runCatching {
            val engineClass = runCatching {
                Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference")
            }.getOrNull() ?: return null

            val optionsClass = Class.forName("com.google.mediapipe.tasks.genai.llminference.LlmInference\$LlmInferenceOptions")
            val builderMethod = optionsClass.getMethod("builder")
            val builder = builderMethod.invoke(null)
            builder.javaClass.getMethod("setModelPath", String::class.java).invoke(builder, file.absolutePath)
            runCatching {
                builder.javaClass.getMethod("setMaxTokens", Int::class.javaPrimitiveType).invoke(builder, maxTokens)
            }
            runCatching {
                builder.javaClass.getMethod("setMaxTopK", Int::class.javaPrimitiveType).invoke(builder, 1)
            }
            val options = builder.javaClass.getMethod("build").invoke(builder)

            val createMethod = engineClass.getMethod("createFromOptions", Context::class.java, options.javaClass)
            val engine = createMethod.invoke(null, context, options)

            val generateMethod = engineClass.getMethod("generateResponse", String::class.java)
            val closeMethod = engineClass.getMethod("close")

            LiteRtReportNarrator(
                generateFn = { prompt -> generateMethod.invoke(engine, prompt) as String },
                closeFn = { closeMethod.invoke(engine) },
                modelName = file.name,
                maxTokens = maxTokens
            )
        }.onFailure {
            Log.d(TAG, "MediaPipe tasks-genai failed on .litertlm file: ${it.message}")
        }.getOrNull()
    }
}
