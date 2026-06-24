package com.mlainton.nova

import com.google.firebase.messaging.FirebaseMessagingService

class NovaFirebaseMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Thread {
            val result = NovaApiClient.registerPushToken(token)
            if (result.ok) {
                android.util.Log.d("NOVA_PUSH", "Refreshed FCM token registered (length=${token.length})")
            } else {
                android.util.Log.w("NOVA_PUSH", "Refreshed FCM token registration failed")
            }
        }.start()
    }
}
