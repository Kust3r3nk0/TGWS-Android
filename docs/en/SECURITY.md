# Security policy

## Supported versions

| Version | Security support |
| --- | --- |
| 0.6.3 Debug candidate | Best-effort only; not production-ready |
| Earlier versions | Not supported |

## Reporting vulnerabilities

**Please do not post exploitable vulnerabilities, live MTProto secrets, or private logs in a public Issue.**

If the GitHub repository has private vulnerability reporting enabled, use **Security → Report a vulnerability**. Otherwise, contact the repository owner privately using their GitHub profile and request a secure reporting channel before sharing details.

Do not publish working exploit code or credentials until maintainers can assess the issue.

## Security principles

- Local listener must remain bound to loopback by default.
- Treat proxy secrets and configuration as sensitive: redact logs, no plaintext export by default.
- Explicit TLS certificate and hostname validation; document transport-specific exceptions.
- Bounded buffers, connection concurrency and timeouts; check cancellation and resource cleanup.
- Prefer transparent CI builds, reproducible toolchains and dependency updates.

**Security has not been externally audited.**

[Русская версия](../../SECURITY.md)
