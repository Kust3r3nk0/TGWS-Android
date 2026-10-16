package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.kust3r3nk0.tgwsandroid.core.*

internal data class TransportDraft(val mode:TransportMode=TransportMode.DIRECT,val fakeDomain:String="",
    val domains:String="",val workers:String="",val h2:Boolean=false){
    fun settings():TransportSettings {
        fun lines(text:String)=text.lineSequence().map{it.trim()}.filter{it.isNotEmpty()}.toList()
        return TransportSettings(mode,fakeDomain.trim().lowercase(java.util.Locale.ROOT),
            lines(domains).map{it.lowercase(java.util.Locale.ROOT)},lines(workers),h2 && mode==TransportMode.AUTO_CLOUDFLARE)
    }
    override fun toString()="TransportDraft(redacted)"
    companion object {fun from(value:TransportSettings)=TransportDraft(value.mode,value.fakeTlsDomain,
        value.cloudflareDomains.joinToString("\n"),value.workerUrls.joinToString("\n"),value.http2Media)}
}

@Composable internal fun TransportEditor(value:TransportDraft,onChange:(TransportDraft)->Unit,editable:Boolean,reveal:Boolean,relayUrl:String){
    Text(stringResource(R.string.transport_settings),style=MaterialTheme.typography.titleMedium)
    for(mode in TransportMode.entries){
        Row(Modifier.fillMaxWidth().clickable(enabled=editable){onChange(value.copy(mode=mode))},verticalAlignment=Alignment.CenterVertically){
            RadioButton(selected=value.mode==mode,onClick={onChange(value.copy(mode=mode))},enabled=editable)
            Text(stringResource(when(mode){TransportMode.DIRECT->R.string.mode_direct;TransportMode.RELAY_ONLY->R.string.mode_relay;TransportMode.AUTO_CLOUDFLARE->R.string.mode_cf}))
        }
    }
    if(value.mode==TransportMode.AUTO_CLOUDFLARE){
        Text(stringResource(R.string.cf_order),style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(value=value.domains,onValueChange={onChange(value.copy(domains=it.take(2048),h2=value.h2 && it.isNotBlank()))},
            modifier=Modifier.fillMaxWidth(),label={Text(stringResource(R.string.cf_domains))},enabled=editable,minLines=2,
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
        OutlinedTextField(value=value.workers,onValueChange={onChange(value.copy(workers=it.take(8199)))},modifier=Modifier.fillMaxWidth(),
            label={Text(stringResource(R.string.worker_endpoints))},enabled=editable,minLines=2,
            visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
        if(relayUrl.isNotEmpty())TextButton(enabled=editable,onClick={
            val urls=(value.workers.lineSequence().filter{it.isNotBlank()}.toList()+relayUrl).distinct()
            onChange(value.copy(workers=urls.joinToString("\n")))
        }){Text(stringResource(R.string.use_saved_relay))}
        Row(verticalAlignment=Alignment.CenterVertically){Switch(checked=value.h2,onCheckedChange={onChange(value.copy(h2=it))},enabled=editable && value.domains.isNotBlank())
            Text(stringResource(R.string.h2_media))}
        Text(stringResource(R.string.h2_notice),style=MaterialTheme.typography.bodySmall)
        if(runCatching{value.settings()}.isFailure)Text(stringResource(R.string.cf_configuration_invalid),color=MaterialTheme.colorScheme.error)
    }
    OutlinedTextField(value=value.fakeDomain,onValueChange={onChange(value.copy(fakeDomain=it.take(253)))},modifier=Modifier.fillMaxWidth(),
        label={Text(stringResource(R.string.fake_tls_domain))},enabled=editable,singleLine=true,
        isError=value.fakeDomain.isNotBlank() && !TelegramSecret.validDomain(value.fakeDomain.trim().lowercase(java.util.Locale.ROOT)),
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri))
    Text(stringResource(R.string.fake_tls_notice),style=MaterialTheme.typography.bodySmall)
}

@Composable internal fun DiagnosticsPanel(snapshot:ProxySnapshot,onProbe:()->Unit,onCopy:(String)->Unit){
    var expanded by remember{mutableStateOf(false)}
    var carriers by remember{mutableStateOf(false)}
    var preview by remember{mutableStateOf<String?>(null)}
    TextButton(onClick={expanded=!expanded}){Text(stringResource(R.string.diagnostics))}
    if(expanded){
        val network=snapshot.network
        val unknown=stringResource(R.string.unknown)
        fun yes(value:Boolean?)=when(value){true->R.string.yes;false->R.string.no;null->R.string.unknown}
        val kind=when(network.kind){AccessKind.NONE->R.string.access_none;AccessKind.WIFI->R.string.access_wifi;AccessKind.CELLULAR->R.string.access_cell;AccessKind.OTHER->R.string.access_other}
        val radio=when(network.operator.radio){RadioKind.UNKNOWN->R.string.unknown;RadioKind.G2->R.string.radio_2g;RadioKind.G3->R.string.radio_3g;RadioKind.LTE->R.string.radio_lte;RadioKind.LTE_PLUS->R.string.radio_lte_plus;RadioKind.NR->R.string.radio_nr}
        Text(stringResource(R.string.diagnostic_local,stringResource(if(snapshot.localAvailable)R.string.yes else R.string.no)))
        Text(stringResource(R.string.network_summary,stringResource(kind),stringResource(yes(network.validated)),stringResource(yes(network.captivePortal))))
        Text(stringResource(R.string.network_cost,stringResource(yes(network.metered)),stringResource(yes(network.roaming))))
        Text(stringResource(R.string.network_families,stringResource(yes(network.ipv4)),stringResource(yes(network.ipv6)),network.nat64Prefix?:unknown))
        Text(stringResource(R.string.network_dns,network.dnsServers.joinToString(", ").ifEmpty{unknown},
            stringResource(yes(network.privateDns)),network.privateDnsName?:unknown))
        Text(stringResource(R.string.operator_details,network.operator.registeredName?:unknown,network.operator.registeredPlmn?:unknown,
            network.operator.simName?:unknown,network.operator.simPlmn?:unknown,stringResource(radio)))
        Text(stringResource(R.string.traffic_counts,snapshot.sent,snapshot.received))
        Text(stringResource(R.string.diagnostics_notice),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(enabled=snapshot.localAvailable,onClick=onProbe){Text(stringResource(R.string.probe_routes))}
        Text(stringResource(R.string.probe_limit),style=MaterialTheme.typography.bodySmall)
        for(route in snapshot.routes){
            val transport=when(route.kind){TransportKind.WSS->R.string.kind_wss;TransportKind.TCP->R.string.kind_tcp;TransportKind.RELAY->R.string.kind_relay;TransportKind.WORKER->R.string.kind_worker;TransportKind.CLOUDFLARE->R.string.kind_cf;TransportKind.HTTP2->R.string.kind_h2}
            val status=when(route.status){RouteStatus.UNCHECKED->R.string.route_unchecked;RouteStatus.CONNECTING->R.string.route_connecting;RouteStatus.AVAILABLE->R.string.route_available;RouteStatus.BACKOFF->R.string.route_backoff;RouteStatus.UNAVAILABLE->R.string.route_unavailable}
            val stage=when(route.stage){RouteStage.SETUP->R.string.stage_setup;RouteStage.HANDSHAKE->R.string.stage_handshake;RouteStage.READY->R.string.stage_ready;RouteStage.DNS->R.string.stage_dns;RouteStage.TLS->R.string.stage_tls;RouteStage.CLOSED->R.string.stage_closed}
            val checked=if(route.checkedAtMillis==0L)unknown else java.text.DateFormat.getTimeInstance().format(java.util.Date(route.checkedAtMillis))
            Text(stringResource(R.string.route_details,route.id,if(route.media)-route.dc else route.dc,stringResource(transport),stringResource(status),
                stringResource(stage),checked,route.setupMillis?.toString()?:unknown,route.issue?.let{stringResource(diagnosticIssueResource(it))}?:stringResource(R.string.no),route.attempts))
            route.timings?.let{timing->Text(stringResource(R.string.phase_timings,timing.dnsMillis?.toString()?:unknown,
                timing.tcpMillis?.toString()?:unknown,timing.tlsMillis?.toString()?:unknown,timing.httpMillis?.toString()?:unknown))
                Text(stringResource(R.string.route_dns_answers,timing.resolvedAddresses.joinToString(", ").ifEmpty{unknown}))}
        }
        TextButton(onClick={carriers=!carriers}){Text(stringResource(R.string.carrier_reference))}
        if(carriers){
            Text(stringResource(R.string.carrier_reference_notice),style=MaterialTheme.typography.bodySmall)
            for(card in CarrierReference.cards){
                val name=when(card.brand){CarrierBrand.MEGAFON->R.string.carrier_megafon;CarrierBrand.MTS->R.string.carrier_mts;CarrierBrand.BEELINE->R.string.carrier_beeline;CarrierBrand.T2->R.string.carrier_t2}
                Text(stringResource(R.string.carrier_card,stringResource(name),card.primaryPlmn,card.apn))
            }
        }
        OutlinedButton(onClick={preview=DiagnosticReport.render(snapshot)}){Text(stringResource(R.string.report_preview))}
    }
    preview?.let{report->AlertDialog(onDismissRequest={preview=null},title={Text(stringResource(R.string.report_preview))},
        text={Text(report,Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()))},
        confirmButton={TextButton(onClick={onCopy(report);preview=null}){Text(stringResource(R.string.report_copy))}},
        dismissButton={TextButton(onClick={preview=null}){Text(stringResource(R.string.close))}})}
}

private fun diagnosticIssueResource(issue:ServerIssue):Int=when(issue){
    ServerIssue.DNS->R.string.error_dns;ServerIssue.NETWORK->R.string.error_network;ServerIssue.TLS->R.string.error_tls
    ServerIssue.HTTP2->R.string.error_http2;ServerIssue.RETRY_LATER->R.string.retry_later;ServerIssue.RELAY->R.string.error_relay
    ServerIssue.TCP->R.string.error_tcp;ServerIssue.WSS_HANDSHAKE->R.string.error_wss_handshake
    ServerIssue.TIMEOUT->R.string.error_timeout;ServerIssue.PORT->R.string.error_port;ServerIssue.CONFIGURATION->R.string.error_configuration
    ServerIssue.HANDSHAKE->R.string.error_handshake;ServerIssue.CAPACITY->R.string.error_capacity;ServerIssue.SESSION->R.string.error_session
}
