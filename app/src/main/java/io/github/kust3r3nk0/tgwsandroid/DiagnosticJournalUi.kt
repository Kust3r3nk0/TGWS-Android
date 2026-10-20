package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable internal fun DiagnosticJournal(history:DiagnosticHistoryState,recording:Boolean,report:()->String,
    onCopy:(String)->Unit,onShare:(String)->Unit,onClear:()->Unit){
    var expanded by rememberSaveable{mutableStateOf(false)}
    var levelName by rememberSaveable{mutableStateOf<String?>(null)}
    var categoryName by rememberSaveable{mutableStateOf<String?>(null)}
    var preview by remember{mutableStateOf<String?>(null)}
    var confirmClear by remember{mutableStateOf(false)}
    val palette=MaterialTheme.colorScheme
    val chipColors=FilterChipDefaults.filterChipColors(selectedContainerColor=palette.primary.copy(alpha=.16f),
        selectedLabelColor=palette.onSurface,selectedLeadingIconColor=palette.primary)
    SectionCard(stringResource(R.string.premium_events),Modifier.testTag("diagnostics.events")){
        Text(stringResource(R.string.journal_counts,
            history.events.filter{it.level==EventLevel.ERROR}.sumOf{it.occurrences.toLong()},
            history.events.filter{it.level==EventLevel.WARNING}.sumOf{it.occurrences.toLong()}),style=MaterialTheme.typography.bodySmall)
        if(!recording)Text(stringResource(R.string.journal_disabled))
        if(history.events.isEmpty())Text(stringResource(R.string.premium_no_events))
        history.events.takeLast(5).asReversed().forEach{DiagnosticEventLine(it)}
        TextButton(onClick={expanded=true},modifier=Modifier.testTag("diagnostics.all")){Text(stringResource(R.string.premium_all_events))}
        OutlinedButton(onClick={preview=report()},modifier=Modifier.testTag("diagnostics.report")){Text(stringResource(R.string.report_preview))}
    }
    if(expanded){
        val level=levelName?.let(EventLevel::valueOf)
        val category=categoryName?.let(EventCategory::valueOf)
        val filtered=remember(history.events,level,category){filterEvents(history.events,level,category)}
        AlertDialog(onDismissRequest={expanded=false},containerColor=palette.surface,modifier=Modifier.testTag("journal.dialog"),title={Text(stringResource(R.string.premium_events))},
            text={Column(Modifier.heightIn(max=480.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
                    FilterChip(level==null,{levelName=null},colors=chipColors,label={Text(stringResource(R.string.journal_all))},modifier=Modifier.testTag("journal.level.ALL"))
                    EventLevel.entries.forEach{item->FilterChip(level==item,{levelName=item.name},colors=chipColors,label={Text(stringResource(levelResource(item)))},modifier=Modifier.testTag("journal.level.${item.name}"))}
                }
                var menu by remember{mutableStateOf(false)}
                Box{
                    OutlinedButton(onClick={menu=true},modifier=Modifier.testTag("journal.category")){
                        Text(category?.let{stringResource(categoryResource(it))}?:stringResource(R.string.journal_all_categories))
                    }
                    DropdownMenu(menu,{menu=false}){
                        DropdownMenuItem(text={Text(stringResource(R.string.journal_all_categories))},onClick={categoryName=null;menu=false})
                        EventCategory.entries.forEach{item->DropdownMenuItem(text={Text(stringResource(categoryResource(item)))},onClick={categoryName=item.name;menu=false},modifier=Modifier.testTag("journal.category.${item.name}"))}
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f,fill=false).heightIn(max=300.dp).testTag("journal.list"),verticalArrangement=Arrangement.spacedBy(12.dp)){
                    if(filtered.isEmpty())item{Text(stringResource(R.string.journal_no_matches))}
                    itemsIndexed(filtered){_,event->DiagnosticEventLine(event,details=true)}
                }
                TextButton(onClick={confirmClear=true},enabled=history.events.isNotEmpty() || history.samples.isNotEmpty(),modifier=Modifier.testTag("journal.clear")){Text(stringResource(R.string.journal_clear))}
            }},
            confirmButton={TextButton(onClick={expanded=false}){Text(stringResource(R.string.close))}},
            dismissButton={TextButton(onClick={preview=report()}){Text(stringResource(R.string.journal_preview))}})
    }
    if(confirmClear)AlertDialog(onDismissRequest={confirmClear=false},containerColor=palette.surface,title={Text(stringResource(R.string.journal_clear))},
        text={Text(stringResource(R.string.journal_clear_notice))},confirmButton={TextButton(onClick={onClear();confirmClear=false},modifier=Modifier.testTag("journal.clear.confirm")){Text(stringResource(R.string.journal_clear))}},
        dismissButton={TextButton(onClick={confirmClear=false}){Text(stringResource(R.string.close))}})
    preview?.let{value->AlertDialog(onDismissRequest={preview=null},containerColor=palette.surface,title={Text(stringResource(R.string.report_preview))},
        text={Column(Modifier.heightIn(max=360.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            Text(stringResource(R.string.journal_export_notice),style=MaterialTheme.typography.bodySmall)
            Text(value,Modifier.testTag("journal.preview"),style=MaterialTheme.typography.bodySmall)
        }},confirmButton={TextButton(onClick={onShare(value);preview=null},modifier=Modifier.testTag("journal.share")){Text(stringResource(R.string.journal_share))}},
        dismissButton={TextButton(onClick={onCopy(value);preview=null}){Text(stringResource(R.string.report_copy))}})}
}

@Composable internal fun DiagnosticEventLine(e:DiagnosticEvent,details:Boolean=false){
    val c=MaterialTheme.colorScheme
    val time=remember(e.timeMillis){java.text.DateFormat.getTimeInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(e.timeMillis))}
    val message=when{
        e.phase!=null->stringResource(statusResource(e.phase))
        e.rejection!=null->stringResource(rejectionResource(e.rejection))
        e.rejected!=null->stringResource(R.string.premium_rejected_event,e.rejected)
        e.issue!=null->stringResource(issueResource(e.issue))
        e.provider!=null->stringResource(providerStateResource(e.provider.state))
        e.localCheck!=null->stringResource(localOutcomeResource(e.localCheck.outcome))
        e.kind==EventKind.CLIENT_CLOSED->stringResource(R.string.journal_client_closed)
        e.kind==EventKind.ROUTE_CLOSED->stringResource(R.string.route_closed)
        e.kind==EventKind.ROUTE_FAILED->stringResource(R.string.route_unavailable)
        e.transport!=null->e.transport.name+" · "+stringResource(R.string.route_available)
        else->stringResource(if(e.success)R.string.network_validated else R.string.network_unvalidated)
    }
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
        Text("$time · ${stringResource(levelResource(e.level))} · ${stringResource(categoryResource(e.category))}",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
        Text(message,style=MaterialTheme.typography.bodySmall,color=if(e.level==EventLevel.ERROR)c.error else c.onSurface)
        if(e.occurrences>1)Text(stringResource(R.string.journal_occurrences,e.occurrences),style=MaterialTheme.typography.labelSmall)
        if(details){
            Text("${e.kind.name} · ${e.issue?.name?:e.rejection?.name?:e.kind.name}",style=MaterialTheme.typography.labelSmall,color=c.onSurfaceVariant)
            e.route?.let{r->
                Text(stringResource(R.string.journal_route,r.id,if(r.media)-r.dc else r.dc,e.transport?.name.orEmpty(),r.stage.name,r.attempts),style=MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.phase_timings,r.dnsMillis?.toString()?:"—",r.tcpMillis?.toString()?:"—",r.tlsMillis?.toString()?:"—",r.httpMillis?.toString()?:"—"),style=MaterialTheme.typography.bodySmall)
                if(r.closeCode!=null || r.closeCategory!=null)Text(stringResource(R.string.journal_close,r.closeCode?.toString()?:"—",r.closeCategory?.name?:"—"),style=MaterialTheme.typography.bodySmall)
            }
            e.provider?.let{Text(listOfNotNull(it.packageName,it.version,it.channel.name).joinToString(" · "),style=MaterialTheme.typography.bodySmall)}
            e.network?.let{Text("${it.name} · IPv4 ${e.ipv4} · IPv6 ${e.ipv6}",style=MaterialTheme.typography.bodySmall)}
        }
    }
}

internal fun levelResource(level:EventLevel)=when(level){EventLevel.INFO->R.string.journal_info;EventLevel.WARNING->R.string.journal_warning;EventLevel.ERROR->R.string.journal_error}
internal fun categoryResource(category:EventCategory)=when(category){
    EventCategory.SERVICE->R.string.premium_service;EventCategory.NETWORK->R.string.premium_network
    EventCategory.ROUTE->R.string.premium_route;EventCategory.CLIENT->R.string.premium_client
    EventCategory.CONFIGURATION->R.string.journal_configuration;EventCategory.DNS->R.string.journal_dns
    EventCategory.TLS->R.string.journal_tls;EventCategory.TCP->R.string.journal_tcp;EventCategory.WSS->R.string.journal_wss
    EventCategory.RELAY->R.string.premium_relay;EventCategory.HTTP2->R.string.journal_http2
    EventCategory.WEBVIEW->R.string.journal_webview;EventCategory.CHECK->R.string.journal_check
}
