package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable internal fun DiagnosticTools(s:ProxySnapshot,provider:WebViewStatus,checking:Boolean,local:LocalCheckResult?,onLocal:()->Unit,onCancel:()->Unit,onRefresh:()->Unit){
    val advice=diagnosticAdvice(s)
    if(advice.isNotEmpty())SectionCard(stringResource(R.string.tools_advice),Modifier.testTag("diagnostics.advice")){
        advice.forEach{Text(stringResource(adviceResource(it)),style=MaterialTheme.typography.bodySmall)}
    }
    SectionCard(stringResource(R.string.tools_local),Modifier.testTag("diagnostics.local")){
        Text(stringResource(R.string.tools_local_notice),style=MaterialTheme.typography.bodySmall)
        if(checking)LinearProgressIndicator(Modifier.fillMaxWidth())
        local?.let{
            Text(stringResource(localOutcomeResource(it.outcome)),Modifier.testTag("diagnostics.local.result"))
            it.millis?.let{ms->Text(stringResource(R.string.tools_local_time,ms),style=MaterialTheme.typography.bodySmall)}
        }
        if(checking)OutlinedButton(onClick=onCancel){Text(stringResource(R.string.update_cancel))}
        else OutlinedButton(onClick=onLocal,modifier=Modifier.testTag("diagnostics.local.check")){Text(stringResource(R.string.tools_local_run))}
    }
    SectionCard(stringResource(R.string.journal_webview),Modifier.testTag("diagnostics.webview")){
        Text(stringResource(providerStateResource(provider.state)),Modifier.testTag("webview.state"))
        provider.packageName?.let{Text(it,Modifier.testTag("webview.package"),style=MaterialTheme.typography.bodySmall)}
        provider.version?.let{Text(stringResource(R.string.tools_webview_version,it),Modifier.testTag("webview.version"))}
        if(provider.state==ProviderState.AVAILABLE)Text(stringResource(R.string.tools_webview_channel,stringResource(channelResource(provider.channel))),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.tools_webview_notice),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick=onRefresh,modifier=Modifier.testTag("webview.refresh")){Text(stringResource(R.string.tools_refresh))}
    }
}
internal fun providerStateResource(state:ProviderState)=when(state){ProviderState.UNCHECKED->R.string.tools_webview_unchecked;ProviderState.AVAILABLE->R.string.tools_webview_available;ProviderState.MISSING->R.string.tools_webview_missing;ProviderState.QUERY_FAILED->R.string.tools_webview_failed}
internal fun channelResource(channel:ProviderChannel)=when(channel){ProviderChannel.STABLE->R.string.tools_channel_stable;ProviderChannel.BETA->R.string.tools_channel_beta;ProviderChannel.DEV->R.string.tools_channel_dev;ProviderChannel.CANARY->R.string.tools_channel_canary;ProviderChannel.SYSTEM->R.string.tools_channel_system;ProviderChannel.UNKNOWN->R.string.unknown}
internal fun localOutcomeResource(outcome:LocalCheckOutcome)=when(outcome){LocalCheckOutcome.AVAILABLE->R.string.tools_local_available;LocalCheckOutcome.REFUSED->R.string.tools_local_refused;LocalCheckOutcome.TIMEOUT->R.string.error_timeout;LocalCheckOutcome.INVALID_PORT->R.string.error_port;LocalCheckOutcome.IO_ERROR->R.string.tools_local_failed}
internal fun adviceResource(advice:DiagnosticAdvice)=when(advice){
    DiagnosticAdvice.CAPTIVE_PORTAL->R.string.advice_portal;DiagnosticAdvice.NO_NETWORK->R.string.advice_network
    DiagnosticAdvice.SECRET->R.string.advice_secret;DiagnosticAdvice.PORT->R.string.advice_port
    DiagnosticAdvice.INCOMPLETE_HANDSHAKE->R.string.advice_incomplete_handshake
    DiagnosticAdvice.HANDSHAKE_FORMAT->R.string.advice_handshake_format
    DiagnosticAdvice.DNS->R.string.advice_dns;DiagnosticAdvice.TLS->R.string.advice_tls
    DiagnosticAdvice.ROUTE->R.string.advice_route;DiagnosticAdvice.LIMIT->R.string.advice_limit
    DiagnosticAdvice.RETRY->R.string.advice_retry;DiagnosticAdvice.STORAGE->R.string.advice_storage
}
