package io.github.kust3r3nk0.tgwsandroid

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

internal fun trustedUpdateAsset(url:String,tag:String,name:String):Boolean {
    val u=url.toHttpUrlOrNull()?:return false
    return u.scheme=="https" && u.host=="github.com" && u.port==443 && u.username.isEmpty() &&
        u.password.isEmpty() && u.query==null && u.fragment==null &&
        u.pathSegments==UPDATE_REPOSITORY.split('/')+listOf("releases","download",tag,name) &&
        !tag.contains('/') && name.matches(Regex("[A-Za-z0-9._-]{1,180}"))
}

internal interface UpdateHttp {
    suspend fun text(url:String,limit:Int=2_000_000):String
    suspend fun download(url:String,target:File,expectedSize:Long,progress:(Long)->Unit)
}

/** No credentials, TLS exceptions, arbitrary note URLs, or unbounded response buffers. */
internal class GithubUpdateHttp(private val client:OkHttpClient=OkHttpClient.Builder()
    .connectTimeout(15,TimeUnit.SECONDS).readTimeout(20,TimeUnit.SECONDS)
    .callTimeout(10,TimeUnit.MINUTES).followRedirects(false).followSslRedirects(false).build()):UpdateHttp {
    private suspend fun <T> read(url:String,redirects:Int=0,deadline:Long=System.nanoTime()+TimeUnit.SECONDS.toNanos(60),consume:(Response,()->Unit)->T):T {
        if(redirects>4)throw UpdateException(UpdateIssue.BAD_RESPONSE)
        val remaining=TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime())
        if(remaining<=0)throw UpdateException(UpdateIssue.TIMEOUT)
        val result=suspendCancellableCoroutine<Pair<T?,String?>> { continuation ->
            val call=client.newCall(Request.Builder().url(url)
                .header("User-Agent","TGWS-Android-UpdateCenter")
                .header("Accept","application/vnd.github+json")
                .header("X-GitHub-Api-Version","2026-03-10").build())
            call.timeout().timeout(minOf(remaining,client.callTimeoutMillis.takeIf{it>0}?.toLong()?:remaining),TimeUnit.MILLISECONDS)
            continuation.invokeOnCancellation{call.cancel()}
            call.enqueue(object:Callback {
                override fun onFailure(call:Call,e:IOException){
                    if(continuation.isActive)continuation.resumeWithException(if(e is java.io.InterruptedIOException)
                        UpdateException(UpdateIssue.TIMEOUT) else UpdateException(UpdateIssue.NETWORK))
                }
                override fun onResponse(call:Call,response:Response){
                    try{response.use { reply ->
                        if(reply.code in listOf(301,302,303,307,308)) {
                            val next=reply.header("Location")?.toHttpUrlOrNull()?:throw UpdateException(UpdateIssue.BAD_RESPONSE)
                            if(next.scheme!="https" || next.port!=443 || next.username.isNotEmpty() || next.password.isNotEmpty() ||
                                next.host !in setOf("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com"))
                                throw UpdateException(UpdateIssue.BAD_RESPONSE)
                            if(continuation.isActive)continuation.resume(null to next.toString())
                        }else{
                            if(!reply.isSuccessful)throw httpFailure(reply)
                            val value=consume(reply){continuation.context.ensureActive()}
                            if(continuation.isActive)continuation.resume(value to null)
                        }
                    }}catch(e:Exception){if(continuation.isActive)continuation.resumeWithException(
                        if(e is UpdateException)e else if(e is java.io.InterruptedIOException)UpdateException(UpdateIssue.TIMEOUT)
                        else UpdateException(UpdateIssue.NETWORK))}
                }
            })
        }
        result.second?.let{return read(it,redirects+1,deadline,consume)}
        @Suppress("UNCHECKED_CAST") return result.first as T
    }
    private fun httpFailure(reply:Response):UpdateException {
        val now=System.currentTimeMillis()
        val retry=(reply.header("Retry-After")?.toLongOrNull()?.coerceIn(60,86400)?.times(1000)?.plus(now)
            ?:reply.header("X-RateLimit-Reset")?.toLongOrNull()?.times(1000)?.coerceIn(now+60000,now+86400000)
            ?:now+15*60000)
        return when(reply.code){
            403->UpdateException(if(reply.header("X-RateLimit-Remaining")=="0")UpdateIssue.RATE_LIMIT else UpdateIssue.FORBIDDEN,retry)
            404->UpdateException(UpdateIssue.NOT_FOUND)
            429->UpdateException(UpdateIssue.RATE_LIMIT,retry)
            else->UpdateException(UpdateIssue.NETWORK)
        }
    }
    override suspend fun text(url:String,limit:Int):String=read(url){reply,active->
        val body=reply.body
        if(body.contentLength()>limit)throw UpdateException(UpdateIssue.BAD_RESPONSE)
        val out=java.io.ByteArrayOutputStream()
        body.byteStream().use{input->val b=ByteArray(16384);while(true){active();val n=input.read(b);if(n<0)break
            if(out.size()+n>limit)throw UpdateException(UpdateIssue.BAD_RESPONSE);out.write(b,0,n)}}
        out.toString("UTF-8")
    }
    override suspend fun download(url:String,target:File,expectedSize:Long,progress:(Long)->Unit){
        read(url,deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(10)){reply,active->
            val body=reply.body
            if(body.contentLength()>MAX_UPDATE_BYTES || body.contentLength()>=0 && body.contentLength()!=expectedSize)
                throw UpdateException(UpdateIssue.INVALID_APK)
            var total=0L
            body.byteStream().use{input->FileOutputStream(target).use{output->val b=ByteArray(65536)
                while(true){active();val n=input.read(b);if(n<0)break;total+=n
                    if(total>MAX_UPDATE_BYTES || total>expectedSize)throw UpdateException(UpdateIssue.INVALID_APK)
                    output.write(b,0,n);progress(total)}
                output.fd.sync()
            }}
            if(total!=expectedSize || total==0L)throw UpdateException(UpdateIssue.INVALID_APK)
        }
    }
}

