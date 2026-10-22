package com.pulso.tv.model.helix.game

import com.pulso.tv.model.helix.Pagination
import kotlinx.serialization.Serializable

@Serializable
class GamesResponse(
    val data: List<Game>,
    val pagination: Pagination? = null,
)