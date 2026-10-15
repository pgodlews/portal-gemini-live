# Security and privacy

## Credentials

Supply your own Gemini API key at runtime. Do not add keys to source, build configuration, test fixtures, issues, screenshots, or commits. Prefer entering the key directly in the app's Settings. ADB intent-based configuration is supported for development, but command arguments can remain in shell history and device diagnostics.

The key is stored in private Android `SharedPreferences`, without additional application-level encryption. App backup is disabled. This does not protect data on rooted or otherwise compromised devices. Do not share app data directories or device backups.

## Local setup and export

The QR dialog starts a temporary HTTP server listening on available network interfaces, on a port between 8080 and 8090. A random session token is required for setup and export requests. URLs and QR images grant access while that server is active.

HTTP is unencrypted. Use a trusted private network, do not expose this port to the internet, and close the dialog when finished. The token does not protect against someone who can observe network traffic. Responses disable caching and referrer forwarding, but downloaded transcripts and browser history remain under the recipient's control. Previously opened requests may still be in progress when a dialog closes.

## Diagnostics

The Live API client omits raw protocol messages, prompts, transcripts, server close reasons, and API response bodies from logs. API keys and local server tokens are not intentionally logged. Connection errors show status codes or generic descriptions; inspect settings and connectivity rather than publishing raw device logs.

Review all diagnostics manually before sharing. Automated secret scanners cannot detect every credential or personal detail.

## Reporting

Use GitHub private vulnerability reporting if the repository has it enabled. Otherwise, ask the maintainer for a private reporting channel without posting exploit details or credentials in a public issue. Rotate any credential that has been exposed; removing it from a file does not invalidate it.
