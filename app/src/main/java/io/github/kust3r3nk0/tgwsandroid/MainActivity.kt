package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.*
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private var updateRequest by mutableIntStateOf(0)
    override fun onNewIntent(intent:Intent){
        super.onNewIntent(intent)
        if(intent.getBooleanExtra(OPEN_UPDATES,false))updateRequest++
    }
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val packageInfo=packageManager.getPackageInfo(packageName,0)
        val version=packageInfo.versionName.orEmpty()
        setContent {
            val form:ProxyFormModel=viewModel()
            val tools:DiagnosticToolsModel=viewModel()
            val relayChecks:RelayChecksModel=viewModel()
            val autoConfig:AutoConfigModel=viewModel()
            val shareScope=rememberCoroutineScope()
            val preferences=remember{AppPreferences(this@MainActivity)}
            var theme by remember{mutableStateOf(preferences.theme)}
            var glass by remember{mutableStateOf(preferences.liquidGlass)}
            var transparent by remember{mutableStateOf(preferences.transparency)}
            var recording by remember{mutableStateOf(preferences.diagnostics)}
            var connectionAlerts by remember{mutableStateOf(preferences.connectionAlerts)}
            var reveal by remember{mutableStateOf(false)}
            var page by rememberSaveable{mutableIntStateOf(if(intent.getBooleanExtra(OPEN_UPDATES,false))2 else 0)}
            val pageStates=rememberSaveableStateHolder()
            var updateParent by rememberSaveable{mutableIntStateOf(1)}
            var network by remember{mutableStateOf(NetworkView())}
            LaunchedEffect(form.portText.value){tools.selectPort(form.portText.value)}
            LaunchedEffect(updateRequest){
                if(updateRequest>0 || intent.getBooleanExtra(OPEN_UPDATES,false)){
                    autoConfig.cancel()
                    updateParent=1;page=2;reveal=false;intent.removeExtra(OPEN_UPDATES)
                }
            }
            DisposableEffect(Unit){
                var monitor:AndroidNetworkMonitor?=null
                val providers=ProviderPackageMonitor(this@MainActivity,{tools.provider.packageName},{tools.refreshProvider()})
                fun start(){
                    providers.start()
                    if(monitor==null)monitor=AndroidNetworkMonitor(this@MainActivity,Handler(Looper.getMainLooper())){_,view->network=view}.also{it.start()}
                }
                val observer=LifecycleEventObserver{_,event->when(event){
                    Lifecycle.Event.ON_START->{ProxyState.recordDiagnostics=preferences.diagnostics;tools.refreshProvider();start()}
                    Lifecycle.Event.ON_STOP->{monitor?.close();monitor=null;providers.close();tools.cancelCheck();relayChecks.cancel();autoConfig.cancel()}
                    Lifecycle.Event.ON_PAUSE->reveal=false
                    Lifecycle.Event.ON_RESUME->tools.refreshProvider()
                    else->Unit
                }}
                lifecycle.addObserver(observer)
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)){ProxyState.recordDiagnostics=preferences.diagnostics;tools.refreshProvider();start()}
                onDispose{lifecycle.removeObserver(observer);monitor?.close();providers.close()}
            }
            DisposableEffect(reveal){
                if(reveal)window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                onDispose{window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)}
            }
            TgwsTheme(theme,glass,transparent){
                val dark=darkTheme(theme)
                val background=MaterialTheme.colorScheme.background.toArgb()
                SideEffect{
                    window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(background))
                    val bars=if(dark)SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                        else SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle=bars,navigationBarStyle=bars)
                }
                val snapshot=ProxyState.snapshot
                fun navigate(next:Int){if(next!=11)relayChecks.cancel();if(next!=10)autoConfig.cancel();reveal=false;page=next}
                fun copySecret(){form.withSaved(snapshot.running){copySensitive(getString(R.string.clipboard_secret),it.secret);form.feedbackResource.value=R.string.secret_copied}}
                fun copyLink(){form.withSaved(snapshot.running){copySensitive(getString(R.string.clipboard_link),SettingsValidation.telegramLink(it.port,it.secret,it.transport.fakeTlsDomain));form.feedbackResource.value=R.string.link_copied}}
                fun shareLink(){form.withSaved(snapshot.running){
                    val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,SettingsValidation.telegramLink(it.port,it.secret,it.transport.fakeTlsDomain))
                    try{startActivity(Intent.createChooser(send,getString(R.string.premium_share)))}
                    catch(_:ActivityNotFoundException){form.errorResource.value=R.string.error_telegram_missing}
                }}
                fun openTelegram(){form.withSaved(snapshot.running){
                    try{startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(SettingsValidation.telegramLink(it.port,it.secret,it.transport.fakeTlsDomain))))}
                    catch(_:ActivityNotFoundException){form.errorResource.value=R.string.error_telegram_missing}
                    catch(_:SecurityException){form.errorResource.value=R.string.error_telegram_missing}
                }}
                fun toggle(){
                    reveal=false;form.feedbackResource.value=null
                    if(snapshot.running){
                        try{startService(Intent(this@MainActivity,ProxyService::class.java).setAction(ProxyService.ACTION_STOP))}
                        catch(_:RuntimeException){form.errorResource.value=R.string.error_service_start}
                    }else if(!form.valid){form.errorResource.value=if(form.secretUnavailable.value)R.string.error_secret_unavailable else R.string.error_configuration}
                    else form.withSaved{
                        try{
                            if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),10)
                            startForegroundService(Intent(this@MainActivity,ProxyService::class.java).setAction(ProxyService.ACTION_START))
                        }catch(_:RuntimeException){form.errorResource.value=R.string.error_service_start}
                    }
                }
                fun probe(){
                    try{startService(Intent(this@MainActivity,ProxyService::class.java).setAction(ProxyService.ACTION_PROBE));form.feedbackResource.value=R.string.probe_requested}
                    catch(_:RuntimeException){form.errorResource.value=R.string.error_service_start}
                }
                if(page==2 || page==3){
                    TgwsUpdateHub(page,version,{navigate(it)},{navigate(updateParent)},{
                        if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),12)
                    })
                }else{
                    BackHandler(enabled=page!=0){navigate(if(page>=5)1 else 0)}
                    AppScaffold(page,{navigate(it)},version,dark,{
                        theme=if(dark)ThemeMode.LIGHT else ThemeMode.DARK;preferences.theme=theme
                    },onBack={navigate(1)},pages=pageStates){
                        if(form.busy.value){LinearProgressIndicator(Modifier.fillMaxWidth().testTag("settings.loading"));Text(stringResource(R.string.settings_loading),style=MaterialTheme.typography.bodySmall)}
                        val error=form.errorResource.value ?: snapshot.issue?.let(::issueResource)
                        if(error!=null)Text(if(snapshot.issueIsWarning && form.errorResource.value==null)stringResource(R.string.session_event,stringResource(error))else stringResource(error),color=if(snapshot.issueIsWarning && form.errorResource.value==null)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        form.feedbackResource.value?.let{Text(stringResource(it),style=MaterialTheme.typography.bodySmall)}
                        when(page){
                            0->PremiumHome(snapshot,network,form.portText.value,form.relayUrl.value,form.transportDraft.value,
                                !form.busy.value,form.valid,::toggle,::shareLink,::copySecret,::openTelegram,{navigate(if(form.transportDraft.value.relays.enabled)11 else 5)},{navigate(4)})
                            4->PremiumDiagnostics(snapshot.copy(network=network),ProxyState.diagnostics,::probe,{copySensitive(getString(R.string.diagnostics),it)},
                                recording=recording,report={DiagnosticReport.render(snapshot.copy(network=network),ProxyState.diagnostics,tools.provider,version,packageInfo.longVersionCode,Build.VERSION.SDK_INT)},
                                onShare={report->shareScope.launch{
                                    try{
                                        val share=withContext(Dispatchers.IO){DiagnosticExport.prepare(applicationContext,report)}
                                        if(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))startActivity(Intent.createChooser(share,getString(R.string.journal_share)))
                                    }catch(cancel:kotlinx.coroutines.CancellationException){throw cancel}
                                    catch(_:Exception){form.errorResource.value=R.string.journal_export_failed}
                                }},onClear={ProxyState.clearDiagnostics()}){
                                DiagnosticTools(snapshot.copy(network=network),tools.provider,tools.checking,tools.localResult,
                                    {tools.checkLocal(form.portText.value)},{tools.cancelCheck()},{tools.refreshProvider()})
                            }
                            5->ConnectionSettings(form,!snapshot.running && !form.busy.value,reveal,{reveal=!reveal},::copySecret,::copyLink)
                            6->SectionCard(stringResource(R.string.transport_settings)){
                                if(snapshot.running)Text(stringResource(R.string.stop_to_edit))
                                TransportEditor(form.transportDraft.value,{form.transportDraft.value=it},!snapshot.running && !form.busy.value,reveal,form.relayUrl.value)
                                SaveSettingsButton(form,!snapshot.running){reveal=false}
                            }
                            7->AppearanceSettings(theme,{theme=it;preferences.theme=it},glass,{glass=it;preferences.liquidGlass=it},transparent,{transparent=it;preferences.transparency=it})
                            8->AboutSettings(version,{url->startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))})
                            9->{DiagnosticOptions(recording,{
                                recording=it;preferences.diagnostics=it;ProxyState.recordDiagnostics=it
                            },snapshot.localAvailable,::probe)
                                SectionCard(stringResource(R.string.smart_alert_title)){
                                    SmartSwitch(R.string.smart_alert_enable,connectionAlerts,tag="smart.alerts"){
                                        connectionAlerts=it;preferences.connectionAlerts=it
                                        if(it && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
                                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),13)
                                        if(!it)ConnectionNotifications.clear(this@MainActivity)
                                    }
                                    Text(stringResource(R.string.smart_alert_notice),style=MaterialTheme.typography.bodySmall)
                                }
                            }
                            11->RelayManagerScreen(form,relayChecks,snapshot,!snapshot.running && !form.busy.value,reveal,{reveal=!reveal})
                            10->SmartConnectionSettings(form,!snapshot.running && !form.busy.value,network.kind,autoConfig)
                            else->PremiumSettings({next->
                                if(next==2 || next==3)updateParent=1
                                navigate(next)
                            },theme,form.transportDraft.value.mode,recording,form.transportDraft.value.profiles.enabled)
                        }
                    }
                }
            }
        }
    }
    private fun copySensitive(label:String,value:String){
        val clip=ClipData.newPlainText(label,value)
        clip.description.extras=PersistableBundle().apply{putBoolean(ClipDescription.EXTRA_IS_SENSITIVE,true)}
        getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
    }
}
