package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.dp
import io.github.kust3r3nk0.tgwsandroid.core.RelayEndpoint
import io.github.kust3r3nk0.tgwsandroid.core.TransportMode

@Composable internal fun SettingsRow(title:Int,description:String,tag:String,icon:Int,onClick:()->Unit){
    Row(Modifier.fillMaxWidth().heightIn(min=64.dp).clickable(role=Role.Button,onClick=onClick).testTag(tag).padding(vertical=6.dp),
        verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Icon(painterResource(icon),null,Modifier.size(24.dp),tint=MaterialTheme.colorScheme.primary)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(3.dp)){
            Text(stringResource(title),style=MaterialTheme.typography.titleSmall)
            Text(description,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight,null,tint=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable internal fun PremiumSettings(onPage:(Int)->Unit,theme:ThemeMode,mode:TransportMode,recording:Boolean){
    SectionCard(stringResource(R.string.premium_connection)){
        SettingsRow(R.string.mtproxy_settings,stringResource(R.string.premium_connection_summary),"settings.connection",R.drawable.ic_premium_link){onPage(5)}
    }
    SectionCard(stringResource(R.string.transport_settings)){
        SettingsRow(R.string.premium_transport,stringResource(modeResource(mode)),"settings.transport",R.drawable.ic_premium_hub){onPage(6)}
    }
    SectionCard(stringResource(R.string.appearance_title)){
        SettingsRow(R.string.premium_appearance,stringResource(when(theme){ThemeMode.SYSTEM->R.string.theme_system;ThemeMode.DARK->R.string.theme_dark;ThemeMode.LIGHT->R.string.theme_light}),"settings.appearance",R.drawable.ic_sun){onPage(7)}
    }
    SectionCard(stringResource(R.string.update_section),Modifier.testTag("settings.updates")){
        SettingsRow(R.string.update_center,stringResource(R.string.update_source_description),"settings.update.center",R.drawable.ic_update_download){onPage(2)}
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        SettingsRow(R.string.update_auto,stringResource(R.string.update_settings),"settings.update.options",R.drawable.ic_settings){onPage(3)}
    }
    SectionCard(stringResource(R.string.diagnostics)){
        SettingsRow(R.string.premium_diagnostic_options,stringResource(if(recording)R.string.premium_recording_on else R.string.premium_recording_off),"settings.diagnostics",R.drawable.ic_update_speed){onPage(9)}
    }
    SectionCard(stringResource(R.string.about_title)){
        SettingsRow(R.string.premium_about,stringResource(R.string.premium_about_summary),"settings.about",R.drawable.tgws_symbol){onPage(8)}
    }
}
@Composable internal fun ConnectionSettings(form:ProxyFormModel,editable:Boolean,reveal:Boolean,onReveal:()->Unit,onCopy:()->Unit,onLink:()->Unit){
    var port by form.portText;var secret by form.secretText;var relay by form.relayUrl;var imported by form.imported
    SectionCard(stringResource(R.string.mtproxy_settings)){
        if(!editable)Text(stringResource(R.string.stop_to_edit),style=MaterialTheme.typography.bodySmall)
        OutlinedTextField(port,{port=it.filter{c->c in '0'..'9'}.take(5)},Modifier.fillMaxWidth().testTag("settings.port"),
            label={Text(stringResource(R.string.local_port))},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),enabled=editable,singleLine=true)
        OutlinedTextField(secret,{secret=it.trim()},Modifier.fillMaxWidth().testTag("settings.secret"),label={Text(stringResource(R.string.secret_label))},
            visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),
            enabled=editable && !form.secretUnavailable.value,singleLine=true,isError=secret.isNotEmpty() && !SettingsValidation.validSecret(secret),
            supportingText={Text(stringResource(R.string.secret_input_help))},trailingIcon={TextButton(onClick=onReveal,enabled=secret.isNotEmpty() || relay.isNotEmpty()){
                Text(stringResource(if(reveal)R.string.secret_hide else R.string.secret_show))}})
        OutlinedTextField(relay,{relay=it.take(RelayEndpoint.MAX_URL_LENGTH)},Modifier.fillMaxWidth().testTag("settings.relay"),label={Text(stringResource(R.string.relay_label))},
            visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Uri),
            enabled=editable,singleLine=true,isError=runCatching{RelayEndpoint.parse(relay)}.isFailure)
        Text(stringResource(R.string.premium_transport_location),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(imported,{imported=it.take(2048)},Modifier.fillMaxWidth(),label={Text(stringResource(R.string.import_label))},enabled=editable,
            visualTransformation=if(reveal)VisualTransformation.None else PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password),singleLine=true)
        OutlinedButton(onClick={form.importSecret();if(reveal)onReveal()},enabled=editable && imported.isNotEmpty()){Text(stringResource(R.string.import_button))}
        if(form.secretUnavailable.value)Text(stringResource(R.string.secret_recovery_notice),color=MaterialTheme.colorScheme.error)
        OutlinedButton(onClick={form.generate();if(reveal)onReveal()},enabled=editable){Text(stringResource(if(form.secretUnavailable.value)R.string.secret_replace else R.string.secret_generate))}
        Text(stringResource(R.string.telegram_link_notice),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.secret_change_notice),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=onCopy,enabled=form.valid && !form.busy.value,modifier=Modifier.fillMaxWidth()){Text(stringResource(R.string.copy_secret))}
        OutlinedButton(onClick=onLink,enabled=form.valid && !form.busy.value,modifier=Modifier.fillMaxWidth().testTag("telegram.copy_link")){Text(stringResource(R.string.copy_link))}
        SaveSettingsButton(form,editable){if(reveal)onReveal()}
    }
}
@Composable internal fun SaveSettingsButton(form:ProxyFormModel,editable:Boolean,onSave:()->Unit){
    Button(onClick={onSave();form.withSaved()},enabled=editable && form.valid && !form.busy.value,modifier=Modifier.fillMaxWidth().testTag("settings.save")){
        Text(stringResource(R.string.save_settings))
    }
}
@Composable internal fun DiagnosticOptions(recording:Boolean,onRecord:(Boolean)->Unit,canProbe:Boolean,onProbe:()->Unit){
    SectionCard(stringResource(R.string.premium_diagnostic_options)){
        Row(verticalAlignment=Alignment.CenterVertically){Text(stringResource(R.string.premium_recording),Modifier.weight(1f));Switch(recording,onRecord,Modifier.testTag("diagnostics.record"))}
        Text(stringResource(R.string.premium_history_notice),style=MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick=onProbe,enabled=canProbe){Text(stringResource(R.string.probe_routes))}
        Text(stringResource(R.string.probe_limit),style=MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun AboutSettings(version:String,onOpen:(String)->Unit){
    SectionCard(stringResource(R.string.about_title)){
        Text(stringResource(R.string.version_label,version),Modifier.testTag("about.version"))
        Text(stringResource(R.string.premium_release_notice),Modifier.testTag("about.notice"),style=MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.premium_developer))
        TextButton(onClick={onOpen("https://github.com/Kust3r3nk0/TGWS-Android")}){Text(stringResource(R.string.premium_repository))}
        TextButton(onClick={onOpen("https://github.com/Kust3r3nk0/TGWS-Android/blob/main/LICENSE")}){Text(stringResource(R.string.premium_license))}
    }
}
internal fun modeResource(mode:TransportMode)=when(mode){TransportMode.DIRECT->R.string.mode_direct;TransportMode.RELAY_ONLY->R.string.mode_relay;TransportMode.AUTO_CLOUDFLARE->R.string.mode_cf}
