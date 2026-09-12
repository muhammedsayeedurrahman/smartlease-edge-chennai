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
    val dosAndDontsPoints: String? = null // Stored as a JSON array or newline-separated string
)
