package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

internal class UpdateDownloads(private val context:Context,private val verifier:UpdateApkVerifier=AndroidUpdateApkVerifier(context)) {
    private val verified=File(context.cacheDir,"verified_updates")
    private val pending=File(context.cacheDir,"pending_updates")
    private val prefs=context.getSharedPreferences("tgws_update_download",Context.MODE_PRIVATE)
    private val connectivity=context.getSystemService(ConnectivityManager::class.java)
    private fun wifi(network:Network?):Boolean {
        val caps=network?.let(connectivity::getNetworkCapabilities)?:return false
        // Android propagates underlying transports into a VPN's capabilities. Do
        // not scan unrelated networks or use hidden underlying-network APIs.
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) && !caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }
    suspend fun download(release:GithubRelease,wifiOnly:Boolean,progress:(Long)->Unit,verifying:()->Unit):File=withContext(Dispatchers.IO){
        if(!trustedUpdateAsset(release.apk.url,release.tag,release.apk.name) ||
            !trustedUpdateAsset(release.checksum.url,release.tag,release.checksum.name))throw UpdateException(UpdateIssue.BAD_RESPONSE)
        val network=connectivity.activeNetwork?:throw UpdateException(UpdateIssue.NETWORK)
        if(wifiOnly && !wifi(network))throw UpdateException(UpdateIssue.NO_WIFI)
        // Bind both DNS and sockets to this active network. Losing it cannot silently
        // move the APK onto a different connection; a VPN remains the active route.
        val client=OkHttpClient.Builder().socketFactory(network.socketFactory)
            .dns{host->network.getAllByName(host).toList()}.followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS).callTimeout(10,TimeUnit.MINUTES).build()
        val http=GithubUpdateHttp(client)
        val tmp=File(pending,"pending.apk")
        val constraintLost=java.util.concurrent.atomic.AtomicBoolean()
        val callback=object:ConnectivityManager.NetworkCallback(){
            override fun onLost(lost:Network){if(lost==network)client.dispatcher.cancelAll()}
            override fun onCapabilitiesChanged(changed:Network,caps:NetworkCapabilities){
                if(wifiOnly && changed==network && (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))){
                    constraintLost.set(true);client.dispatcher.cancelAll()
                }
            }
        }
        var registered=false
        try{
            connectivity.registerDefaultNetworkCallback(callback);registered=true
            val sha=checksumFor(http.text(release.checksum.url,100_000),release.apk.name)
            if(!pending.exists()&&!pending.mkdirs() || !verified.exists()&&!verified.mkdirs())throw UpdateException(UpdateIssue.STORAGE)
            val target=File(verified,"update-$sha.apk")
            currentCoroutineContext().ensureActive()
            http.download(release.apk.url,tmp,release.apk.size){bytes->
                if(wifiOnly && (connectivity.activeNetwork!=network || !wifi(network)))throw UpdateException(UpdateIssue.NO_WIFI)
                progress(bytes)
            }
            currentCoroutineContext().ensureActive();verifying()
            verifyUpdateChecksum(tmp,sha)
            verifier.verify(tmp,release)
            currentCoroutineContext().ensureActive()
            if(target.exists()&&!target.delete())throw UpdateException(UpdateIssue.STORAGE)
            if(!tmp.renameTo(target))throw UpdateException(UpdateIssue.STORAGE)
            if(!prefs.edit().putString("release",encodeRelease(release).toString()).putString("sha",sha).commit())
                throw UpdateException(UpdateIssue.STORAGE)
            verified.listFiles()?.filter{it!=target}?.forEach{it.delete()}
            target
        }catch(error:Exception){tmp.delete();if(error is CancellationException)throw error
            if(constraintLost.get())throw UpdateException(UpdateIssue.NO_WIFI);throw error}
        finally{if(registered)connectivity.unregisterNetworkCallback(callback);client.dispatcher.cancelAll();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
    suspend fun ready(release:GithubRelease):File?=withContext(Dispatchers.IO){
        val saved=runCatching{prefs.getString("release",null)?.let{decodeRelease(org.json.JSONObject(it))}}.getOrNull()
        if(saved!=release)return@withContext null
        val sha=prefs.getString("sha",null)?.takeIf{it.matches(Regex("[0-9a-f]{64}"))}?:return@withContext null
        val file=File(verified,"update-$sha.apk")
        try{if(!file.isFile || file.length()!=release.apk.size || sha256(file)!=sha)throw UpdateException(UpdateIssue.CHECKSUM)
            verifier.verify(file,release);file
        }catch(_:UpdateException){file.delete();prefs.edit().clear().commit();null}
    }
}
