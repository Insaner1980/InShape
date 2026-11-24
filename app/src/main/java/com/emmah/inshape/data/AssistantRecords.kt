package com.emmah.inshape.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity
data class AssistantDraft(@PrimaryKey val key: String, val id: String, val input: String, val review: String? = null)

@Entity
data class AssistantReceipt(@PrimaryKey val id: String, val digest: String, val targetId: String, val resultIds: String)

@Serializable
@Entity
data class AssistantMemory(
    @PrimaryKey val id: String,
    val kind: String,
    val exerciseId: String?,
    val text: String,
    val creationWorkoutId: String?,
    val acknowledgedAt: String? = null,
    val revision: Long = 1
)
