package io.github.kust3r3nk0.tgwsandroid

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay

private data class UpdatePalette(val background:Color,val card:Color,val border:Color,val accent:Color,val dim:Color,val hero:List<Color>)
@Composable private fun palette():UpdatePalette = if(MaterialTheme.colorScheme.background.luminance()<.5f)
    UpdatePalette(Color(0xFF090F11),Color(0xFF141B1E),Color(0xFF2B383B),Color(0xFF39E999),Color(0xFFADB8B9),listOf(Color(0xFF153E31),Color(0xFF0F201E),Color(0xFF11191C)))
    else UpdatePalette(Color(0xFFF1F7F4),Color(0xFFFCFFFD),Color(0xFFCCDCD3),Color(0xFF006D42),Color(0xFF4E6258),listOf(Color(0xFFCAF2DC),Color(0xFFE5F4EB),Color(0xFFF6FBF8)))

@Composable private fun UpdatePanel(modifier:Modifier=Modifier,spacing:androidx.compose.ui.unit.Dp=8.dp,content:@Composable ColumnScope.()->Unit){
    val c=palette()
    Card(modifier.fillMaxWidth().liquidGlass(),shape=RoundedCornerShape(20.dp),border=BorderStroke(1.dp,c.border.copy(alpha=.55f)),
        colors=CardDefaults.cardColors(containerColor=if(LocalLiquidGlass.current||LocalTransparency.current)c.card.copy(alpha=.86f)else c.card,contentColor=MaterialTheme.colorScheme.onSurface)){
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(spacing),content=content)
    }
}
@Composable internal fun TgwsUpdateHub(page:Int,currentVersion:String,onPage:(Int)->Unit,onBack:()->Unit,
    requestNotificationPermission:()->Unit,model:UpdateViewModel=viewModel()){
    val state by model.state.collectAsState()
    val context=LocalContext.current
    val installer=remember(context){UpdateInstaller(context)}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){model.permissionReturned()}
    val installation=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){model.permissionReturned()}
    fun install(){model.install(installer.allowed()){file->
        try{if(file==null)permission.launch(installer.permissionIntent())else installation.launch(installer.installIntent(file))}
        catch(_:android.content.ActivityNotFoundException){model.installerFailed()}
        catch(_:SecurityException){model.installerFailed()}
    }}
    fun back(){model.defer();if(page==3)onPage(2)else onBack()}
    BackHandler(onBack=::back)
    LaunchedEffect(page){if(page==2)model.check()}
    LaunchedEffect(state.ready){if(state.ready && model.takeInstallRequest())install()}
    UpdateHubScreen(page,currentVersion,state,::back,{onPage(3)},model::options,model::check,
        {if(state.ready)install()else model.download()},model::cancel,requestNotificationPermission)
}

