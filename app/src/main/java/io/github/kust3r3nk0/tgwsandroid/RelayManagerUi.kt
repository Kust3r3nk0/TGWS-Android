package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import io.github.kust3r3nk0.tgwsandroid.core.*

@Composable internal fun RelayManagerScreen(form:ProxyFormModel,checks:RelayChecksModel,snapshot:ProxySnapshot,editable:Boolean,reveal:Boolean,onReveal:()->Unit){
    val config=form.transportDraft.value.relays
    fun change(value:RelayConfiguration){form.transportDraft.value=form.transportDraft.value.copy(relays=value)}
    fun reset(){form.relayEditingId.value=null;form.relayName.value="";form.relayEndpoint.value="";form.relayMedia.value=true}
    var deleting by remember{mutableStateOf<String?>(null)}
    SectionCard(stringResource(R.string.relay_manager),Modifier.testTag("relays.manager")){
        if(!editable)Text(stringResource(R.string.stop_to_edit))
        SmartSwitch(R.string.relays_enable,config.enabled,editable && config.nodes.isNotEmpty(),"relays.enabled"){change(config.copy(enabled=it))}
        Text(stringResource(R.string.relays_scope),style=MaterialTheme.typography.bodySmall)
        if(form.transportDraft.value.mode==TransportMode.DIRECT)Text(stringResource(R.string.relays_direct_notice),style=MaterialTheme.typography.bodySmall)
        for(strategy in RelayStrategy.entries)Row(verticalAlignment=Alignment.CenterVertically){
            RadioButton(config.strategy==strategy,{change(config.copy(strategy=strategy))},Modifier.testTag("relays.mode.${strategy.name}"),enabled=editable)
            Text(stringResource(when(strategy){RelayStrategy.MANUAL->R.string.relays_manual;RelayStrategy.FAILOVER->R.string.relays_failover;RelayStrategy.AUTOMATIC->R.string.relays_auto}))
        }
        SmartSwitch(R.string.relays_media_prefer,config.preferMedia,editable,"relays.prefer.media"){change(config.copy(preferMedia=it))}
        Text(stringResource(R.string.relays_media_notice),style=MaterialTheme.typography.bodySmall)
        if(!form.valid && !form.busy.value)Text(stringResource(R.string.relays_save_requirement),color=MaterialTheme.colorScheme.error)
        SaveSettingsButton(form,editable){}
    }
    if(config.nodes.isEmpty())Text(stringResource(R.string.relays_empty),Modifier.testTag("relays.empty"))
    for((index,node) in config.nodes.withIndex())SectionCard(stringResource(R.string.relays_node,index+1),Modifier.testTag("relays.node.${node.id}")){
        Text(node.name,style=MaterialTheme.typography.titleMedium)
        Text(if(reveal)node.url else stringResource(R.string.premium_endpoint_hidden),style=MaterialTheme.typography.bodySmall)
        if(snapshot.health.active.any{it.relaySlot==index+1})Text(stringResource(R.string.relays_active),color=MaterialTheme.colorScheme.secondary)
        Row(verticalAlignment=Alignment.CenterVertically){
            RadioButton(config.primary==node.id,{
                change(config.copy(primary=node.id,backups=(config.backups+listOfNotNull(config.primary)).distinct().filter{it!=node.id}))
            },Modifier.testTag("relays.primary.${node.id}"),enabled=editable)
            Text(stringResource(R.string.relays_primary))
        }
        if(config.primary!=node.id)Row(verticalAlignment=Alignment.CenterVertically){
            Checkbox(node.id in config.backups,{on->change(config.copy(backups=if(on)config.backups+node.id else config.backups-node.id))},Modifier.testTag("relays.backup.${node.id}"),enabled=editable)
            Text(stringResource(R.string.relays_backup))
        }
        Text(stringResource(if(node.media)R.string.relays_media_yes else R.string.relays_media_no),style=MaterialTheme.typography.bodySmall)
        checks.results[node.id]?.let{result->
            Text(if(result.issue==null)stringResource(R.string.relays_check_ok,result.millis?:0)else stringResource(R.string.relays_check_failed,stringResource(issueResource(ProxyIssue.valueOf(result.issue.name)))),Modifier.testTag("relays.result.${node.id}"))
            Text(java.text.DateFormat.getTimeInstance().format(java.util.Date(result.at)),style=MaterialTheme.typography.bodySmall)
            result.upgrade?.status?.let{Text(stringResource(R.string.relays_http,it),style=MaterialTheme.typography.bodySmall)}
        }
        if(checks.checking==node.id){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=checks::cancel){Text(stringResource(R.string.relays_cancel))}}
        Column(verticalArrangement=Arrangement.spacedBy(4.dp)){
            OutlinedButton(onClick={checks.check(node.id,node.url)},enabled=checks.checking==null,modifier=Modifier.testTag("relays.check.${node.id}")){Text(stringResource(R.string.relays_check))}
            TextButton(onClick={checks.clear(node.id);form.relayEditingId.value=node.id;form.relayName.value=node.name;form.relayEndpoint.value=node.url;form.relayMedia.value=node.media},enabled=editable,modifier=Modifier.testTag("relays.edit.${node.id}")){Text(stringResource(R.string.relays_edit))}
            TextButton(onClick={deleting=node.id},enabled=editable,modifier=Modifier.testTag("relays.delete.${node.id}")){Text(stringResource(R.string.relays_delete))}
        }
    }
    if(checks.limited)Text(stringResource(R.string.relays_rate_limit))
    Text(stringResource(R.string.relays_probe_notice),style=MaterialTheme.typography.bodySmall)
    SectionCard(stringResource(if(form.relayEditingId.value==null)R.string.relays_add else R.string.relays_edit)){
        OutlinedTextField(form.relayName.value,{form.relayName.value=it.take(48)},Modifier.fillMaxWidth().testTag("relays.name"),label={Text(stringResource(R.string.relays_name))},enabled=editable,singleLine=true)
        OutlinedTextField(form.relayEndpoint.value,{form.relayEndpoint.value=it.take(RelayEndpoint.MAX_URL_LENGTH)},Modifier.fillMaxWidth().testTag("relays.url"),label={Text(stringResource(R.string.relay_label))},enabled=editable,
            visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),singleLine=true)
        TextButton(onClick=onReveal,modifier=Modifier.testTag("relays.reveal")){Text(stringResource(if(reveal)R.string.secret_hide else R.string.secret_show))}
        SmartSwitch(R.string.relays_allow_media,form.relayMedia.value,editable,"relays.node.media"){form.relayMedia.value=it}
        val node=runCatching{RelayNode(form.relayEditingId.value?:"new",form.relayName.value.trim(),form.relayEndpoint.value,form.relayMedia.value)}.getOrNull()
        val valid=node!=null && (form.relayEditingId.value!=null || config.nodes.size<8) && runCatching{config.put(node)}.isSuccess
        if((form.relayName.value.isNotEmpty() || form.relayEndpoint.value.isNotEmpty()) && !valid)Text(stringResource(R.string.relays_invalid),color=MaterialTheme.colorScheme.error)
        Button(onClick={val next=node!!.copy(id=form.relayEditingId.value?:java.util.UUID.randomUUID().toString());checks.clear(next.id);change(config.put(next));reset()},enabled=editable && valid,modifier=Modifier.testTag("relays.apply")){Text(stringResource(R.string.relays_apply))}
        if(form.relayEditingId.value!=null)TextButton(onClick={reset()},enabled=editable){Text(stringResource(R.string.relays_cancel))}
        Text(stringResource(R.string.relays_save_notice),style=MaterialTheme.typography.bodySmall)
    }
    deleting?.let{id->AlertDialog(onDismissRequest={deleting=null},title={Text(stringResource(R.string.relays_delete))},text={Text(stringResource(R.string.relays_delete_confirm))},
        confirmButton={TextButton(onClick={checks.clear(id);change(config.remove(id));if(form.relayEditingId.value==id)reset();deleting=null},modifier=Modifier.testTag("relays.delete.confirm")){Text(stringResource(R.string.relays_delete))}},
        dismissButton={TextButton(onClick={deleting=null}){Text(stringResource(R.string.relays_cancel))}})}
}
