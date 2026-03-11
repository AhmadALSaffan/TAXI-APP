package com.taxiapp.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class User(
    val uid: String = "",
    val displayName: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val rating: Double = 5.0,
    val memberSince: Long = 0L,
    val defaultPaymentMethod: String = "",
    val homeAddress: String = "",
    val workAddress: String = "",
    val role: String = "passenger",
    val settings: UserSettings = UserSettings()
)

@IgnoreExtraProperties
data class UserSettings(
    val pushEnabled: Boolean = true,
    val emailEnabled: Boolean = false,
    val darkMode: Boolean = true,
    val language: String = "en"
)