package com.emmah.inshape.feature.checkin

data class BodyRegion(val id: String, val label: String)
val bodyRegions: List<BodyRegion> =
    listOf("neck", "upper_back", "lower_back", "chest", "abdomen").map {
        BodyRegion(it, it.replace('_', ' ').replaceFirstChar(Char::titlecase))
    } +
        listOf("shoulder", "upper_arm", "elbow", "forearm_wrist", "hip", "thigh", "knee", "calf_shin", "ankle_foot").flatMap { part ->
            listOf("left", "right").map { side ->
                BodyRegion(
                    "${side}_$part",
                    "$side ${if (part == "forearm_wrist") {
                        "wrist and forearm"
                    } else {
                        part.replace(
                            '_',
                            ' '
                        )
                    }}".replaceFirstChar(Char::titlecase)
                )
            }
        }
