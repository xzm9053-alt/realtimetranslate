package com.xzm.realtimetranslate.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A user-configured glossary term mapping from [source] language term to [target] language translation.
 */
@Serializable
data class GlossaryTerm(
    val id: String = UUID.randomUUID().toString(),
    val source: String,
    val target: String,
    val enabled: Boolean = true,
    val note: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
