package com.pulso.tv.model.gql

import kotlinx.serialization.Serializable

@Serializable
class PageInfo(
    val hasNextPage: Boolean? = null,
)