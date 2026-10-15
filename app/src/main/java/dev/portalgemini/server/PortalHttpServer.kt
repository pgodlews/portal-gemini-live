package dev.portalgemini.server

import android.content.Context
import android.util.Log
import dev.portalgemini.api.LiveConfig
import dev.portalgemini.model.ChatMessage
import dev.portalgemini.util.ConversationExporter
import dev.portalgemini.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class ServerMode {
    SETUP,
    EXPORT
}

class PortalHttpServer(
    private val context: Context,
    val mode: ServerMode,
    private val initialConfig: LiveConfig? = null,
    private val conversationMessages: List<ChatMessage> = emptyList(),
    private val onConfigReceived: ((apiKey: String, model: String, voice: String) -> Unit)? = null
) {
    companion object {
        private const val TAG = "PortalHttpServer"
        const val DEFAULT_PORT = 8080
    }

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null

    val sessionToken: String = UUID.randomUUID().toString()
    private var actualPort: Int = DEFAULT_PORT

    private val _statusText = MutableStateFlow("Waiting for scan...")
    val statusText: StateFlow<String> = _statusText

    private val _isConfigSaved = MutableStateFlow(false)
    val isConfigSaved: StateFlow<Boolean> = _isConfigSaved

    fun getUrl(): String? {
        val ip = NetworkUtils.getLocalIpAddress(context) ?: return null
        val path = if (mode == ServerMode.EXPORT) "export" else ""
        return "http://$ip:$actualPort/$path?token=$sessionToken"
    }

    fun start(): Boolean {
        try {
            // Find open port starting from DEFAULT_PORT
            var port = DEFAULT_PORT
            var ss: ServerSocket? = null
            for (p in port..port + 10) {
                try {
                    ss = ServerSocket(p)
                    actualPort = p
                    break
                } catch (e: Exception) {
                    // Port in use, try next
                }
            }

            if (ss == null) {
                Log.e(TAG, "Failed to bind any port from $port to ${port + 10}")
                return false
            }

            serverSocket = ss
            Log.i(TAG, "PortalHttpServer started on port $actualPort (mode=$mode)")

            serverJob = scope.launch {
                while (isActive) {
                    try {
                        val client = ss.accept()
                        scope.launch { handleClient(client) }
                    } catch (e: Exception) {
                        if (!ss.isClosed) {
                            Log.w(TAG, "Socket accept error: ${e.message}")
                        }
                        break
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start server", e)
            return false
        }
    }

    fun stop() {
        try {
            serverJob?.cancel()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing server socket: ${e.message}")
        } finally {
            serverSocket = null
            Log.i(TAG, "PortalHttpServer stopped")
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            socket.soTimeout = 10000
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            if (parts.size < 2) return

            val method = parts[0]
            val fullPath = parts[1]

            // Parse path and query params
            val pathParts = fullPath.split("?", limit = 2)
            val path = pathParts[0]
            val queryMap = mutableMapOf<String, String>()
            if (pathParts.size > 1) {
                for (param in pathParts[1].split("&")) {
                    val kv = param.split("=", limit = 2)
                    if (kv.isNotEmpty()) {
                        queryMap[kv[0]] = if (kv.size > 1) java.net.URLDecoder.decode(kv[1], "UTF-8") else ""
                    }
                }
            }

            // Read headers
            var contentLength = 0
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                if (line.isNullOrBlank()) break
                val lower = line!!.lowercase()
                if (lower.startsWith("content-length:")) {
                    contentLength = lower.substringAfter(":").trim().toIntOrNull() ?: 0
                }
            }

            // Security check: Validate session token
            val reqToken = queryMap["token"]
            if (reqToken != sessionToken) {
                sendResponse(out, 403, "text/plain", "Forbidden: Invalid or missing session token.")
                return
            }

            _statusText.value = "Phone connected!"

            when {
                // SETUP MODE
                mode == ServerMode.SETUP && method == "GET" && (path == "/" || path.isEmpty()) -> {
                    val html = buildSetupHtml(initialConfig)
                    sendResponse(out, 200, "text/html; charset=utf-8", html)
                }

                mode == ServerMode.SETUP && method == "POST" && path == "/api/setup" -> {
                    if (contentLength > 65536) {
                        sendResponse(out, 413, "text/plain", "Payload Too Large")
                        return
                    }
                    val bodyChars = CharArray(contentLength)
                    var readTotal = 0
                    while (readTotal < contentLength) {
                        val read = reader.read(bodyChars, readTotal, contentLength - readTotal)
                        if (read == -1) break
                        readTotal += read
                    }
                    val body = String(bodyChars, 0, readTotal)
                    val json = JSONObject(body)
                    val apiKey = json.optString("apiKey", "").trim()
                    val model = json.optString("model", "models/gemini-3.8-live").trim()
                    val voice = json.optString("voice", "Aoede").trim()

                    if (apiKey.isNotBlank()) {
                        onConfigReceived?.invoke(apiKey, model, voice)
                        _statusText.value = "✓ Configuration saved!"
                        _isConfigSaved.value = true
                        val resJson = JSONObject().put("status", "ok").put("message", "Configuration saved!").toString()
                        sendResponse(out, 200, "application/json", resJson)
                    } else {
                        val resJson = JSONObject().put("status", "error").put("message", "API Key is required").toString()
                        sendResponse(out, 400, "application/json", resJson)
                    }
                }

                // EXPORT MODE
                mode == ServerMode.EXPORT && method == "GET" && (path == "/export" || path == "/" || path.isEmpty()) -> {
                    val html = buildExportHtml(conversationMessages)
                    sendResponse(out, 200, "text/html; charset=utf-8", html)
                }

                mode == ServerMode.EXPORT && method == "GET" && path == "/export/download" -> {
                    val format = queryMap["format"] ?: "md"
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    when (format) {
                        "md" -> {
                            val md = ConversationExporter.toMarkdown(conversationMessages)
                            sendDownloadResponse(out, "text/markdown; charset=utf-8", "gemini_conversation_$timestamp.md", md)
                        }
                        "txt" -> {
                            val txt = ConversationExporter.toPlainText(conversationMessages)
                            sendDownloadResponse(out, "text/plain; charset=utf-8", "gemini_conversation_$timestamp.txt", txt)
                        }
                        "html" -> {
                            val html = ConversationExporter.toHtml(conversationMessages)
                            sendDownloadResponse(out, "text/html; charset=utf-8", "gemini_conversation_$timestamp.html", html)
                        }
                        else -> {
                            sendResponse(out, 400, "text/plain", "Unknown format")
                        }
                    }
                }

                else -> {
                    sendResponse(out, 404, "text/plain", "Not Found")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling client request (${e.javaClass.simpleName})")
        } finally {
            try { socket.close() } catch (e: Exception) { /* ignored */ }
        }
    }

    private fun sendResponse(out: OutputStream, code: Int, contentType: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val statusMsg = when (code) {
            200 -> "OK"
            400 -> "Bad Request"
            403 -> "Forbidden"
            404 -> "Not Found"
            else -> "Error"
        }
        val headers = "HTTP/1.1 $code $statusMsg\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\n" +
                "Referrer-Policy: no-referrer\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    private fun sendDownloadResponse(out: OutputStream, contentType: String, filename: String, content: String) {
        val bytes = content.toByteArray(Charsets.UTF_8)
        val headers = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: $contentType\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Content-Disposition: attachment; filename=\"$filename\"\r\n" +
                "Cache-Control: no-store\r\n" +
                "Referrer-Policy: no-referrer\r\n" +
                "X-Content-Type-Options: nosniff\r\n" +
                "Connection: close\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        out.write(bytes)
        out.flush()
    }

    // --- HTML TEMPLATES ---

    private fun buildSetupHtml(cfg: LiveConfig?): String {
        val currentModel = cfg?.model ?: "models/gemini-3.8-live"
        val currentVoice = cfg?.voice ?: "Puck"

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Portal Gemini Live · Setup</title>
    <style>
        :root {
            --bg: #0d1117;
            --card: #161b22;
            --border: #30363d;
            --text: #e6edf3;
            --muted: #8b949e;
            --blue: #38bdf8;
            --purple: #c084fc;
            --green: #4ade80;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: var(--bg);
            color: var(--text);
            min-height: 100vh;
            display: flex;
            align-items: center;
            justify-content: center;
            padding: 20px;
        }
        .card {
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 16px;
            max-width: 480px;
            width: 100%;
            padding: 28px;
            box-shadow: 0 8px 32px rgba(0,0,0,0.5);
        }
        .header {
            text-align: center;
            margin-bottom: 24px;
        }
        .header h1 {
            font-size: 24px;
            font-weight: 700;
            background: linear-gradient(135deg, var(--blue), var(--purple));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            margin-bottom: 6px;
        }
        .header p { color: var(--muted); font-size: 14px; }
        .form-group { margin-bottom: 20px; }
        label {
            display: block;
            font-size: 13px;
            font-weight: 600;
            margin-bottom: 8px;
            color: var(--text);
        }
        .input-row {
            display: flex;
            gap: 8px;
        }
        input, select {
            width: 100%;
            padding: 12px 14px;
            background: #0d1117;
            border: 1px solid var(--border);
            border-radius: 8px;
            color: var(--text);
            font-size: 15px;
            outline: none;
            transition: border-color 0.2s;
        }
        input:focus, select:focus {
            border-color: var(--blue);
        }
        button.paste-btn {
            background: #21262d;
            border: 1px solid var(--border);
            color: var(--blue);
            padding: 0 16px;
            border-radius: 8px;
            font-weight: 600;
            cursor: pointer;
            white-space: nowrap;
        }
        .help-link {
            display: block;
            margin-top: 6px;
            font-size: 12px;
            color: var(--blue);
            text-decoration: none;
        }
        .help-link:hover { text-decoration: underline; }
        .submit-btn {
            width: 100%;
            padding: 14px;
            background: linear-gradient(135deg, #2563eb, #7c3aed);
            border: none;
            border-radius: 10px;
            color: white;
            font-size: 16px;
            font-weight: 600;
            cursor: pointer;
            transition: transform 0.1s, opacity 0.2s;
            margin-top: 8px;
        }
        .submit-btn:active { transform: scale(0.98); }
        .submit-btn:disabled { opacity: 0.5; cursor: not-allowed; }
        .success-box {
            display: none;
            text-align: center;
            padding: 24px 0;
        }
        .success-icon {
            font-size: 48px;
            color: var(--green);
            margin-bottom: 12px;
        }
        .success-title { font-size: 20px; font-weight: 700; margin-bottom: 8px; }
        .success-desc { color: var(--muted); font-size: 14px; }
    </style>
</head>
<body>
    <div class="card">
        <div id="formView">
            <div class="header">
                <h1>Portal Gemini Live</h1>
                <p>Configure from your phone over local Wi-Fi</p>
            </div>

            <form id="setupForm" onsubmit="handleSave(event)">
                <div class="form-group">
                    <label for="apiKey">Gemini API Key</label>
                    <div class="input-row">
                        <input type="password" id="apiKey" placeholder="AIzaSy..." required autocomplete="off" autocorrect="off" autocapitalize="off" />
                        <button type="button" class="paste-btn" onclick="pasteKey()">Paste</button>
                    </div>
                    <a href="https://aistudio.google.com/app/apikey" target="_blank" class="help-link">Get an API key at Google AI Studio &rarr;</a>
                </div>

                <div class="form-group">
                    <label for="model">Model</label>
                    <select id="model">
                        <option value="models/gemini-3.8-live" selected>gemini-3.8-live (Recommended)</option>
                    </select>
                </div>

                <div class="form-group">
                    <label for="voice">Voice Persona</label>
                    <select id="voice">
                        <option value="Puck" ${if (currentVoice == "Puck") "selected" else ""}>Puck (Upbeat & Energetic)</option>
                        <option value="Charon" ${if (currentVoice == "Charon") "selected" else ""}>Charon (Informative & Steady)</option>
                        <option value="Aoede" ${if (currentVoice == "Aoede") "selected" else ""}>Aoede (Breezy & Conversational)</option>
                        <option value="Kore" ${if (currentVoice == "Kore") "selected" else ""}>Kore (Firm & Composed)</option>
                        <option value="Fenrir" ${if (currentVoice == "Fenrir") "selected" else ""}>Fenrir (Excitable & Bold)</option>
                    </select>
                </div>

                <button type="submit" id="submitBtn" class="submit-btn">Save to Portal</button>
            </form>
        </div>

        <div id="successView" class="success-box">
            <div class="success-icon">✓</div>
            <div class="success-title">Configuration Saved!</div>
            <div class="success-desc">Your Portal is now connected and ready. You can close this page.</div>
        </div>
    </div>

    <script>
        const token = new URLSearchParams(window.location.search).get('token');

        async function pasteKey() {
            try {
                const text = await navigator.clipboard.readText();
                if (text) document.getElementById('apiKey').value = text.trim();
            } catch (err) {
                alert('Please paste directly into the text field.');
            }
        }

        async function handleSave(e) {
            e.preventDefault();
            const btn = document.getElementById('submitBtn');
            btn.disabled = true;
            btn.textContent = 'Saving...';

            const payload = {
                token: token,
                apiKey: document.getElementById('apiKey').value.trim(),
                model: document.getElementById('model').value,
                voice: document.getElementById('voice').value
            };

            try {
                const res = await fetch('/api/setup?token=' + token, {
                    method: 'POST',
                    headers: { 'Content-Type': 'application/json' },
                    body: JSON.stringify(payload)
                });
                if (res.ok) {
                    document.getElementById('formView').style.display = 'none';
                    document.getElementById('successView').style.display = 'block';
                } else {
                    const data = await res.json();
                    alert(data.message || 'Error saving configuration');
                    btn.disabled = false;
                    btn.textContent = 'Save to Portal';
                }
            } catch (err) {
                alert('Connection error: ' + err.message);
                btn.disabled = false;
                btn.textContent = 'Save to Portal';
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }

    private fun buildExportHtml(messages: List<ChatMessage>): String {
        val sessionGroups = messages.groupBy { it.sessionIndex }
        val transcriptHtml = sessionGroups.entries.joinToString("\n") { (sessionIdx, sessionMsgs) ->
            val firstTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sessionMsgs.first().timestampMs))
            val lastTime = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(sessionMsgs.last().timestampMs))
            val divider = """
                <div style="text-align: center; margin: 24px 0 12px 0;">
                    <span style="background: #21262d; border: 1px solid var(--border); padding: 4px 12px; border-radius: 12px; font-size: 12px; color: var(--muted); font-weight: 600;">
                        💬 Session $sessionIdx ($firstTime - $lastTime) · ${sessionMsgs.size} turns
                    </span>
                </div>
            """.trimIndent()

            val msgsHtml = sessionMsgs.joinToString("\n") { msg ->
                val isUser = msg.sender == dev.portalgemini.model.ChatSender.USER
                val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(msg.timestampMs))
                val senderClass = if (isUser) "user" else "gemini"
                val senderLabel = if (isUser) "You" else "Gemini"
                val text = msg.text.trim()
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                """
                <div class="message $senderClass">
                    <div class="msg-header">
                        <span class="sender">$senderLabel</span>
                        <span class="time">$time</span>
                    </div>
                    <div class="msg-body">$text</div>
                </div>
                """.trimIndent()
            }
            "$divider\n$msgsHtml"
        }

        return """
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>Gemini Live · Export Conversation</title>
    <style>
        :root {
            --bg: #0d1117;
            --card: #161b22;
            --border: #30363d;
            --text: #e6edf3;
            --muted: #8b949e;
            --blue: #38bdf8;
            --purple: #c084fc;
            --green: #4ade80;
        }
        * { box-sizing: border-box; margin: 0; padding: 0; }
        body {
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            background: var(--bg);
            color: var(--text);
            padding: 24px 16px;
            display: flex;
            justify-content: center;
        }
        .container { max-width: 680px; width: 100%; }
        .header {
            border-bottom: 1px solid var(--border);
            padding-bottom: 20px;
            margin-bottom: 20px;
        }
        .header h1 {
            font-size: 24px;
            font-weight: 700;
            background: linear-gradient(135deg, var(--blue), var(--purple));
            -webkit-background-clip: text;
            -webkit-text-fill-color: transparent;
            margin-bottom: 4px;
        }
        .header p { color: var(--muted); font-size: 13px; }
        .actions-card {
            background: var(--card);
            border: 1px solid var(--border);
            border-radius: 12px;
            padding: 16px;
            margin-bottom: 24px;
        }
        .actions-label { font-size: 12px; font-weight: 600; color: var(--muted); text-transform: uppercase; margin-bottom: 12px; }
        .buttons-grid {
            display: grid;
            grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
            gap: 10px;
        }
        .btn {
            background: #21262d;
            border: 1px solid var(--border);
            color: var(--text);
            padding: 10px 12px;
            border-radius: 8px;
            font-size: 13px;
            font-weight: 600;
            cursor: pointer;
            text-align: center;
            text-decoration: none;
            display: inline-block;
            transition: all 0.2s;
        }
        .btn:hover {
            border-color: var(--blue);
            color: var(--blue);
        }
        .btn.primary {
            background: linear-gradient(135deg, #2563eb, #7c3aed);
            border: none;
            color: white;
        }
        .message {
            margin-bottom: 14px;
            padding: 12px 16px;
            border-radius: 10px;
            border: 1px solid var(--border);
            background: var(--card);
        }
        .message.user { border-left: 3px solid var(--blue); }
        .message.gemini { border-left: 3px solid var(--purple); }
        .msg-header {
            display: flex;
            justify-content: space-between;
            font-size: 12px;
            font-weight: 600;
            margin-bottom: 6px;
        }
        .user .sender { color: var(--blue); }
        .gemini .sender { color: var(--purple); }
        .time { color: var(--muted); font-weight: normal; font-size: 11px; }
        .msg-body { font-size: 14px; line-height: 1.5; white-space: pre-wrap; word-break: break-word; }
        #copyFeedback {
            display: none;
            color: var(--green);
            font-size: 12px;
            margin-top: 8px;
            text-align: center;
        }
    </style>
</head>
<body>
    <div class="container">
        <div class="header">
            <h1>Gemini Live Conversation</h1>
            <p>${messages.size} turns recorded · Exported from Meta Portal</p>
        </div>

        <div class="actions-card">
            <div class="actions-label">Export & Download Options</div>
            <div class="buttons-grid">
                <button class="btn primary" onclick="copyAll()">📋 Copy All</button>
                <a class="btn" href="/export/download?format=md&token=' + token + '" id="dlMd">📄 .md (Markdown)</a>
                <a class="btn" href="/export/download?format=txt&token=' + token + '" id="dlTxt">📝 .txt (Text)</a>
                <a class="btn" href="/export/download?format=html&token=' + token + '" id="dlHtml">🌐 .html (HTML)</a>
            </div>
            <div id="copyFeedback">✓ Copied conversation to clipboard!</div>
        </div>

        <div class="transcript">
            $transcriptHtml
        </div>
    </div>

    <script>
        const token = new URLSearchParams(window.location.search).get('token');
        document.getElementById('dlMd').href = '/export/download?format=md&token=' + token;
        document.getElementById('dlTxt').href = '/export/download?format=txt&token=' + token;
        document.getElementById('dlHtml').href = '/export/download?format=html&token=' + token;

        async function copyAll() {
            try {
                const res = await fetch('/export/download?format=md&token=' + token);
                const text = await res.text();
                await navigator.clipboard.writeText(text);
                const fb = document.getElementById('copyFeedback');
                fb.style.display = 'block';
                setTimeout(() => { fb.style.display = 'none'; }, 3000);
            } catch (err) {
                alert('Could not copy to clipboard: ' + err.message);
            }
        }
    </script>
</body>
</html>
        """.trimIndent()
    }
}