/** Pure UI: networking, persistence and installation live in their own components. */
@Composable internal fun UpdateHubScreen(page:Int,currentVersion:String,state:UpdateState,onBack:()->Unit,onSettings:()->Unit,
    onOptions:(UpdateOptions)->Unit,onCheck:()->Unit,onUpdate:()->Unit,onCancel:()->Unit,requestNotificationPermission:()->Unit){
    val c=palette()
    var now by remember{mutableLongStateOf(System.currentTimeMillis())}
    LaunchedEffect(state.retryAt){val wait=state.retryAt-System.currentTimeMillis();if(wait>0)delay(wait.coerceAtMost(86400000));now=System.currentTimeMillis()}
    val canCheck=!state.checking && !state.downloading && now>=state.retryAt
    Scaffold(Modifier.imePadding().semantics{testTagsAsResourceId=true},containerColor=c.background,contentColor=MaterialTheme.colorScheme.onBackground,
        topBar={Row(Modifier.fillMaxWidth().background(c.background).statusBarsPadding().padding(horizontal=6.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically){
            IconButton(onClick=onBack,modifier=Modifier.testTag("updates.back")){Icon(Icons.AutoMirrored.Filled.ArrowBack,stringResource(R.string.update_back),tint=c.dim)}
            if(page==2)Image(painterResource(R.drawable.tgws_symbol),null,Modifier.size(32.dp))
            Column(Modifier.weight(1f).padding(horizontal=10.dp)){
                Text(stringResource(if(page==2)R.string.app_name else R.string.update_auto),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
                if(page==2)Text(stringResource(R.string.update_center),style=MaterialTheme.typography.bodySmall,color=c.dim)
            }
            if(page==2)IconButton(onClick=onSettings,modifier=Modifier.testTag("updates.settings")){Icon(Icons.Default.MoreVert,stringResource(R.string.update_settings),tint=c.dim)}
        }},bottomBar={if(page==2 && state.release!=null)Column(Modifier.fillMaxWidth().background(c.background).navigationBarsPadding().padding(horizontal=16.dp,vertical=10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
            if(state.downloading)OutlinedButton(onClick=onCancel,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag("updates.cancel")){Text(stringResource(R.string.update_cancel))}
            else Button(onClick=onUpdate,enabled=!state.checking,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp).testTag("updates.install"),contentPadding=PaddingValues(0.dp),
                colors=ButtonDefaults.buttonColors(containerColor=c.accent,contentColor=if(c.accent.luminance()>.4f)Color(0xFF002D18)else Color.White)){
                val gradient=if(c.accent.luminance()>.4f)listOf(Color(0xFF77F5AA),Color(0xFF00CE76))else listOf(Color(0xFF008552),c.accent)
                Row(Modifier.fillMaxWidth().background(Brush.verticalGradient(gradient)).padding(14.dp),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){
                    Icon(if(state.ready)Icons.Default.Check else ImageVector.vectorResource(R.drawable.ic_update_download),null,Modifier.size(22.dp));Spacer(Modifier.width(8.dp))
                    Text(stringResource(if(state.ready)R.string.update_install else R.string.update_download),fontWeight=FontWeight.Bold)
                }
            }
            OutlinedButton(onClick=onBack,modifier=Modifier.fillMaxWidth().heightIn(min=48.dp),border=BorderStroke(1.dp,c.border)){Text(stringResource(R.string.update_later),color=c.dim)}
        }}){insets->
        LazyColumn(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets).testTag("updates.scroll"),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
            if(page==2){
                item{UpdateHero(state)}
                if(state.release!=null){
                    item{Text(stringResource(R.string.update_whats_new),Modifier.padding(top=10.dp,bottom=4.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)}
                    items(state.release.sections,key={it.category}){ChangeCard(it)}
                    if(state.release.sections.isEmpty())item{UpdatePanel{Text(stringResource(R.string.update_no_notes),color=c.dim)}}
                }
                if(state.downloading)item{UpdatePanel(Modifier.testTag("updates.progress")){
                    if(state.verifying)LinearProgressIndicator(Modifier.fillMaxWidth(),color=c.accent)
                    else LinearProgressIndicator(progress={if(state.total>0)state.downloaded.toFloat()/state.total else 0f},modifier=Modifier.fillMaxWidth(),color=c.accent)
                    Text(if(state.verifying)stringResource(R.string.update_verifying)else stringResource(R.string.update_progress,state.downloaded/1024,state.total/1024),color=c.dim)
                }}
                item{UpdateFeedback(state)}
                if(state.release==null)item{ManualCheck(canCheck,"updates.refresh",onCheck)}
            }else{
                item{UpdateSwitch(Icons.Default.Refresh,R.string.update_auto,R.string.update_auto_description,state.options.auto,"updates.auto"){onOptions(state.options.copy(auto=it))}}
                item{UpdatePanel(spacing=0.dp){
                    Text(stringResource(R.string.update_period),Modifier.padding(bottom=8.dp),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                    UpdateOptions.periods.forEachIndexed{index,period->
                        if(index>0)HorizontalDivider(color=c.border.copy(alpha=.4f))
                        val selected=state.options.minutes==period
                        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clip(RoundedCornerShape(14.dp)).background(if(selected)c.accent.copy(alpha=.10f)else Color.Transparent)
                            .selectable(selected,onClick={onOptions(state.options.copy(minutes=period))},enabled=state.options.auto,role=Role.RadioButton)
                            .testTag("updates.period.$period").padding(vertical=2.dp),verticalAlignment=Alignment.CenterVertically){
                            RadioButton(selected,onClick=null,enabled=state.options.auto,colors=RadioButtonDefaults.colors(selectedColor=c.accent))
                            Text(stringResource(periodResource(period)),Modifier.weight(1f).padding(end=6.dp),style=MaterialTheme.typography.bodyMedium,color=if(state.options.auto)MaterialTheme.colorScheme.onSurface else c.dim)
                        }
                    }
                }}
                item{UpdateSwitch(Icons.Default.Notifications,R.string.update_notifications,R.string.update_notifications_description,state.options.notify,"updates.notify"){
                    onOptions(state.options.copy(notify=it));if(it)requestNotificationPermission()}}
                item{UpdateSwitch(ImageVector.vectorResource(R.drawable.ic_update_wifi),R.string.update_wifi,R.string.update_wifi_description,state.options.wifiOnly,"updates.wifi"){onOptions(state.options.copy(wifiOnly=it))}}
                item{UpdatePanel{
                    InfoLine(stringResource(R.string.update_current_version),currentVersion)
                    ChannelChoice(state.options,onOptions)
                    InfoLine(stringResource(R.string.update_last_check),if(state.lastSuccess>0)java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT,java.text.DateFormat.SHORT).format(java.util.Date(state.lastSuccess))else stringResource(R.string.update_never))
                    Text(stringResource(resultResource(state.result)),style=MaterialTheme.typography.bodySmall,color=c.dim,modifier=Modifier.testTag("updates.result"))
                }}
                item{UpdateFeedback(state)}
                item{ManualCheck(canCheck,"updates.manual",onCheck)}
                item{Text(stringResource(R.string.update_approximate_schedule),style=MaterialTheme.typography.bodySmall,color=c.dim)}
            }
        }
    }
}
@Composable private fun ChannelChoice(options:UpdateOptions,onOptions:(UpdateOptions)->Unit){
    var expanded by remember{mutableStateOf(false)}
    val c=palette()
    Box{
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).clickable(role=Role.Button){expanded=true}.testTag("updates.channel.menu"),
            verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
            Text(stringResource(R.string.update_channel),Modifier.weight(1f),color=c.dim,style=MaterialTheme.typography.bodySmall)
            Text(stringResource(if(options.channel==UpdateChannel.STABLE)R.string.update_channel_stable else R.string.update_channel_preview),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
            Icon(Icons.Default.KeyboardArrowDown,null,Modifier.size(20.dp),tint=c.dim)
        }
        DropdownMenu(expanded,onDismissRequest={expanded=false}){
            UpdateChannel.entries.forEach{channel->DropdownMenuItem(
                text={Text(stringResource(if(channel==UpdateChannel.STABLE)R.string.update_channel_stable else R.string.update_channel_preview))},
                leadingIcon={RadioButton(options.channel==channel,onClick=null)},
                onClick={expanded=false;onOptions(options.copy(channel=channel))},modifier=Modifier.testTag("updates.channel.${channel.name}"))}
        }
    }
}
@Composable private fun ManualCheck(enabled:Boolean,tag:String,click:()->Unit){
    OutlinedButton(onClick=click,enabled=enabled,modifier=Modifier.fillMaxWidth().heightIn(min=50.dp).testTag(tag),
        colors=ButtonDefaults.outlinedButtonColors(contentColor=MaterialTheme.colorScheme.onSurface),border=BorderStroke(1.dp,palette().border)){
        Icon(Icons.Default.Refresh,null);Spacer(Modifier.width(8.dp));Text(stringResource(R.string.update_check_now))}
}
@Composable private fun UpdateHero(state:UpdateState){
    val c=palette();val release=state.release
    Surface(Modifier.fillMaxWidth().testTag("updates.hero"),shape=RoundedCornerShape(24.dp),color=Color.Transparent,border=BorderStroke(1.dp,c.accent.copy(alpha=.18f))){
        Box(Modifier.fillMaxWidth().heightIn(min=208.dp).background(Brush.linearGradient(c.hero))){
            if(release!=null)Image(painterResource(R.drawable.update_download_glass),null,Modifier.size(160.dp).align(Alignment.BottomEnd))
            Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)){
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Text(stringResource(if(release!=null)R.string.update_available else if(state.result==UpdateCheckResult.CURRENT)R.string.update_current else R.string.update_center),Modifier.weight(1f),style=MaterialTheme.typography.bodySmall,color=c.dim)
                    if(release!=null)Surface(shape=CircleShape,color=c.accent.copy(alpha=.12f),border=BorderStroke(1.dp,c.accent.copy(alpha=.35f))){Text(stringResource(R.string.update_new),Modifier.padding(horizontal=10.dp,vertical=7.dp),color=c.accent,style=MaterialTheme.typography.labelSmall)}
                }
                Text(release?.let{"v${it.version}"}?:stringResource(if(state.result==UpdateCheckResult.CURRENT)R.string.update_current_title else R.string.app_name),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,modifier=Modifier.testTag("updates.version"))
                Text(release?.sections?.firstOrNull()?.text?.lineSequence()?.firstOrNull{it.isNotBlank()}?.removePrefix("- ")
                    ?:stringResource(if(state.checking)R.string.update_checking else if(state.result==UpdateCheckResult.CURRENT)R.string.update_current_description else R.string.update_source_description),
                    Modifier.fillMaxWidth(if(release!=null).66f else 1f),maxLines=4,overflow=TextOverflow.Ellipsis,style=MaterialTheme.typography.bodyMedium,color=c.dim)
            }
        }
    }
}
@Composable private fun ChangeCard(section:ReleaseSection){
    var expanded by rememberSaveable(section.category){mutableStateOf(false)}
    val c=palette()
    UpdatePanel(Modifier.animateContentSize().testTag("updates.category.${section.category.name}")){
        Row(Modifier.fillMaxWidth().clickable(role=Role.Button){expanded=!expanded}.heightIn(min=64.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)){
            val dark=c.background.luminance()<.5f
            val color=when(section.category){ChangeCategory.FIXES->if(dark)Color(0xFF9AAEFF)else Color(0xFF3854A3);ChangeCategory.PERFORMANCE->if(dark)Color(0xFFE5B944)else Color(0xFF846000);else->c.accent}
            Surface(shape=CircleShape,color=color.copy(alpha=.12f)){Icon(when(section.category){ChangeCategory.FIXES->Icons.Default.Build;ChangeCategory.FEATURES->ImageVector.vectorResource(R.drawable.ic_update_auto_awesome);ChangeCategory.IMPORTANT->Icons.Default.Warning;ChangeCategory.PERFORMANCE->ImageVector.vectorResource(R.drawable.ic_update_speed);else->Icons.Default.Info},null,Modifier.padding(12.dp).size(26.dp),tint=color)}
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){
                Text(stringResource(categoryResource(section.category)),style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold)
                Text(section.text,maxLines=if(expanded)Int.MAX_VALUE else 3,overflow=TextOverflow.Ellipsis,color=c.dim,style=MaterialTheme.typography.bodyMedium)
            }
            Icon(if(expanded)Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,stringResource(if(expanded)R.string.update_collapse else R.string.update_expand),Modifier.size(20.dp),tint=c.dim)
        }
    }
}
@Composable private fun UpdateSwitch(icon:ImageVector,title:Int,description:Int,checked:Boolean,tag:String,onChange:(Boolean)->Unit){
    val c=palette();val label=stringResource(title)
    UpdatePanel{Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
        Icon(icon,null,Modifier.size(25.dp),tint=c.dim)
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label,style=MaterialTheme.typography.titleSmall,fontWeight=FontWeight.SemiBold);Text(stringResource(description),style=MaterialTheme.typography.bodySmall,color=c.dim)}
        Switch(checked,onChange,modifier=Modifier.testTag(tag).semantics{this.contentDescription=label},colors=SwitchDefaults.colors(checkedTrackColor=c.accent,checkedThumbColor=Color.White))
    }}
}
@Composable private fun InfoLine(label:String,value:String){Row(horizontalArrangement=Arrangement.spacedBy(12.dp)){
    Text(label,Modifier.weight(1f),color=palette().dim,style=MaterialTheme.typography.bodySmall);Text(value,Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)}}
