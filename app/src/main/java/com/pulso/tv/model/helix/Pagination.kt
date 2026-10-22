package com.pulso.tv.model.helix

import kotlinx.serialization.Serializable

@Serializable
class Pagination(
    val cursor: String? = null,
)