package com.example.licenta.model

data class Car(
    val brand: String = "",
    val model: String = "",
    val year: String = "",
    val licensePlate: String = "",
    val userId: String = "" // 🔑 IMPORTANT: trebuie să existe
)