@Composable private fun UpdateFeedback(state:UpdateState){
    if(state.checking)Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){CircularProgressIndicator(Modifier.size(20.dp),color=palette().accent);Text(stringResource(R.string.update_checking))}
    state.issue?.let{Text(stringResource(updateIssueResource(it)),color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("updates.error"))}
    if(state.installPermissionNeeded)Text(stringResource(R.string.update_permission_hint),color=palette().dim)
    if(state.retryAt>System.currentTimeMillis())Text(stringResource(R.string.update_retry_at,java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(state.retryAt))),style=MaterialTheme.typography.bodySmall,color=palette().dim)
}
internal fun periodResource(minutes:Long)=when(minutes){15L->R.string.update_period_15;60L->R.string.update_period_60;180L->R.string.update_period_180;360L->R.string.update_period_360;720L->R.string.update_period_720;1440L->R.string.update_period_1440;else->R.string.update_period_10080}
internal fun categoryResource(category:ChangeCategory)=when(category){ChangeCategory.IMPORTANT->R.string.update_important;ChangeCategory.FEATURES->R.string.update_features;ChangeCategory.FIXES->R.string.update_fixes;ChangeCategory.PERFORMANCE->R.string.update_performance;ChangeCategory.TECHNICAL->R.string.update_technical;ChangeCategory.GENERAL->R.string.update_general}
internal fun resultResource(result:UpdateCheckResult)=when(result){UpdateCheckResult.NEVER->R.string.update_never;UpdateCheckResult.CURRENT->R.string.update_current;UpdateCheckResult.AVAILABLE->R.string.update_available;UpdateCheckResult.FAILED->R.string.update_check_failed}
internal fun updateIssueResource(issue:UpdateIssue)=when(issue){
    UpdateIssue.NETWORK->R.string.update_error_network;UpdateIssue.TIMEOUT->R.string.update_error_timeout;UpdateIssue.FORBIDDEN->R.string.update_error_forbidden;UpdateIssue.NOT_FOUND->R.string.update_error_not_found;UpdateIssue.RATE_LIMIT->R.string.update_error_rate_limit;UpdateIssue.BAD_RESPONSE->R.string.update_error_bad_response;UpdateIssue.NO_WIFI->R.string.update_error_wifi;UpdateIssue.CHECKSUM->R.string.update_error_checksum;UpdateIssue.INVALID_APK->R.string.update_error_invalid_apk;UpdateIssue.WRONG_PACKAGE->R.string.update_error_package;UpdateIssue.OLD_VERSION->R.string.update_error_old;UpdateIssue.SIGNATURE->R.string.update_error_signature;UpdateIssue.INCOMPATIBLE->R.string.update_error_incompatible;UpdateIssue.STORAGE->R.string.update_error_storage;UpdateIssue.INSTALLER->R.string.update_error_installer
}
