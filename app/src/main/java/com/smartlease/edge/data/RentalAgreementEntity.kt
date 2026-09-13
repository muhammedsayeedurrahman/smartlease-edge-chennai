package com.smartlease.edge.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "rental_agreements",
    foreignKeys = [
        ForeignKey(
            entity = PropertyEntity::class,
            parentColumns = arrayOf("id"),
            childColumns = arrayOf("propertyId"),
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class RentalAgreementEntity(
    @PrimaryKey val id: String,
    val propertyId: String,
    val pdfFilePath: String,
    val dosAndDontsSummary: String? = null,
    val dosAndDontsPoints: String? = null, // Stored as a JSON array or newline-separated string
    /**
     * Name of the [com.smartlease.edge.narration.NarrationSource] that wrote
     * [dosAndDontsSummary] -- "GEMMA" or "TEMPLATE". Carried the same way every report section
     * already carries its narrator, so a report printing this text can credit it honestly
     * instead of implying every summary came from the model.
     */
    val dosAndDontsSource: String? = null
)
