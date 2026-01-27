package com.application.zaona.weather.model

import com.google.gson.annotations.SerializedName

data class CityLocation(
    @SerializedName("id") val id: String,
    @SerializedName("name") val name: String,
    @SerializedName("adm1") val adm1: String, // Province
    @SerializedName("adm2") val adm2: String  // City
) {
    override fun toString(): String {
        return "$name ($adm1 - $adm2)"
    }
}

data class GeoResponse(
    @SerializedName("code") val code: String,
    @SerializedName("location") val location: List<CityLocation>?
)
