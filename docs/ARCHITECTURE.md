# Architecture

## Components

| Component | Responsibility |
|---|---|
| `MainActivity` | Permissions, preferences, session lifecycle, UI state, and local setup/export server lifecycle |
| `GeminiLiveClient` | Gemini Live WebSocket setup, audio/video messages, transcripts, and interruption callbacks |
| `AudioRecorder` | 16 kHz mono PCM capture, supported audio effects, mute gating, and local amplitude |
| `AudioPlayer` | Queued 24 kHz mono PCM playback and interruption flushing |
| `PortalCameraManager` | Camera2 preview, JPEG encoding, frame pacing, and camera mode |
| `PortalHttpServer` | Token-protected local HTTP setup and transcript downloads |
| `TelemetryManager` | Rolling throughput, latency, network information, and fixed cost estimates |
| Compose UI | Settings, camera preview, animated orb, telemetry, subtitles, and history |

Source files are under `app/src/main/java/dev/portalgemini/`.

## Data flow

```mermaid
flowchart LR
    Mic[Microphone] --> Recorder[AudioRecorder]
    Recorder -->|Unmuted audio| Client[GeminiLiveClient]
    Camera[Camera2] --> Preview[Local preview]
    Camera -->|Enabled camera frames| Client
    Client <-->|TLS WebSocket| Gemini[Gemini Live API]
    Client --> Player[AudioPlayer]
    Player --> Speakers[Speakers]
    Client --> History[In-memory conversation history]
    History --> Export[Local HTTP export]
    Settings[Settings or phone setup] --> Prefs[Private SharedPreferences]
    Prefs --> Client
```

The client uses the `v1alpha` `BidiGenerateContent` endpoint. The API key is a query parameter on the TLS WebSocket request. The setup message selects audio responses, a voice and model, transcription, and an optional system prompt. Both text and binary WebSocket frames are decoded as JSON.

Audio and camera sends are gated on the connected state. Muting audio stops transmission but retains local capture for amplitude visualization. Camera standby stops frame transmission while retaining the local preview. Incoming interruption events flush playback. Transcript state remains in memory until exported or cleared.

## Live session lifecycle

```mermaid
sequenceDiagram
    autonumber
    actor User
    participant Main as MainActivity
    participant Rec as AudioRecorder
    participant Cam as PortalCameraManager
    participant Client as GeminiLiveClient
    participant Player as AudioPlayer
    participant API as Gemini Live API

    User->>Main: Start Gemini Live
    Main->>Cam: Set mode (STANDBY if voice-only, else CONTINUOUS)
    Main->>Rec: start()
    Main->>Client: connect(config)
    Client->>API: Open TLS WebSocket
    Client-->>Main: INITIALIZING
    Client->>API: Setup (model, voice, transcription, system prompt)
    API-->>Client: setupComplete
    Client-->>Main: CONNECTED

    loop Unmuted and connected
        Rec->>Client: 16 kHz PCM chunk
        Client->>API: realtime_input audio
    end
    Note over Rec: Muted: capture and local amplitude continue, nothing is sent

    loop Camera streaming and connected
        Cam->>Client: JPEG frame
        Client->>API: realtime_input image
    end

    API-->>Client: serverContent audio and transcripts
    Client-->>Player: 24 kHz PCM queued for playback
    API-->>Client: serverContent interrupted
    Client-->>Player: Flush queued playback
    API-->>Client: serverContent turnComplete
    Client-->>Main: Update turn state and telemetry

    User->>Main: Stop
    Main->>Client: disconnect()
    Main->>Rec: stop()
    Main->>Player: interrupt()
```

## Connection states

```mermaid
stateDiagram-v2
    [*] --> DISCONNECTED
    DISCONNECTED --> CONNECTING: connect()
    CONNECTING --> INITIALIZING: WebSocket open, setup sent
    INITIALIZING --> CONNECTED: setupComplete
    CONNECTING --> ERROR: connection failure
    INITIALIZING --> ERROR: connection failure
    CONNECTED --> ERROR: connection failure
    CONNECTING --> DISCONNECTED: closed
    INITIALIZING --> DISCONNECTED: closed
    CONNECTED --> DISCONNECTED: closed or disconnect()
    ERROR --> CONNECTING: connect()
    ERROR --> DISCONNECTED: disconnect()
```

Audio and camera sends are gated on `CONNECTED`. Reconnect attempts that arrive while the client is `CONNECTING`, `INITIALIZING`, or `CONNECTED` are ignored.

## Camera modes

```mermaid
stateDiagram-v2
    [*] --> STANDBY
    STANDBY --> CONTINUOUS: toggleStreaming()
    CONTINUOUS --> STANDBY: toggleStreaming()
    STANDBY --> AUTO: triggerAutoCamera()
    AUTO --> STANDBY: triggerAutoCamera()
    note right of STANDBY : Local preview only, no frames sent
    note right of AUTO : Frames sent while active
    note right of CONTINUOUS : Frames sent at the configured rate
```

Preview runs in every mode. Only `STANDBY` stops frame transmission. The mode is set when a session starts and changed from the camera controls.

## Local setup and export

```mermaid
sequenceDiagram
    autonumber
    actor Phone as Phone browser
    participant Portal as MainActivity
    participant Server as PortalHttpServer
    participant Prefs as Private SharedPreferences

    rect rgb(240, 240, 240)
    Note over Portal,Server: Setup mode, random token in the QR link, plain HTTP on a trusted network
    Portal->>Server: Start server, show QR code
    Phone->>Server: GET setup page with token
    Server-->>Phone: Setup form, Cache-Control no-store
    Phone->>Server: POST /api/setup with token
    Server->>Portal: onConfigReceived(key, model, voice)
    Portal->>Prefs: Save settings
    end

    rect rgb(240, 240, 240)
    Note over Portal,Server: Export mode, snapshot of in-memory history
    Portal->>Server: Start export server with message snapshot, show QR code
    Phone->>Server: GET /export with token
    Server-->>Phone: Export page with download links
    Phone->>Server: GET /export/download?format=md, txt, or html, with token
    Server-->>Phone: Transcript file
    end
```

Setup and export each run on their own server instance with a full random UUID token. Both transfers are unencrypted HTTP, so use them only on a trusted private network.

## Configuration and privacy

Settings are persisted in `gemini_prefs` using private Android `SharedPreferences`. The key is supplied at runtime and is not included in the build. Android app backup is disabled. The local setup page does not prefill the stored key.

Setup and export use separate instances of the local HTTP server. Each instance gets a full random UUID token, required in the request URL. Responses include `Cache-Control: no-store` and `Referrer-Policy: no-referrer`. The connection is plain HTTP, so this feature requires a trusted private network. See [security guidance](../SECURITY.md).

Diagnostics omit key fragments, tokens, prompts, transcript text, raw protocol messages, and raw connection errors. Generic connection errors retain an HTTP status when available.

## Platform considerations

The app uses Camera2 directly for Portal camera compatibility. CameraX dependencies remain in the build but are not the active capture path. The app targets Android API 29 and compiles against API 35; behavior on other firmware and devices needs device testing.

Microphone capture and local HTTP requests use IO coroutines. Camera callbacks and JPEG encoding use a background handler thread. WebSocket callbacks update flows and invoke audio/transcript callbacks. Audio playback uses a queue that is currently unbounded. Hardware echo cancellation depends on device support.
