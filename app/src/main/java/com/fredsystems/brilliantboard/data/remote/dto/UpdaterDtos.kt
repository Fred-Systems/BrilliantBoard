package com.fredsystems.brilliantboard.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class GitHubAssetDto(
    val name: String = "",
    @kotlinx.serialization.SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
)

@Serializable
data class GitHubReleaseDto(
    @kotlinx.serialization.SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    val assets: List<GitHubAssetDto> = emptyList(),
)
