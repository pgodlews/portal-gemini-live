package dev.portalgemini.util

import dev.portalgemini.model.ChatMessage
import dev.portalgemini.model.ChatSender
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationExporter {

    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

    fun toMarkdown(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("# Gemini Live Conversation\n\n")
        sb.append("Exported: ${dateTimeFormat.format(Date())}\n")
        sb.append("Total Turns: ${messages.size}\n\n---\n\n")

        val sessionGroups = messages.groupBy { it.sessionIndex }
        for ((sessionIdx, sessionMsgs) in sessionGroups) {
            val firstTime = timeFormat.format(Date(sessionMsgs.first().timestampMs))
            val lastTime = timeFormat.format(Date(sessionMsgs.last().timestampMs))
            sb.append("## 💬 Session $sessionIdx ($firstTime - $lastTime) · ${sessionMsgs.size} turns\n\n")
            for (msg in sessionMsgs) {
                val time = timeFormat.format(Date(msg.timestampMs))
                val senderLabel = if (msg.sender == ChatSender.USER) "👤 You" else "🤖 Gemini"
                sb.append("### $senderLabel ($time)\n\n")
                sb.append("${msg.text.trim()}\n\n")
            }
            sb.append("---\n\n")
        }

        return sb.toString()
    }

    fun toPlainText(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("=== Gemini Live Conversation ===\n")
        sb.append("Exported: ${dateTimeFormat.format(Date())}\n")
        sb.append("Total Turns: ${messages.size}\n\n")

        val sessionGroups = messages.groupBy { it.sessionIndex }
        for ((sessionIdx, sessionMsgs) in sessionGroups) {
            val firstTime = timeFormat.format(Date(sessionMsgs.first().timestampMs))
            val lastTime = timeFormat.format(Date(sessionMsgs.last().timestampMs))
            sb.append("--- Session $sessionIdx ($firstTime - $lastTime) · ${sessionMsgs.size} turns ---\n\n")
            for (msg in sessionMsgs) {
                val time = timeFormat.format(Date(msg.timestampMs))
                val senderLabel = if (msg.sender == ChatSender.USER) "You" else "Gemini"
                sb.append("[$time] $senderLabel:\n")
                sb.append("${msg.text.trim()}\n\n")
            }
        }

        return sb.toString()
    }

    fun toHtml(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        val sessionGroups = messages.groupBy { it.sessionIndex }

        sb.append("""
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Gemini Live Conversation</title>
    <style>
        :root {
            --bg-color: #0d1117;
            --card-bg: #161b22;
            --user-bg: #1f293d;
            --gemini-bg: #261c3b;
            --user-accent: #38bdf8;
            --gemini-accent: #c084fc;
            --text-color: #e6edf3;
            --text-muted: #8b949e;
            --border-color: #30363d;
        }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            background-color: var(--bg-color);
            color: var(--text-color);
            line-height: 1.6;
            margin: 0;
            padding: 20px;
            display: flex;
            justify-content: center;
        }
        .container {
            max-width: 760px;
            width: 100%;
        }
        .header {
            border-bottom: 1px solid var(--border-color);
            padding-bottom: 16px;
            margin-bottom: 24px;
        }
        .header h1 {
            margin: 0 0 8px 0;
            font-size: 24px;
            font-weight: 700;
            background: linear-gradient(135deg, #38bdf8, #818cf8, #c084fc);
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
        }
        .meta {
            color: var(--text-muted);
            font-size: 13px;
        }
        .session-divider {
            text-align: center;
            margin: 28px 0 16px 0;
            position: relative;
        }
        .session-divider::before {
            content: "";
            position: absolute;
            top: 50%;
            left: 0;
            right: 0;
            border-top: 1px solid var(--border-color);
            z-index: 1;
        }
        .session-divider span {
            position: relative;
            z-index: 2;
            background-color: var(--card-bg);
            padding: 6px 16px;
            border-radius: 12px;
            border: 1px solid var(--border-color);
            font-size: 13px;
            font-weight: 600;
            color: var(--text-muted);
        }
        .message {
            margin-bottom: 16px;
            padding: 14px 18px;
            border-radius: 12px;
            border: 1px solid var(--border-color);
            box-shadow: 0 2px 8px rgba(0, 0, 0, 0.2);
        }
        .message.user {
            background-color: var(--user-bg);
            border-left: 4px solid var(--user-accent);
        }
        .message.gemini {
            background-color: var(--gemini-bg);
            border-left: 4px solid var(--gemini-accent);
        }
        .message-header {
            display: flex;
            justify-content: space-between;
            font-size: 12px;
            font-weight: 600;
            margin-bottom: 6px;
        }
        .user .sender { color: var(--user-accent); }
        .gemini .sender { color: var(--gemini-accent); }
        .time { color: var(--text-muted); font-weight: normal; }
        .content {
            font-size: 15px;
            white-space: pre-wrap;
            word-break: break-word;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Gemini Live Conversation</h1>
            <div class="meta">Exported: ${dateTimeFormat.format(Date())} · Total Turns: ${messages.size} · Sessions: ${sessionGroups.size}</div>
        </div>
        """.trimIndent())

        for ((sessionIdx, sessionMsgs) in sessionGroups) {
            val firstTime = timeFormat.format(Date(sessionMsgs.first().timestampMs))
            val lastTime = timeFormat.format(Date(sessionMsgs.last().timestampMs))
            sb.append("""
        <div class="session-divider">
            <span>💬 Session $sessionIdx ($firstTime - $lastTime) · ${sessionMsgs.size} turns</span>
        </div>
            """.trimIndent())

            for (msg in sessionMsgs) {
                val isUser = msg.sender == ChatSender.USER
                val time = timeFormat.format(Date(msg.timestampMs))
                val senderClass = if (isUser) "user" else "gemini"
                val senderLabel = if (isUser) "You" else "Gemini"
                val escapedText = msg.text.trim()
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")

                sb.append("""
        <div class="message $senderClass">
            <div class="message-header">
                <span class="sender">$senderLabel</span>
                <span class="time">$time</span>
            </div>
            <div class="content">$escapedText</div>
        </div>
                """.trimIndent())
            }
        }

        sb.append("""
    </div>
</body>
</html>
        """.trimIndent())

        return sb.toString()
    }
}
