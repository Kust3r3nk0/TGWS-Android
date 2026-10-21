package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.kust3r3nk0.tgwsandroid.core.*

internal fun profileResource(id:ProfileId)=when(id){ProfileId.AUTOMATIC->R.string.smart_profile_auto;ProfileId.WIFI->R.string.access_wifi;ProfileId.MOBILE->R.string.access_cell;ProfileId.CUSTOM->R.string.smart_profile_custom}
internal fun kindResource(kind:TransportKind)=when(kind){TransportKind.WSS->R.string.kind_wss;TransportKind.TCP->R.string.kind_tcp;TransportKind.RELAY->R.string.kind_relay;TransportKind.WORKER->R.string.kind_worker;TransportKind.CLOUDFLARE->R.string.kind_cf;TransportKind.HTTP2->R.string.kind_h2}
internal fun routingModeResource(mode:RoutingMode)=when(mode){RoutingMode.AUTOMATIC->R.string.smart_mode_auto;RoutingMode.MANUAL->R.string.smart_mode_manual;RoutingMode.FAILOVER->R.string.smart_mode_failover}
internal fun availableKinds(draft:TransportDraft,relay:String):Set<TransportKind> = when(draft.mode){
    TransportMode.DIRECT->setOf(TransportKind.WSS,TransportKind.TCP)
    TransportMode.RELAY_ONLY->if(relay.isNotBlank())setOf(TransportKind.RELAY)else emptySet()
    TransportMode.AUTO_CLOUDFLARE->buildSet{
        add(TransportKind.TCP)
        add(TransportKind.WSS)
        if(draft.workers.isNotBlank())add(TransportKind.WORKER)
        if(draft.domains.isNotBlank()){add(TransportKind.CLOUDFLARE);if(draft.h2)add(TransportKind.HTTP2)}
        if(relay.isNotBlank())add(TransportKind.RELAY)
    }
}

@Composable internal fun SmartSwitch(title:Int,value:Boolean,enabled:Boolean=true,tag:String,onChange:(Boolean)->Unit){
    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Text(stringResource(title),Modifier.weight(1f),style=MaterialTheme.typography.bodyMedium)
        Switch(value,onChange,Modifier.testTag(tag),enabled=enabled)
    }
}

@Composable internal fun SmartConnectionSettings(form:ProxyFormModel,editable:Boolean,network:AccessKind=AccessKind.NONE){
    val options=form.transportDraft.value.profiles
    fun change(value:ConnectionProfiles){form.transportDraft.value=form.transportDraft.value.copy(profiles=value)}
    var editingName by rememberSaveable{mutableStateOf(options.id(connectionNetwork(network)).name)}
    val editing=ProfileId.valueOf(editingName)
    val profile=options.profile(editing)
    fun update(value:ConnectionProfile)=change(options.withProfile(editing,value))
    val available=availableKinds(form.transportDraft.value,form.relayUrl.value)
    SectionCard(stringResource(R.string.smart_title),Modifier.testTag("smart.settings")){
        if(!editable)Text(stringResource(R.string.stop_to_edit))
        SmartSwitch(R.string.smart_enable,options.enabled,editable,"smart.enabled"){change(options.copy(enabled=it))}
        Text(stringResource(R.string.smart_scope_notice),style=MaterialTheme.typography.bodySmall)
        SmartSwitch(R.string.smart_auto_network,options.automaticNetwork,editable,"smart.network.auto"){change(options.copy(automaticNetwork=it))}
        Text(stringResource(R.string.smart_network_profile,stringResource(profileResource(options.id(connectionNetwork(network))))),style=MaterialTheme.typography.bodySmall)
        if(!options.automaticNetwork)for(id in ProfileId.entries){
            Row(verticalAlignment=Alignment.CenterVertically){
                RadioButton(options.selected==id,{change(options.copy(selected=id))},Modifier.testTag("smart.selected.${id.name}"),enabled=editable)
                Text(stringResource(profileResource(id)))
            }
        }
    }
    SectionCard(stringResource(R.string.smart_edit_profile)){
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)){
            ProfileId.entries.forEach{id->FilterChip(editing==id,{editingName=id.name},enabled=editable,
                label={Text(stringResource(profileResource(id)))},modifier=Modifier.testTag("smart.profile.${id.name}"))}
        }
        for(mode in RoutingMode.entries)Row(verticalAlignment=Alignment.CenterVertically){
            RadioButton(profile.mode==mode,{update(profile.copy(mode=mode))},Modifier.testTag("smart.mode.${mode.name}"),enabled=editable)
            Text(stringResource(routingModeResource(mode)))
        }
        Text(stringResource(R.string.smart_primary),style=MaterialTheme.typography.titleSmall)
        for(kind in TransportKind.entries)Row(verticalAlignment=Alignment.CenterVertically){
            RadioButton(profile.primary==kind,{
                update(profile.copy(primary=kind,allowed=profile.allowed+kind,backups=(profile.backups+profile.primary)-kind))
            },Modifier.testTag("smart.primary.${kind.name}"),enabled=editable && kind in available)
            Text(stringResource(kindResource(kind))+if(kind !in available)" · "+stringResource(R.string.smart_unconfigured)else "")
        }
        Text(stringResource(R.string.smart_permissions),style=MaterialTheme.typography.titleSmall)
        for(kind in TransportKind.entries)Row(verticalAlignment=Alignment.CenterVertically){
            Checkbox(kind in profile.allowed,{checked->update(profile.copy(allowed=if(checked)profile.allowed+kind else profile.allowed-kind,
                backups=if(checked)profile.backups else profile.backups-kind))},Modifier.testTag("smart.allowed.${kind.name}"),enabled=editable && kind!=profile.primary)
            Text(stringResource(kindResource(kind)))
        }
        if(profile.mode==RoutingMode.FAILOVER){
            Text(stringResource(R.string.smart_backups),style=MaterialTheme.typography.titleSmall)
            for(kind in TransportKind.entries.filter{it in profile.allowed && it!=profile.primary})Row(verticalAlignment=Alignment.CenterVertically){
                Checkbox(kind in profile.backups,{checked->update(profile.copy(backups=if(checked)profile.backups+kind else profile.backups-kind))},Modifier.testTag("smart.backup.${kind.name}"),enabled=editable)
                Text(stringResource(kindResource(kind)))
            }
        }
        if(available.none(profile::permits))Text(stringResource(R.string.smart_no_routes),color=MaterialTheme.colorScheme.error)
        SmartSwitch(R.string.smart_adaptive,profile.adaptiveRecovery,editable,"smart.adaptive"){update(profile.copy(adaptiveRecovery=it))}
        Text(stringResource(R.string.smart_recovery_notice),style=MaterialTheme.typography.bodySmall)
        SmartSwitch(R.string.smart_power_saver,profile.power==PowerPolicy.SAVER,editable,"smart.power"){update(profile.copy(power=if(it)PowerPolicy.SAVER else PowerPolicy.BALANCED))}
        Text(stringResource(R.string.smart_power_notice),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.h2_notice),style=MaterialTheme.typography.bodySmall)
    }
    if(runCatching{form.transportDraft.value.settings()}.isFailure)Text(stringResource(R.string.cf_configuration_invalid),color=MaterialTheme.colorScheme.error)
    SaveSettingsButton(form,editable){}
}
