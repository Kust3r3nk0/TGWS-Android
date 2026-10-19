package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import org.json.JSONObject

internal data class UpdateHistory(val lastSuccess:Long=0,val lastAttempt:Long=0,val retryAt:Long=0,
    val result:UpdateCheckResult=UpdateCheckResult.NEVER,val issue:UpdateIssue?=null)
internal interface UpdatePersistence {
    fun options():UpdateOptions
    fun save(options:UpdateOptions)
    fun history():UpdateHistory
    fun save(history:UpdateHistory)
    fun cached():GithubRelease?
    fun cache(release:GithubRelease?)
    fun notified(id:Long):Boolean
    fun markNotified(id:Long)
}
/** Separate public update preferences: proxy/Keystore records are never touched. */
internal class UpdateOptionsStore(context:Context):UpdatePersistence {
    private val prefs=context.applicationContext.getSharedPreferences("tgws_update_options",Context.MODE_PRIVATE)
    override fun options()=UpdateOptions(prefs.getBoolean("auto",true),
        prefs.getLong("period",360).takeIf{it in UpdateOptions.periods}?:360,
        prefs.getBoolean("notify",true),prefs.getBoolean("wifi",false),
        runCatching{UpdateChannel.valueOf(prefs.getString("channel",null).orEmpty())}.getOrDefault(UpdateChannel.STABLE))
    override fun save(options:UpdateOptions){
        if(!prefs.edit().putBoolean("auto",options.auto).putLong("period",options.minutes)
            .putBoolean("notify",options.notify).putBoolean("wifi",options.wifiOnly)
            .putString("channel",options.channel.name).commit())throw UpdateException(UpdateIssue.STORAGE)
    }
    override fun history()=UpdateHistory(prefs.getLong("success",0),prefs.getLong("attempt",0),prefs.getLong("retry",0),
        runCatching{UpdateCheckResult.valueOf(prefs.getString("result",null).orEmpty())}.getOrDefault(UpdateCheckResult.NEVER),
        runCatching{UpdateIssue.valueOf(prefs.getString("issue",null).orEmpty())}.getOrNull())
    override fun save(history:UpdateHistory){
        if(!prefs.edit().putLong("success",history.lastSuccess).putLong("attempt",history.lastAttempt).putLong("retry",history.retryAt)
            .putString("result",history.result.name).putString("issue",history.issue?.name).commit())throw UpdateException(UpdateIssue.STORAGE)
    }
    override fun cached()=runCatching{prefs.getString("release",null)?.let{decodeRelease(JSONObject(it))}}.getOrNull()
    override fun cache(release:GithubRelease?){
        if(!prefs.edit().putString("release",release?.let{encodeRelease(it).toString()}).commit())throw UpdateException(UpdateIssue.STORAGE)
    }
    override fun notified(id:Long)=prefs.getStringSet("notified_ids",emptySet()).orEmpty().contains(id.toString())
    override fun markNotified(id:Long){
        val ids=prefs.getStringSet("notified_ids",emptySet()).orEmpty().toMutableSet()
        ids+=id.toString()
        if(!prefs.edit().putStringSet("notified_ids",ids.takeLastBounded(100)).commit())throw UpdateException(UpdateIssue.STORAGE)
    }
    private fun Set<String>.takeLastBounded(size:Int)=mapNotNull(String::toLongOrNull).sortedDescending().take(size).map(Long::toString).toSet()
}
internal fun encodeRelease(r:GithubRelease)=JSONObject().put("id",r.id).put("tag",r.tag).put("version",r.version)
    .put("code",r.versionCode).put("sdk",r.minSdk).put("channel",r.channel.name).put("notes",r.notes)
    .put("apk",JSONObject().put("name",r.apk.name).put("url",r.apk.url).put("size",r.apk.size))
    .put("checksum",JSONObject().put("name",r.checksum.name).put("url",r.checksum.url).put("size",r.checksum.size))
internal fun decodeRelease(j:JSONObject):GithubRelease {
    fun asset(name:String)=j.getJSONObject(name).let{UpdateAsset(it.getString("name"),it.getString("url"),it.getLong("size"))}
    return GithubRelease(j.getLong("id"),j.getString("tag"),j.getString("version"),j.getLong("code"),j.getInt("sdk"),
        UpdateChannel.valueOf(j.getString("channel")),j.getString("notes"),asset("apk"),asset("checksum"))
}
