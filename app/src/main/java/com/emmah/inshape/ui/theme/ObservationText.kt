package com.emmah.inshape.ui.theme

import com.emmah.inshape.data.BodyObservation

fun observationText(observation: BodyObservation): String =
    "${observation.region.replace('_', ' ')}: pain ${observation.pain?.toString() ?: "not recorded"}, stiffness " +
        "${observation.stiffness?.toString() ?: "not recorded"}"
