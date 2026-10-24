package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource

@Composable internal fun LocalDpiSettings(preferences:AppPreferences,editable:Boolean,mtprotoPort:Int?=null){
    var port by remember{mutableStateOf(preferences.localSocksPort.takeIf{it!=0}?.toString()?:"1080")}
    var enabled by remember{mutableStateOf(preferences.localSocksPort!=0)}
    val valid=port.toIntOrNull()?.let{it in 1..65535 && it!=mtprotoPort}==true
    Text(stringResource(R.string.dpi_title),style=MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.dpi_notice),style=MaterialTheme.typography.bodySmall)
    SmartSwitch(R.string.dpi_enable,enabled,editable && (enabled || valid),"dpi.enabled"){
        enabled=it;preferences.localSocksPort=if(it)port.toInt()else 0
    }
    OutlinedTextField(port,{value->port=value.take(5)
        if(enabled && port.toIntOrNull()?.let{it in 1..65535}==true)preferences.localSocksPort=port.toInt()
    },Modifier.fillMaxWidth().testTag("dpi.port"),enabled=editable && !enabled,
        label={Text(stringResource(R.string.dpi_port))},singleLine=true,isError=!valid)
    Text(stringResource(R.string.dpi_scope),style=MaterialTheme.typography.bodySmall)
}
