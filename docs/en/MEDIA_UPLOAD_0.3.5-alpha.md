# TGWS-Android 0.3.5-alpha — Media upload investigation

This is a diagnostic work-in-progress, not proof of a media fix.

Owner-reported reproduction: Android 16 + LTE, Cloudflare Worker WSS relay,
Telegram text and SD photos work while HD photo upload remains pending.

The PR adds sanitized Worker WebSocket close reasons (including BYTE_LIMIT,
QUEUE_LIMIT, WRITE_TIMEOUT and TCP_ERROR), typed Android close observation,
per-route cumulative sent/received counters, and a CLOSED state instead of stale
READY for finished sessions. Tests cover binary streams exceeding 16 MiB under
a raised **test-only** limit.

The deployed Worker remains unchanged until explicitly redeployed; its default
16 MiB per-session bidirectional budget remains in place. Cloudflare Workers
WebSocket.send does not expose drain/backpressure; increasing limits without
bounded flow control could increase memory pressure. Final SD/HD/voice/video
Telegram end-to-end testing on physical Android 16+ phones belongs to the owner.

See [Russian engineering and verification plan](../MEDIA_UPLOAD_0.3.5-alpha.md).
