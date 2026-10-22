package com.devledger.app.notifications

import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Firebase (n.d.): current-token retrieval. See REFERENCES-CODE.md. */
@Singleton
class FcmTokenProvider @Inject constructor() {
    suspend fun currentToken(): String = FirebaseMessaging.getInstance().token.await()
}
