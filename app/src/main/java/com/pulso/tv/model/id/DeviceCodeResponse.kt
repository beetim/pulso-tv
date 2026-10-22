package com.pulso.tv.model.id

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class DeviceCodeResponse(
    @SerialName("device_code")
    val deviceCode: String? = null,
    @SerialName("user_code")
    val userCode: String? = null,
    // seconds until the code dies and seconds to wait between polls
    @SerialName("expires_in")
    val expiresIn: Int? = null,
    @SerialName("interval")
    val interval: Int? = null,
)