internal fun checksumFor(text:String,name:String):String {
    val matches=text.lineSequence().mapNotNull{line->Regex("([0-9a-fA-F]{64})[ \\t]+\\*?([^\\r\\n]+)")
        .matchEntire(line.trim())?.takeIf{it.groupValues[2]==name}?.groupValues?.get(1)?.lowercase()}.toList()
    if(matches.size!=1)throw UpdateException(UpdateIssue.CHECKSUM)
    return matches.single()
}
internal fun sha256(file:File):String {
    val digest=MessageDigest.getInstance("SHA-256")
    file.inputStream().use{input->val b=ByteArray(65536);while(true){val n=input.read(b);if(n<0)break;digest.update(b,0,n)}}
    return digest.digest().joinToString(""){"%02x".format(it)}
}
internal fun verifyUpdateChecksum(file:File,expected:String){
    if(!expected.matches(Regex("[0-9a-fA-F]{64}")) || !sha256(file).equals(expected,true))throw UpdateException(UpdateIssue.CHECKSUM)
}
/** Reject compressed manifest/resource bombs before any signature or package parser. */
internal fun validateUpdateArchive(file:File){
    try{java.util.zip.ZipFile(file).use{zip->
        if(zip.size()>25000)throw UpdateException(UpdateIssue.INVALID_APK)
        var total=0L;var manifests=0
        val entries=zip.entries()
        while(entries.hasMoreElements()){
            val entry=entries.nextElement();total+=entry.size.coerceAtLeast(0)
            if(entry.size<0 || entry.size>64L*1024*1024 || total>512L*1024*1024)throw UpdateException(UpdateIssue.INVALID_APK)
            if(entry.name=="AndroidManifest.xml"){manifests++;if(entry.size !in 1..2L*1024*1024)throw UpdateException(UpdateIssue.INVALID_APK)}
            if(entry.name=="resources.arsc" && entry.size>32L*1024*1024)throw UpdateException(UpdateIssue.INVALID_APK)
        }
        if(manifests!=1)throw UpdateException(UpdateIssue.INVALID_APK)
    }}catch(error:UpdateException){throw error}catch(_:java.io.IOException){throw UpdateException(UpdateIssue.INVALID_APK)}
}

