package com.mlainton.nova

import android.content.Context

object LiveBrokerClient {
    fun askOpenAi(context: Context, message: String): BrokerResult {
        return BrokerResult(
            providerLabel = "OpenAI live",
            reply = "OpenAI live is not wired up yet. The next step is to replace this placeholder with the Railway/FastAPI backend call."
        )
    }
}
