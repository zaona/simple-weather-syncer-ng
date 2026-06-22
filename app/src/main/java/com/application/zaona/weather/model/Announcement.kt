package com.application.zaona.weather.model

data class Announcement(
    val id: String,
    val title: String,
    val content: String,
    val publishDate: String,
    val importance: String,       // "normal" / "high"
    val versionCodeMin: Int?,     // null = no min limit
    val versionCodeMax: Int?,     // null = no max limit
    val enabled: Boolean
)

data class AnnouncementData(
    val announcements: List<Announcement>
)
