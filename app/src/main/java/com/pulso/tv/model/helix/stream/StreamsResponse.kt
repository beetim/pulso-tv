package com.pulso.tv.model.helix.stream

import com.pulso.tv.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class StreamsResponse(
    val data: List<Stream>,
    val pagination: Pagination? = null,
)