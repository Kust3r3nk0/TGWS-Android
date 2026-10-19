package io.github.kust3r3nk0.tgwsandroid

/** Explicit preview/export contains enums and counters, never configuration or network addresses. */
internal object DiagnosticReport {
    fun render(value:ProxySnapshot):String=buildString {
        append("{\n  \"schema\": 1,\n  \"phase\": \"").append(value.phase.name).append("\",\n")
        append("  \"access\": \"").append(value.network.kind.name).append("\",\n")
        append("  \"validated\": ").append(value.network.validated).append(",\n")
        append("  \"captive_portal\": ").append(value.network.captivePortal).append(",\n")
        append("  \"ipv4\": ").append(value.network.ipv4).append(",\n  \"ipv6\": ").append(value.network.ipv6).append(",\n")
        append("  \"nat64_present\": ").append(value.network.nat64Prefix!=null).append(",\n")
        append("  \"radio\": \"").append(value.network.operator.radio.name).append("\",\n")
        append("  \"sessions\": ").append(value.sessions).append(",\n  \"pending\": ").append(value.pending).append(",\n")
        append("  \"sent\": ").append(value.sent).append(",\n  \"received\": ").append(value.received).append(",\n")
        append("  \"routes\": [")
        value.routes.forEachIndexed{index,route->
            if(index>0)append(',')
            append("\n    {\"id\": ").append(route.id).append(", \"dc\": ").append(route.dc)
                .append(", \"media\": ").append(route.media).append(", \"kind\": \"").append(route.kind.name)
                .append("\", \"state\": \"").append(route.status.name).append("\", \"stage\": \"").append(route.stage.name)
                .append("\", \"issue\": \"").append(route.issue?.name?:"NONE").append("\", \"checked_at\": ").append(route.checkedAtMillis)
                .append(", \"setup_ms\": ").append(route.setupMillis?:"null").append(", \"attempts\": ").append(route.attempts)
                .append(", \"dns_ms\": ").append(route.timings?.dnsMillis?:"null")
                .append(", \"tcp_ms\": ").append(route.timings?.tcpMillis?:"null")
                .append(", \"tls_ms\": ").append(route.timings?.tlsMillis?:"null")
                .append(", \"http_ms\": ").append(route.timings?.httpMillis?:"null")
                .append(", \"route_sent\": ").append(route.sent).append(", \"route_received\": ").append(route.received)
                .append(", \"close_code\": ").append(route.closeCode?:"null")
                .append(", \"close_reason\": ").append(route.closeCategory?.name?.let{ "\"$it\"" }?:"null").append('}')
        }
        append("\n  ]\n}\n")
    }
}
