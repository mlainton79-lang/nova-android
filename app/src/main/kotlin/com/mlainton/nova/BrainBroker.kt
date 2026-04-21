package com.mlainton.nova

enum class BrainMode(val displayName: String) {
    AUTO("Auto (smart routing)"),
    LOCAL_TONY("Local Tony"),
    GEMINI_MOCK("Gemini"),
    CLAUDE_MOCK("Claude"),
    OPENAI_LIVE("OpenAI"),
    GROQ("Groq (Llama 4)"),
    MISTRAL("Mistral"),
    DEEPSEEK("DeepSeek"),
    OPENROUTER("OpenRouter"),
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
            lower.contains("what can you do") -> "I can think, plan, and help you execute. Switch to Gemini, Groq, or Council for full AI responses."
            else -> "I'm running locally. Switch to Gemini, Groq, or Council for a proper response."
        }
        return BrokerResult(reply = reply, providerLabel = "Local Tony")
    }
}

data class BrokerResult(val reply: String, val providerLabel: String)