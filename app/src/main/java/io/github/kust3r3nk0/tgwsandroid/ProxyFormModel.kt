package io.github.kust3r3nk0.tgwsandroid

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Sensitive drafts survive Activity recreation in memory, never in savedInstanceState. */
internal class ProxyFormModel(application: Application) : AndroidViewModel(application) {
    val portText = mutableStateOf(SettingsValidation.DEFAULT_PORT.toString())
    val secretText = mutableStateOf("")
    val relayUrl = mutableStateOf("")
    val transportDraft = mutableStateOf(TransportDraft())
    val imported = mutableStateOf("")
    val secretUnavailable = mutableStateOf(false)
    val busy = mutableStateOf(true)
    val errorResource = mutableStateOf<Int?>(null)
    val feedbackResource = mutableStateOf<Int?>(null)
    private val store = SettingsStore(application)
    val configuration get()=runCatching{transportDraft.value.settings()}.getOrNull()
    val valid get()=configuration!=null && portText.value.toIntOrNull()?.let(SettingsValidation::validPort)==true &&
        SettingsValidation.validSecret(secretText.value) && !secretUnavailable.value &&
        (configuration!!.mode!=io.github.kust3r3nk0.tgwsandroid.core.TransportMode.RELAY_ONLY || relayUrl.value.isNotEmpty()) &&
        runCatching{io.github.kust3r3nk0.tgwsandroid.core.RelayEndpoint.parse(relayUrl.value)}.isSuccess

    fun withSaved(running:Boolean=false,action:(StoredSettings)->Unit={feedbackResource.value=R.string.settings_saved}) {
        if(busy.value || !valid)return
        val draft=StoredSettings(portText.value.toInt(),secretText.value,relayUrl.value,requireNotNull(configuration))
        busy.value=true;feedbackResource.value=null
        viewModelScope.launch {
            try {
                val saved=withContext(Dispatchers.IO){
                    if(!running)store.save(draft.port,draft.secret,draft.relayUrl,draft.transport)
                    store.load()
                }
                secretText.value=saved.secret;errorResource.value=null;action(saved)
            }catch(error:SettingsException){
                errorResource.value=settingsError(error.issue)
                if(error.issue==SettingsIssue.SECRET_UNAVAILABLE)secretUnavailable.value=true
            }catch(error:CancellationException){throw error}
            catch(_:RuntimeException){errorResource.value=R.string.error_storage}
            finally{busy.value=false}
        }
    }
    fun importSecret(){
        try {
            val text=imported.value
            val decoded=if(text.startsWith("tg://") || text.startsWith("https://")){
                val link=io.github.kust3r3nk0.tgwsandroid.core.TelegramSecret.import(text)
                portText.value=link.port.toString();link.decoded
            }else io.github.kust3r3nk0.tgwsandroid.core.TelegramSecret.decode(text)
            secretText.value=decoded.secret;transportDraft.value=transportDraft.value.copy(fakeDomain=decoded.domain)
            imported.value="";feedbackResource.value=R.string.import_success;errorResource.value=null
        }catch(_:IllegalArgumentException){errorResource.value=R.string.import_invalid}
    }
    fun generate(){
        if(busy.value)return
        val generated=SettingsValidation.generateSecret()
        if(!secretUnavailable.value){secretText.value=generated;errorResource.value=null;return}
        val port=portText.value.toIntOrNull()
        if(port==null || !SettingsValidation.validPort(port)){errorResource.value=R.string.error_configuration;return}
        busy.value=true
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO){store.recover(port,generated,relayUrl.value,configuration)}
                secretText.value=generated;secretUnavailable.value=false;errorResource.value=null
                feedbackResource.value=R.string.secret_recovered
            }catch(error:SettingsException){errorResource.value=settingsError(error.issue)}
            finally{busy.value=false}
        }
    }
    init {
        viewModelScope.launch {
            try {
                val saved = withContext(Dispatchers.IO) { SettingsStore(application).load() }
                portText.value = saved.port.toString()
                secretText.value = saved.secret
                relayUrl.value = saved.relayUrl
                transportDraft.value = TransportDraft.from(saved.transport)
            } catch (error: SettingsException) {
                secretUnavailable.value = error.issue == SettingsIssue.SECRET_UNAVAILABLE
                errorResource.value = settingsError(error.issue)
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { errorResource.value = R.string.error_storage }
            finally { busy.value = false }
        }
    }
}
