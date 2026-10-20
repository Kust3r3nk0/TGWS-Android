package io.github.kust3r3nk0.tgwsandroid

/** Explicit preview/export contains enums and counters, never configuration or network addresses. */
internal object DiagnosticReport {
    fun render(value:ProxySnapshot,history:DiagnosticHistoryState=DiagnosticHistoryState(),
        provider:WebViewStatus=WebViewStatus(),version:String?=null,versionCode:Long?=null,api:Int?=null):String=buildString {
        append("{\n  \"schema\": 3,\n  \"phase\": \"").append(value.phase.name).append("\",\n")
        val safeVersion=version?.takeIf{it.length<=80 && it.matches(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:-[A-Za-z0-9.-]+)?"))}
        append("  \"app_version\": ").append(json(safeVersion)).append(",\n  \"version_code\": ").append(versionCode?:"null")
        append(",\n  \"android_api\": ").append(api?:"null").append(",\n")
        append("  \"issue\": ").append(json(value.issue?.name)).append(",\n")
        append("  \"rejected_handshakes\": ").append(value.rejectedHandshakes).append(",\n")
        append("  \"last_rejection\": ").append(json(value.lastHandshakeRejection?.name)).append(",\n")
        append("  \"access\": \"").append(value.network.kind.name).append("\",\n")
        append("  \"validated\": ").append(value.network.validated).append(",\n")
        append("  \"captive_portal\": ").append(value.network.captivePortal).append(",\n")
        append("  \"ipv4\": ").append(value.network.ipv4).append(",\n  \"ipv6\": ").append(value.network.ipv6).append(",\n")
        append("  \"nat64_present\": ").append(value.network.nat64Prefix!=null).append(",\n")
        append("  \"radio\": \"").append(value.network.operator.radio.name).append("\",\n")
        append("  \"sessions\": ").append(value.sessions).append(",\n  \"pending\": ").append(value.pending).append(",\n")
        append("  \"sent\": ").append(value.sent).append(",\n  \"received\": ").append(value.received).append(",\n")
        append("  \"routes\": [")
        value.routes.takeLast(32).forEachIndexed{index,route->
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
                .append(", \"close_reason\": ").append(json(route.closeCategory?.name))
                .append(", \"upgrade\": ").append(upgradeJson(route.upgrade))
                .append(", \"retry_ms\": ").append(route.retryInMillis?:"null").append('}')
        }
        append("\n  ],\n  \"webview\": ").append(providerJson(provider))
        append(",\n  \"events\": [")
        history.events.takeLast(DiagnosticHistory.MAX_EVENTS).forEachIndexed{index,e->
            if(index>0)append(',')
            append("\n    {\"time\": ").append(e.timeMillis).append(", \"last_time\": ").append(e.lastTimeMillis)
            append(", \"level\": ").append(json(e.level.name)).append(", \"category\": ").append(json(e.category.name))
            append(", \"event\": ").append(json(e.kind.name)).append(", \"occurrences\": ").append(e.occurrences)
            append(", \"issue\": ").append(json(e.issue?.name)).append(", \"phase\": ").append(json(e.phase?.name))
            append(", \"transport\": ").append(json(e.transport?.name)).append(", \"rejection\": ").append(json(e.rejection?.name))
            append(", \"rejected_total\": ").append(e.rejected?:"null")
            append(", \"network\": ").append(json(e.network?.name)).append(", \"validated\": ").append(e.validated?:"null")
            append(", \"ipv4\": ").append(e.ipv4?:"null").append(", \"ipv6\": ").append(e.ipv6?:"null")
            e.route?.let{r->
                append(", \"route\": {\"id\": ").append(r.id).append(", \"dc\": ").append(r.dc).append(", \"media\": ").append(r.media)
                append(", \"stage\": ").append(json(r.stage.name)).append(", \"attempts\": ").append(r.attempts)
                append(", \"setup_ms\": ").append(r.setupMillis?:"null").append(", \"dns_ms\": ").append(r.dnsMillis?:"null")
                append(", \"tcp_ms\": ").append(r.tcpMillis?:"null").append(", \"tls_ms\": ").append(r.tlsMillis?:"null")
                append(", \"http_ms\": ").append(r.httpMillis?:"null").append(", \"sent\": ").append(r.sent).append(", \"received\": ").append(r.received)
                append(", \"close_code\": ").append(r.closeCode?:"null").append(", \"close_reason\": ").append(json(r.closeCategory?.name))
                append(", \"upgrade\": ").append(upgradeJson(r.upgrade)).append(", \"retry_ms\": ").append(r.retryInMillis?:"null").append('}')
            }
            e.session?.let{s->
                append(", \"session\": {\"id\": ").append(s.id).append(", \"reason\": ").append(json(s.reason.name))
                append(", \"established\": ").append(s.established).append(", \"duration_ms\": ").append(s.durationMillis)
                append(", \"sent\": ").append(s.sent).append(", \"received\": ").append(s.received)
                append(", \"close_code\": ").append(s.peerClose?.code?:"null").append(", \"close_reason\": ").append(json(s.peerClose?.category?.name))
                append(", \"upgrade\": ").append(upgradeJson(s.upgrade)).append('}')
            }
            e.provider?.let{append(", \"webview\": ").append(providerJson(it))}
            e.localCheck?.let{append(", \"local_check\": ").append(json(it.outcome.name)).append(", \"local_check_ms\": ").append(it.millis?:"null")}
            append('}')
        }
        append("\n  ]\n}\n")
    }

    private fun upgradeJson(d:io.github.kust3r3nk0.tgwsandroid.core.HttpUpgradeDiagnostic?):String =
        if(d==null)"null" else "{\"status\": ${d.status?:"null"}, \"failure\": ${json(d.failure?.name)}, \"relay_reason\": ${json(d.relayReason?.name)}, \"retry_after_ms\": ${d.retryAfterMillis?:"null"}}"

    private fun providerJson(p:WebViewStatus):String {
        // Re-sanitize even externally constructed/test snapshots before serializing.
        val safe=if(p.state==ProviderState.AVAILABLE)WebViewStatus.available(p.packageName,p.version,p.versionCode?:0)else WebViewStatus(p.state)
        return "{\"state\": ${json(safe.state.name)}, \"package\": ${json(safe.packageName)}, \"version\": ${json(safe.version)}, \"version_code\": ${safe.versionCode?:"null"}, \"channel_by_package\": ${json(safe.channel.name)}, \"latest_verified\": false, \"embedded\": false}"
    }
    private fun json(value:String?):String = value?.let { text->buildString {
        append('"');for(c in text)when(c){'"'->append("\\\"");'\\'->append("\\\\");'\n'->append("\\n");'\r'->append("\\r");'\t'->append("\\t");else->if(c<' ')append("\\u%04x".format(c.code))else append(c)};append('"')
    }}?:"null"
}
