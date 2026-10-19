package io.github.kust3r3nk0.tgwsandroid

internal const val UPDATE_REPOSITORY = "Kust3r3nk0/TGWS-Android"
internal const val UPDATE_PACKAGE = "io.github.kust3r3nk0.tgwsandroid"
internal const val OPEN_UPDATES = "tgws_open_updates"
internal const val MAX_UPDATE_BYTES = 160L * 1024 * 1024

enum class UpdateChannel { STABLE, PREVIEW }
data class UpdateOptions(val auto:Boolean=true,val minutes:Long=360,val notify:Boolean=true,
    val wifiOnly:Boolean=false,val channel:UpdateChannel=UpdateChannel.STABLE) {
    init { require(minutes in periods) }
    companion object { val periods=listOf(15L,60L,180L,360L,720L,1440L,10080L) }
}

/** SemVer precedence; the repository's legacy '-debug' is an asset-channel suffix. */
internal data class NumericVersion(val major:Int,val minor:Int,val patch:Int,val qualifiers:List<String>):Comparable<NumericVersion> {
    val pre get()=qualifiers.isNotEmpty()
    override fun compareTo(other:NumericVersion):Int {
        compareValuesBy(this,other,{it.major},{it.minor},{it.patch}).takeIf{it!=0}?.let{return it}
        if(!pre || !other.pre)return (!pre).compareTo(!other.pre)
        for(i in 0 until minOf(qualifiers.size,other.qualifiers.size)) {
            val a=qualifiers[i];val b=other.qualifiers[i]
            val an=a.all(Char::isDigit);val bn=b.all(Char::isDigit)
            val n=when {an&&bn->compareValuesBy(a,b,{it.length},{it});an->-1;bn->1;else->a.compareTo(b)}
            if(n!=0)return n
        }
        return qualifiers.size.compareTo(other.qualifiers.size)
    }
}
internal fun numericVersion(raw:String):NumericVersion? {
    val text=raw.removePrefix("v").removeSuffix("-debug")
    val match=Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?").matchEntire(text)?:return null
    val values=(1..3).map{match.groupValues[it].toIntOrNull()?:return null}
    val qualifiers=match.groupValues[4].takeIf{it.isNotEmpty()}?.split('.')?:emptyList()
    if(qualifiers.any{it.all(Char::isDigit)&&it.length>1&&it.startsWith('0')})return null
    return NumericVersion(values[0],values[1],values[2],qualifiers)
}

enum class ChangeCategory { IMPORTANT, FEATURES, FIXES, PERFORMANCE, TECHNICAL, GENERAL }
data class ReleaseSection(val category:ChangeCategory,val text:String)
/** Exact documented Markdown headings, never keyword classification of prose. */
internal fun releaseSections(notes:String):List<ReleaseSection> {
    val headings=mapOf("important" to ChangeCategory.IMPORTANT,"важное" to ChangeCategory.IMPORTANT,
        "features" to ChangeCategory.FEATURES,"новые функции" to ChangeCategory.FEATURES,
        "fixes" to ChangeCategory.FIXES,"исправления" to ChangeCategory.FIXES,
        "performance" to ChangeCategory.PERFORMANCE,"оптимизация" to ChangeCategory.PERFORMANCE,
        "technical" to ChangeCategory.TECHNICAL,"техническое" to ChangeCategory.TECHNICAL,
        "general" to ChangeCategory.GENERAL,"общее сведение" to ChangeCategory.GENERAL)
    val groups=linkedMapOf<ChangeCategory,MutableList<String>>()
    var category=ChangeCategory.GENERAL
    var fenced=false
    notes.take(64_000).lineSequence().forEach{line->
        if(line.trimStart().startsWith("```")){fenced=!fenced;groups.getOrPut(category){mutableListOf()}+=line}
        else if(!fenced && line.startsWith("## ")) {
            val known=headings[line.drop(3).trim().lowercase()]
            category=known?:ChangeCategory.GENERAL
            if(known==null)groups.getOrPut(category){mutableListOf()}+=line
        }else groups.getOrPut(category){mutableListOf()}+=line
    }
    return ChangeCategory.entries.mapNotNull{key->groups[key]?.joinToString("\n")?.trim()?.takeIf(String::isNotEmpty)?.let{ReleaseSection(key,it)}}
}

data class UpdateAsset(val name:String,val url:String,val size:Long)
data class GithubRelease(val id:Long,val tag:String,val version:String,val versionCode:Long,val minSdk:Int,
    val channel:UpdateChannel,val notes:String,val apk:UpdateAsset,val checksum:UpdateAsset) {
    val sections get()=releaseSections(notes)
}
enum class UpdateIssue { NETWORK, TIMEOUT, FORBIDDEN, NOT_FOUND, RATE_LIMIT, BAD_RESPONSE, NO_WIFI,
    CHECKSUM, INVALID_APK, WRONG_PACKAGE, OLD_VERSION, SIGNATURE, INCOMPATIBLE, STORAGE, INSTALLER }
internal class UpdateException(val issue:UpdateIssue,val retryAt:Long=0):java.io.IOException(issue.name)
enum class UpdateCheckResult { NEVER, AVAILABLE, CURRENT, FAILED }
data class UpdateState(val options:UpdateOptions=UpdateOptions(),val checking:Boolean=false,
    val release:GithubRelease?=null,val result:UpdateCheckResult=UpdateCheckResult.NEVER,
    val lastSuccess:Long=0,val retryAt:Long=0,val issue:UpdateIssue?=null,
    val downloading:Boolean=false,val downloaded:Long=0,val total:Long=0,val verifying:Boolean=false,
    val ready:Boolean=false,val installPermissionNeeded:Boolean=false)
