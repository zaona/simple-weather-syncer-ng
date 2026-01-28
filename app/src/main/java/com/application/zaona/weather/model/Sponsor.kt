package com.application.zaona.weather.model

data class Sponsor(
    val name: String,
    val totalAmount: Double
) {
    companion object {
        const val MINIMUM_SUPPORT_AMOUNT = 20.0
    }
}
