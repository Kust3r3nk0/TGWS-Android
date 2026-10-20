package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection

internal fun settingsError(issue: SettingsIssue): Int = when (issue) {
    SettingsIssue.INVALID_CONFIGURATION -> R.string.error_configuration
    SettingsIssue.SECRET_UNAVAILABLE -> R.string.error_secret_unavailable
    SettingsIssue.WRITE_FAILED -> R.string.error_storage
}

internal fun statusResource(phase: ProxyPhase): Int = when (phase) {
    ProxyPhase.STOPPED -> R.string.status_stopped
    ProxyPhase.STARTING -> R.string.status_starting
    ProxyPhase.LISTENING -> R.string.status_listening
    ProxyPhase.ACTIVE -> R.string.status_active
    ProxyPhase.ERROR -> R.string.status_error
    ProxyPhase.STOPPING -> R.string.status_stopping
}

internal fun issueResource(issue: ProxyIssue): Int = when (issue) {
    ProxyIssue.CONFIGURATION -> R.string.error_configuration
    ProxyIssue.PORT -> R.string.error_port
    ProxyIssue.SECRET_UNAVAILABLE -> R.string.error_secret_unavailable
    ProxyIssue.STORAGE -> R.string.error_storage
    ProxyIssue.HANDSHAKE -> R.string.error_handshake
    ProxyIssue.WSS_HANDSHAKE -> R.string.error_wss_handshake
    ProxyIssue.TLS -> R.string.error_tls
    ProxyIssue.TCP -> R.string.error_tcp
    ProxyIssue.RELAY -> R.string.error_relay
    ProxyIssue.RETRY_LATER -> R.string.retry_later
    ProxyIssue.SESSION -> R.string.error_session
    ProxyIssue.CAPACITY -> R.string.error_capacity
    ProxyIssue.TIMEOUT -> R.string.error_timeout
    ProxyIssue.NETWORK_CHANGED -> R.string.network_changed
    ProxyIssue.SERVICE_START -> R.string.error_service_start
    ProxyIssue.HTTP2 -> R.string.error_http2
    ProxyIssue.DNS -> R.string.error_dns
    ProxyIssue.NETWORK -> R.string.error_network
}

internal fun rejectionResource(reason: HandshakeRejection): Int = when (reason) {
    HandshakeRejection.INVALID_LENGTH -> R.string.rejection_length
    HandshakeRejection.RESERVED_PREFIX -> R.string.rejection_prefix
    HandshakeRejection.SECRET_OR_TRANSPORT -> R.string.rejection_secret_or_transport
    HandshakeRejection.UNSUPPORTED_DC -> R.string.rejection_dc
    HandshakeRejection.FAKE_TLS -> R.string.rejection_fake_tls
}
