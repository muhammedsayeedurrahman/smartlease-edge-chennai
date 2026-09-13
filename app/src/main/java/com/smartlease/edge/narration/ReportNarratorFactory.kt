package com.smartlease.edge.narration

import android.content.Context
import android.util.Log

/**
 * Chooses the narrator for this device and reports honestly which one it got.
 *
 * The choice is made from what is actually on the phone, never from a build flag or a
 * preference. A user cannot switch narration "on" -- they can put the weights on the device or
 * not, and the app tells them which situation they are in. That keeps the app's claim about
 * itself ("this report was written by an on-device model") tied to a file that either exists
 * and loaded, or does not.
 *
 * Before loading, [ModelLoadBalancer] checks available RAM and thermal state so that
 * attempting to load a multi-gigabyte model does not get the process killed by Android's Low
 * Memory Killer -- it either scales the token budget down or skips straight to
 * [TemplateReportNarrator] with an honest reason.
 */
object ReportNarratorFactory {

    private const val TAG = "NarratorFactory"

    /**
     * @param narrator the narrator to use. Always usable -- worst case it is
     * [TemplateReportNarrator].
     * @param status one line for diagnostics and the setup screen, saying what was found.
     */
    data class Selection(
        val narrator: ReportNarrator,
        val status: String,
        val usingModel: Boolean
    )

    /**
     * What narration this device is set up for, without loading anything.
     *
     * Separate from [create] because the self-test screen wants the answer and must not pay
     * for it: [create] loads the weights, which pins hundreds of megabytes and takes seconds.
     * A diagnostics screen that did that would stall on open and hold the model for as long
     * as it was on screen.
     *
     * The trade is stated rather than hidden: this reports that a model is *present*, which
     * is not the same as knowing it *loads*. Only [create] can tell you that, and only by
     * doing it. So the wording here says "found", and the loaded-and-working wording is
     * [Selection.status]'s alone.
     */
    fun describe(context: Context): String =
        when (val location = GemmaModelLocator.locate(context)) {
            is GemmaModelLocator.Location.Missing ->
                "rule-based templating - no model file in " + location.searched.joinToString(" or ")

            is GemmaModelLocator.Location.Found -> {
                val availMb = ModelLoadBalancer.getAvailableMemoryBytes(context) / (1024 * 1024)
                val modelMb = location.file.length() / (1024 * 1024)
                val format = if (location.isLiteRtLm) "LiteRT-LM" else "MediaPipe .task"
                "Gemma model found ($format) - ${location.file.name} " +
                    "(${modelMb} MB, ${availMb} MB RAM free), loaded at report time"
            }
        }

    fun create(context: Context): Selection =
        when (val location = GemmaModelLocator.locate(context)) {
            is GemmaModelLocator.Location.Missing -> {
                Log.i(TAG, "No Gemma model found; narration is rule-based templating")
                Selection(
                    narrator = TemplateReportNarrator,
                    status = "rule-based templating - no model file in " +
                        location.searched.joinToString(" or "),
                    usingModel = false
                )
            }

            is GemmaModelLocator.Location.Found -> {
                val megabytes = location.file.length() / (1024 * 1024)
                val decision = ModelLoadBalancer.assess(context, location.file)

                if (decision is ModelLoadBalancer.LoadDecision.Skip) {
                    Log.w(TAG, "Load balancer skipped LLM loading: ${decision.reason}")
                    Selection(
                        narrator = TemplateReportNarrator,
                        status = "rule-based templating - ${decision.reason}",
                        usingModel = false
                    )
                } else {
                    val maxTokens = decision.recommendedMaxTokens
                    val gemma = GemmaReportNarrator.tryCreate(context, location.file, maxTokens)
                    if (gemma == null) {
                        // Found but unusable is a distinct state from absent, and worth saying so:
                        // it is the difference between "you have not set this up" and "you set it
                        // up and it is broken", which need different things from the user.
                        Selection(
                            narrator = TemplateReportNarrator,
                            status = "rule-based templating - ${location.file.name} " +
                                "(${megabytes} MB) failed to load",
                            usingModel = false
                        )
                    } else {
                        val label = if (location.isLiteRtLm) "on-device Gemma (LiteRT)" else "on-device Gemma"
                        val budgetNote = if (decision is ModelLoadBalancer.LoadDecision.Tight) " [budget: ${maxTokens}t]" else ""
                        Selection(
                            narrator = gemma,
                            status = "$label - ${location.file.name} (${megabytes} MB)$budgetNote",
                            usingModel = true
                        )
                    }
                }
            }
        }
}
