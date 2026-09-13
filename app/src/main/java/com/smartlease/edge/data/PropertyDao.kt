package com.smartlease.edge.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface PropertyDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProperty(property: PropertyEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: AreaEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRentalAgreement(agreement: RentalAgreementEntity): Long

    @Query("SELECT * FROM properties")
    suspend fun getAllProperties(): List<PropertyEntity>

    @Query("SELECT * FROM properties WHERE id = :propertyId")
    suspend fun getPropertyById(propertyId: String): PropertyEntity?

    @Query("SELECT * FROM areas WHERE propertyId = :propertyId")
    suspend fun getAreasForProperty(propertyId: String): List<AreaEntity>

    @Query("SELECT * FROM rental_agreements WHERE propertyId = :propertyId LIMIT 1")
    suspend fun getRentalAgreementForProperty(propertyId: String): RentalAgreementEntity?

    @Query("DELETE FROM properties WHERE id = :propertyId")
    suspend fun deleteProperty(propertyId: String)

    @Query("DELETE FROM areas WHERE id = :areaId")
    suspend fun deleteArea(areaId: String)
}
