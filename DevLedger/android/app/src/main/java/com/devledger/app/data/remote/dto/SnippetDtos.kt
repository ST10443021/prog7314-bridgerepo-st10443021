package com.devledger.app.data.remote.dto

import kotlinx.serialization.Serializable

// Reference scope: kotlinx.serialization DTOs. (Kotlin, n.d.). See REFERENCES-CODE.md for full entries.

@Serializable
data class SnippetDto(
    val snippetId: String,
    val folderId: String? = null,
    val title: String,
    val content: String,
    val language: String,
    val description: String? = null,
    val tags: List<String> = emptyList(),
    val isFavourite: Boolean = false,
    val isArchived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class CreateSnippetRequest(
    val title: String,
    val content: String,
    val language: String,
    val folderId: String? = null,
    val description: String? = null,
    val tags: List<String> = emptyList(),
)

@Serializable
data class UserTagDto(
    val tagId: String,
    val name: String,
    val colorHex: String? = null,
    val createdAt: Long,
)
