package com.devledger.app.ui.screens.auth

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Carries the GitHub OAuth redirect result from [com.devledger.app.MainActivity]
 * (which receives it as a deep-link Intent, outside the Compose tree) into
 * [AuthScreen] (which is where the user is when the redirect actually arrives).
 *
 * extraBufferCapacity = 1 so the event isn't lost if MainActivity emits it a moment
 * before AuthScreen's collector subscribes (e.g. during activity recreation).
 */
object GitHubOAuthEvents {
    private val _events = MutableSharedFlow<GitHubOAuthResult>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    suspend fun emit(result: GitHubOAuthResult) = _events.emit(result)
}

sealed class GitHubOAuthResult {
    data class Success(val code: String) : GitHubOAuthResult()
    data class Failure(val reason: String) : GitHubOAuthResult()
}