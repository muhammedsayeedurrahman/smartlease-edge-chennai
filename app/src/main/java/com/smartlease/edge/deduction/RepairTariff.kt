package com.smartlease.edge.deduction

/**
 * Chennai repair rates used to turn a measured defect into a rupee figure.
 *
 * PROVENANCE, because this is the number a landlord and tenant will argue over: these are
 * the team's own market estimates compiled from local contractor quotes for interior wall
 * work in Chennai in 2026. They are NOT published tariffs and no official rate card exists
 * for this. The report prints them as estimates and says so. Anyone deploying this for real
 * money must replace [rateCard] with quotes they can stand behind.
 *
 * Each rate carries a `minimumRupees` because small repairs do not scale linearly: a
 * contractor called out for a 0.4 sq ft crack still charges for the visit, the mixing, and
 * the repaint of the surrounding patch. Ignoring that is what makes naive per-sq-ft
 * estimates read as obviously wrong to anyone who has actually paid for the work.
 */
object RepairTariff {

    /**
     * @param ratePerSqFtRupees marginal cost of area beyond what the minimum already covers.
     * @param minimumRupees call-out floor — charged even for a hairline defect.
     */
    data class Rate(
        val ratePerSqFtRupees: Int,
        val minimumRupees: Int,
        val workDescription: String,
        /**
         * What the report calls this defect. Separate from the map key because the key is the
         * model's dataset vocabulary (`damp_stain`) and a tenant-facing PDF must never print
         * an underscore-cased label lifted straight out of a data.yaml.
         */
        val displayName: String
    )

    /**
     * Keyed by the YOLOv8n-Seg class name in `YoloSegConfig.CLASS_NAMES` — the exact strings,
     * not their descriptions. `RepairTariffCoverageTest` fails the build if the two drift.
     */
    private val rateCard: Map<String, Rate> = mapOf(
        "crack" to Rate(85, 1_500, "Crack routing, filling, plaster patch and repaint", "Crack in wall surface"),
        "peeling" to Rate(45, 800, "Scrape, prime and repaint affected area", "Peeling paint"),
        "spalling" to Rate(150, 2_000, "Cut back to sound substrate, re-render and repaint", "Spalling"),
        "damp_stain" to Rate(60, 900, "Anti-fungal treatment, stain block and repaint", "Damp stain or mould")
    )

    /** Per-tile replacement, including lifting the failed tile and re-bedding. */
    const val HOLLOW_TILE_RUPEES = 900

    /** Charged when an appliance is tested and fails to respond. */
    const val APPLIANCE_NOT_FUNCTIONAL_RUPEES = 3_500

    fun rateFor(defectClass: String): Rate? = rateCard[defectClass.lowercase()]

    /**
     * Cost of one defect of [defectClass] covering [areaSqFt].
     *
     * Returns null for a class with no rate rather than silently costing it at zero — an
     * unpriced defect must show up as unpriced in the report, not as free.
     */
    fun costOf(defectClass: String, areaSqFt: Float): Int? {
        val rate = rateFor(defectClass) ?: return null
        val areaCost = (areaSqFt * rate.ratePerSqFtRupees).toInt()
        return maxOf(rate.minimumRupees, areaCost)
    }

    fun basisFor(defectClass: String, areaSqFt: Float): String? {
        val rate = rateFor(defectClass) ?: return null
        val areaCost = (areaSqFt * rate.ratePerSqFtRupees).toInt()
        return if (areaCost <= rate.minimumRupees) {
            "%.2f sq ft · minimum call-out ₹%,d (%s)".format(areaSqFt, rate.minimumRupees, rate.workDescription)
        } else {
            "%.2f sq ft × ₹%,d/sq ft (%s)".format(areaSqFt, rate.ratePerSqFtRupees, rate.workDescription)
        }
    }
}
