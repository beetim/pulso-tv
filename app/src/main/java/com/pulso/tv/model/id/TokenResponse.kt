package com.pulso.tv.model.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class TokenResponse(
    @SerialName("access_token")
    val token: String? = null,
    @SerialName("refresh_token")
    val refreshToken: String? = null,
    val message: String? = null,
)