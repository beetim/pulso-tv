package com.pulso.tv.model.gql

import kotlinx.serialization.Serializable

@Serializable
class ErrorResponse(
    val errors: List<Error>? = null,
)