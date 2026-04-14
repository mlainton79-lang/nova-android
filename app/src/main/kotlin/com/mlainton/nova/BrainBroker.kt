package com.mlainton.nova

enum class BrainMode(val displayName: String) {
    LOCAL_TONY("Local Tony"),
    OPENAI_LIVE("OpenAI"),
    GEMINI_MOCK("Gemini"),
    CLAUDE_MOCK("Claude"),
    COUNCIL_MOCK("Council")
}

object BrainBroker {
    fun reply(mode: BrainMode, message: String): BrokerResult {
        return when (mode) {
            BrainMode.LOCAL_TONY -> localTonyReply(message)
            else -> BrokerResult(reply = "Switch to a live brain for a proper response.", providerLabel = mode.displayName)
        }
    }

    private fun localTonyReply(message: String): BrokerResult {
        val lower = message.lowercase().trim()
        val reply = when {
            lower.contains("hello") || lower.contains("hi") -> "Hey. What do you need?"
            lower.contains("who are you") || lower.contains("what are you") -> "I'm Tony. Matthew's personal assistant, built into Nova."
            lower.contains("what can you do") -> "I can think, plan, and help you execute. Switch to OpenAI, Gemini, or Claude for full AI responses."
            else -> "I'm running locally. Switch to OpenAI, Gemini, or Claude for a proper response."
        }
        return BrokerResult(reply = reply, providerLabel = "Local Tony")
    }
}

data class BrokerResult(val reply: String, val providerLabel: String)
