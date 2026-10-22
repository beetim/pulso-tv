package com.pulso.tv.model.helix.chat

import kotlinx.serialization.Serializable

@Serializable
class EmoteSetsResponse(
    val template: String,
    val data: List<EmoteTemplate>,
)