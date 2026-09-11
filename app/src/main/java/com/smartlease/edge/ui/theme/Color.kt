package com.smartlease.edge.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Instrument palette.
 *
 * This app is used standing in an empty flat at handover, frequently a dim one -- basements,
 * unfurnished rooms with the power off, stairwells. A dark ground is the functional choice
 * there, not a stylistic one: less glare against a torch-lit wall, and less battery drawn
 * during a walkthrough that has to last the whole viewing.
 *
 * Status is carried by three semantic colours borrowed from measurement equipment rather than
 * one decorative accent. Amber means "check this", green means "verified", red means "Tier-1
 * flag". A reading is never conveyed by colour alone -- every lamp sits beside a value and a
 * label, because a landlord and a tenant read this screen together and one of them may be
 * colour-blind.
 */

// Ground and panels
val SlateGround = Color(0xFF12151A)   // app background
val SlatePanel = Color(0xFF1C222B)    // raised surfaces: cards, readout strip
val SlateEdge = Color(0xFF2B3340)     // hairlines, dividers, control outlines

// Readout text
val ReadoutPrimary = Color(0xFFE8EAED)
val ReadoutDim = Color(0xFF7C8798)

// Status lamps
val LampAmber = Color(0xFFF2A93B)     // caution, alignment drift, inconclusive
val LampGreen = Color(0xFF3FBF7F)     // verified, aligned, pass
val LampRed = Color(0xFFE5484D)       // Tier-1 defect, safety escalation

// Light-mode equivalents, kept for the system-light case. Deliberately paper-like rather
// than white: an inspection report is a document, and this is the screen it comes from.
val PaperGround = Color(0xFFF7F6F3)
val PaperPanel = Color(0xFFFFFFFF)
val PaperEdge = Color(0xFFDCDAD4)
val InkPrimary = Color(0xFF15181D)
val InkDim = Color(0xFF5C6472)
