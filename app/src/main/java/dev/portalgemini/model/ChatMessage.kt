package dev.portalgemini.model

import java.util.UUID

enum class ChatSender {
    USER,
    GEMINI
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: ChatSender,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val sessionId: String = "",
    val sessionIndex: Int = 1
)

data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val index: Int,
    val startTimeMs: Long = System.currentTimeMillis(),
    var endTimeMs: Long? = null,
    val messages: MutableList<ChatMessage> = mutableListOf()
) {
    val isEnded: Boolean get() = endTimeMs != null
    val turnCount: Int get() = messages.size
}