/** Only release-owned assets and a documented manifest can advertise an APK. */
internal class GithubUpdateSource(private val http:UpdateHttp) {
    suspend fun check(installedVersion:String,installedCode:Long,sdk:Int,channel:UpdateChannel):GithubRelease? {
        val installed=numericVersion(installedVersion)?:throw UpdateException(UpdateIssue.BAD_RESPONSE)
        val entries=try{JSONArray(http.text("https://api.github.com/repos/$UPDATE_REPOSITORY/releases?per_page=100"))}
            catch(e:org.json.JSONException){throw UpdateException(UpdateIssue.BAD_RESPONSE)}
        if(entries.length()>100)throw UpdateException(UpdateIssue.BAD_RESPONSE)
        val eligible=(0 until entries.length()).mapNotNull{entries.optJSONObject(it)}.filter{entry->
            val v=numericVersion(entry.optString("tag_name"))
            entry.optLong("id",-1)>0 && !entry.optBoolean("draft",true) && v!=null && v>installed &&
                (channel==UpdateChannel.PREVIEW || !entry.optBoolean("prerelease",true) && !v.pre && !entry.optString("tag_name").endsWith("-debug"))
        }.sortedWith(compareByDescending{numericVersion(it.optString("tag_name"))}).take(8)
        val candidates=mutableListOf<GithubRelease>()
        for(entry in eligible) {
            val tag=entry.optString("tag_name")
            val assets=entry.optJSONArray("assets")?:continue
            val names=mutableMapOf<String,UpdateAsset>()
            for(i in 0 until minOf(assets.length(),40)) {
                val a=assets.optJSONObject(i)?:continue
                val name=a.optString("name");val url=a.optString("browser_download_url");val size=a.optLong("size",-1)
                if(!trustedUpdateAsset(url,tag,name) || size !in 1..MAX_UPDATE_BYTES)continue
                if(names.put(name,UpdateAsset(name,url,size))!=null)throw UpdateException(UpdateIssue.BAD_RESPONSE)
            }
            val manifest=names["update-manifest.json"]?:continue
            if(manifest.size>16_384)continue
            val metadata=try{JSONObject(http.text(manifest.url,16_384))}catch(e:org.json.JSONException){throw UpdateException(UpdateIssue.BAD_RESPONSE)}
            val version=metadata.optString("version_name")
            val v=numericVersion(version)?:continue
            val code=metadata.optLong("version_code",-1)
            val minSdk=metadata.optInt("min_sdk",-1)
            val kind=runCatching{UpdateChannel.valueOf(metadata.optString("channel"))}.getOrNull()?:continue
            val apk=names[metadata.optString("apk")]?:continue
            val sums=names["SHA256SUMS"]?:continue
            if(metadata.opt("version_code") !is Int && metadata.opt("version_code") !is Long || metadata.opt("min_sdk") !is Int ||
                metadata.optInt("schema")!=1 || metadata.optString("package_name")!=UPDATE_PACKAGE ||
                v!=numericVersion(tag) || v<=installed || code<=installedCode || minSdk !in 36..sdk ||
                !apk.name.startsWith("TGWS-Android-") || !apk.name.endsWith(".apk") ||
                channel==UpdateChannel.STABLE && kind!=UpdateChannel.STABLE ||
                kind==UpdateChannel.STABLE && (v.pre || tag.endsWith("-debug") || apk.name.contains("debug",true) || entry.optBoolean("prerelease",true)))continue
            if(sums.size>100_000)continue
            candidates+=GithubRelease(entry.optLong("id"),tag,version,code,minSdk,kind,entry.optString("body").take(64000),apk,sums)
        }
        return candidates.maxWithOrNull(compareBy<GithubRelease>{numericVersion(it.version)}.thenBy{it.versionCode})
    }
}
