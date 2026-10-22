package com.pulso.tv.model.helix.clip

import com.pulso.tv.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class ClipsResponse(
    val data: List<Clip>,
    val pagination: Pagination? = null,
)