package com.pulso.tv.model.chat

import com.pulso.tv.util.TwitchApiHelper

class Raid(
    val raidId: String? = null,
    val targetId: String? = null,
    val targetLogin: String? = null,
    val targetName: String? = null,
    val targetImageURL: String? = null,
    val viewerCount: Int? = null,
    val openStream: Boolean,
) {

    val targetImage: String?
        get() = TwitchApiHelper.getProfileImage(targetImageURL)
}