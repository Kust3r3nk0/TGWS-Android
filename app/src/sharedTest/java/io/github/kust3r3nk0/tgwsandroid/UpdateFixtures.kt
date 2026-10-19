package io.github.kust3r3nk0.tgwsandroid

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal class FixtureUpdateHttp:UpdateHttp {
    var requests=0
    var failure:UpdateException?=null
    val releases=JSONArray()
    val manifests=mutableMapOf<String,String>()
    fun release(version:String="9.0.0",code:Long=99,preview:Boolean=false,minSdk:Int=36,id:Long=99):JSONObject {
        val tag="v$version"+if(preview)"-debug"else ""
        val apk="TGWS-Android-$version"+if(preview)"-debug.apk"else ".apk"
        fun asset(name:String,size:Long)=JSONObject().put("name",name).put("size",size)
            .put("browser_download_url","https://github.com/$UPDATE_REPOSITORY/releases/download/$tag/$name")
        val manifest=asset("update-manifest.json",512)
        manifests[manifest.getString("browser_download_url")]=JSONObject().put("schema",1).put("package_name",UPDATE_PACKAGE)
            .put("version_name",version).put("version_code",code).put("min_sdk",minSdk).put("channel",if(preview)"PREVIEW"else "STABLE").put("apk",apk).toString()
        val entry=JSONObject().put("id",id).put("draft",false).put("prerelease",preview).put("tag_name",tag)
            .put("body","## Fixes\n- Preserve the existing connection.").put("assets",JSONArray().put(manifest).put(asset(apk,100)).put(asset("SHA256SUMS",200)))
        releases.put(entry);return entry
    }
    override suspend fun text(url:String,limit:Int):String {requests++;failure?.let{throw it}
        return if(url.startsWith("https://api.github.com/"))releases.toString() else manifests[url]?:error("Unexpected asset request")}
    override suspend fun download(url:String,target:File,expectedSize:Long,progress:(Long)->Unit)=error("Not used")
}

internal class MemoryUpdatePersistence:UpdatePersistence {
    var preferences=UpdateOptions();var previous=UpdateHistory();var release:GithubRelease?=null;val notifications=mutableSetOf<Long>()
    override fun options()=preferences
    override fun save(options:UpdateOptions){preferences=options}
    override fun history()=previous
    override fun save(history:UpdateHistory){previous=history}
    override fun cached()=release
    override fun cache(release:GithubRelease?){this.release=release}
    override fun notified(id:Long)=id in notifications
    override fun markNotified(id:Long){notifications+=id}
}
