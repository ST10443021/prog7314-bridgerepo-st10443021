package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FolderDto(
    val folderId: String,
    val name: String,
    val parentFolderId: String?,
    val snippetCount: Int,
    val createdAt: Long,
)

@Serializable
data class CreateFolderRequest(val name: String, val parentFolderId: String? = null)

@Serializable
data class DeleteFolderRequest(val reassignToFolderId: String? = null)