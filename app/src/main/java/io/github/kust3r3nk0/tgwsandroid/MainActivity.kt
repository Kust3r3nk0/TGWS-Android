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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
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
        val version=packageManager.getPackageInfo(packageName,0).versionName.orEmpty()
        setContent {
            val form:ProxyFormModel=viewModel()
            val preferences=remember{AppPreferences(this@MainActivity)}
            var theme by remember{mutableStateOf(preferences.theme)}
            var glass by remember{mutableStateOf(preferences.liquidGlass)}
            var transparent by remember{mutableStateOf(preferences.transparency)}
            var recording by remember{mutableStateOf(preferences.diagnostics)}
            var reveal by remember{mutableStateOf(false)}
            var page by rememberSaveable{mutableIntStateOf(if(intent.getBooleanExtra(OPEN_UPDATES,false))2 else 0)}
            var updateParent by rememberSaveable{mutableIntStateOf(1)}
            var network by remember{mutableStateOf(NetworkView())}
            LaunchedEffect(updateRequest){
                if(updateRequest>0 || intent.getBooleanExtra(OPEN_UPDATES,false)){
                    updateParent=1;page=2;reveal=false;intent.removeExtra(OPEN_UPDATES)
                }
            }
            DisposableEffect(Unit){
                var monitor:AndroidNetworkMonitor?=null
                fun start(){if(monitor==null)monitor=AndroidNetworkMonitor(this@MainActivity,Handler(Looper.getMainLooper())){_,view->network=view}.also{it.start()}}
                val observer=LifecycleEventObserver{_,event->when(event){
                    Lifecycle.Event.ON_START->start()
                    Lifecycle.Event.ON_STOP->{monitor?.close();monitor=null}
                    Lifecycle.Event.ON_PAUSE->reveal=false
                    else->Unit
                }}
                lifecycle.addObserver(observer)
                if(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))start()
                onDispose{lifecycle.removeObserver(observer);monitor?.close()}
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
                fun navigate(next:Int){reveal=false;page=next}
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
                    },onBack={navigate(1)}){
                        if(form.busy.value){LinearProgressIndicator(Modifier.fillMaxWidth().testTag("settings.loading"))}
                        val error=form.errorResource.value ?: snapshot.issue?.let(::issueResource)
                        if(error!=null)Text(stringResource(error),color=if(snapshot.issueIsWarning && form.errorResource.value==null)MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        form.feedbackResource.value?.let{Text(stringResource(it),style=MaterialTheme.typography.bodySmall)}
                        when(page){
                            0->PremiumHome(snapshot,network,form.portText.value,form.relayUrl.value,form.transportDraft.value,
                                !form.busy.value,form.valid,::toggle,::shareLink,::copySecret,::openTelegram,{navigate(5)},{navigate(4)})
                            4->PremiumDiagnostics(snapshot.copy(network=network),ProxyState.diagnostics,::probe,{copySensitive(getString(R.string.diagnostics),it)})
                            5->ConnectionSettings(form,!snapshot.running && !form.busy.value,reveal,{reveal=!reveal},::copySecret,::copyLink)
                            6->SectionCard(stringResource(R.string.transport_settings)){
                                if(snapshot.running)Text(stringResource(R.string.stop_to_edit))
                                TransportEditor(form.transportDraft.value,{form.transportDraft.value=it},!snapshot.running && !form.busy.value,reveal,form.relayUrl.value)
                                SaveSettingsButton(form,!snapshot.running){reveal=false}
                            }
                            7->AppearanceSettings(theme,{theme=it;preferences.theme=it},glass,{glass=it;preferences.liquidGlass=it},transparent,{transparent=it;preferences.transparency=it})
                            8->AboutSettings(version,{url->startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(url)))})
                            9->DiagnosticOptions(recording,{
                                recording=it;preferences.diagnostics=it;ProxyState.recordDiagnostics=it
                            },snapshot.localAvailable,::probe)
                            else->PremiumSettings({next->
                                if(next==2 || next==3)updateParent=1
                                navigate(next)
                            },theme,form.transportDraft.value.mode,recording)
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
