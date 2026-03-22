package com.zendeck.app.domain.model

enum class ReadingRating(
    val label: String,
    val shortLabel: String,
    val icon: String
) {
    DEFINITELY_READ("Definitely Read", "Must", "★"),
    GOOD_TO_READ("Good to Read", "Worth", "◆"),
    CAN_SKIP("Can Skip", "Skip", "○")
}
