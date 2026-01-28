package com.application.zaona.weather.model

data class Sponsor(
    val name: String
)

data class SponsorData(
    val updatedAt: String,
    val sponsors: List<Sponsor>
)
