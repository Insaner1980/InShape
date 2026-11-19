package com.emmah.inshape.data

enum class MeasurementBasis(val key: String, val optionLabel: String, val columnLabel: String, val unitLabel: String) {
    Weight("kg", "Weight (kg)", "kg", "kg"),
    MachineLevel("level", "Machine level", "Level", "level"),
    Bodyweight("bodyweight", "Bodyweight", "Reps only", "reps"),
    Assistance("assistance_kg", "Assisted (kg of help)", "Assist kg", "kg assist"),
    Time("duration", "Time", "Min", "min");

    fun formatValue(value: String): String = when (this) {
        MachineLevel -> "Level $value"
        else -> "$value $unitLabel"
    }

    companion object {
        fun fromKey(key: String): MeasurementBasis = entries.find { it.key == key } ?: Weight
    }
}
