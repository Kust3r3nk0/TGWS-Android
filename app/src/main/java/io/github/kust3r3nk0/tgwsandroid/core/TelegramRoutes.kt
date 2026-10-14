package io.github.kust3r3nk0.tgwsandroid.core

/**
 * Default Telegram DC routes mirrored from Flowseal/tg-ws-proxy proxy/utils.py.
 * Keep these mappings in parity tests whenever the upstream baseline changes.
 */
internal object TelegramRoutes {
    private val defaultIps = mapOf(
        1 to "149.154.175.50",
        2 to "149.154.167.51",
        3 to "149.154.175.100",
        4 to "149.154.167.91",
        5 to "149.154.171.5",
        203 to "91.105.192.100",
    )

    fun ip(dc: Int): String? = defaultIps[dc]

    fun webSocketDomains(dc: Int, isMedia: Boolean): List<String> {
        if (!defaultIps.containsKey(dc)) return emptyList()
        val hostnameDc = if (dc == 203) 2 else dc
        val primary = "kws$hostnameDc.web.telegram.org"
        return if (isMedia) listOf("kws$hostnameDc-1.web.telegram.org", primary)
        else listOf(primary)
    }
}
