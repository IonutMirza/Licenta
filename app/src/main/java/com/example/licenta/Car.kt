package com.example.licenta.model

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Car(
    val id: String = "",
    val brand: String = "",
    val model: String = "",
    val year: Int = 0,
    val licensePlate: String = "",
    val userId: String = "",
    val rovinietaDate: String? = null,
    val insuranceDate: String? = null,
    val itpDate: String? = null,
    val notes: String? = null
)
