package com.pulso.tv.model.helix.video

import com.pulso.tv.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class VideosResponse(
    val data: List<Video>,
    val pagination: Pagination? = null,
)