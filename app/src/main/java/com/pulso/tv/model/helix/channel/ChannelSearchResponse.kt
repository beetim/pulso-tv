package com.pulso.tv.model.helix.channel

import com.pulso.tv.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class ChannelSearchResponse(
    val data: List<ChannelSearch>,
    val pagination: Pagination? = null,
)