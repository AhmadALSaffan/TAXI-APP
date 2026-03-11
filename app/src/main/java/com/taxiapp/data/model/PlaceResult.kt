package com.taxiapp.data.model

data class PlaceResult(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val lat: Double = 0.0,
    val lng: Double = 0.0
)