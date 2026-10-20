package io.github.kust3r3nk0.tgwsandroid

import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class UpdateSourceTest {
    @Test fun publishedNewerVersionAndCodeAreRequired()=runBlocking {
        val http=FixtureUpdateHttp();http.release("0.3.8",100);http.release("0.3.10",14);http.release("0.3.11",17)
        assertEquals("0.3.11",GithubUpdateSource(http).check("0.3.9-alpha",15,36,UpdateChannel.STABLE)?.version)
    }
    @Test fun stableDoesNotAcceptPreviewEvenWhenGithubPrereleaseFlagIsWrong()=runBlocking {
        val http=FixtureUpdateHttp();http.release("9.0.0-alpha.1",99,true).put("prerelease",false)
        assertNull(GithubUpdateSource(http).check("0.3.9-alpha",15,36,UpdateChannel.STABLE))
        assertEquals("9.0.0-alpha.1",GithubUpdateSource(http).check("0.3.9-alpha",15,36,UpdateChannel.PREVIEW)?.version)
    }
    @Test fun debugAssetCannotEnterStableChannel()=runBlocking {
        val http=FixtureUpdateHttp();val entry=http.release()
        val name="TGWS-Android-9.0.0-debug.apk"
        entry.getJSONArray("assets").getJSONObject(1).put("name",name)
            .put("browser_download_url","https://github.com/$UPDATE_REPOSITORY/releases/download/v9.0.0/$name")
        val manifest=entry.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
        http.manifests[manifest]=JSONObject(http.manifests.getValue(manifest)).put("apk",name).toString()
        assertNull(GithubUpdateSource(http).check("1.0.0",1,36,UpdateChannel.STABLE))
    }
    @Test fun draftIncompatibleAndMissingApkAreNotOffered()=runBlocking {
        val http=FixtureUpdateHttp();http.release("9.0.0",99).put("draft",true)
        http.release("8.0.0",98,minSdk=37);http.release("7.0.0",97).getJSONArray("assets").remove(1)
        assertNull(GithubUpdateSource(http).check("1.0.0",1,36,UpdateChannel.PREVIEW))
    }
    @Test fun foreignAssetAndNoteLinksNeverBecomeDownloadSources()=runBlocking {
        val http=FixtureUpdateHttp();val release=http.release()
        release.put("body","Install from https://evil.example/app.apk")
        release.getJSONArray("assets").getJSONObject(1).put("browser_download_url","https://evil.example/app.apk")
        assertNull(GithubUpdateSource(http).check("1.0.0",1,36,UpdateChannel.PREVIEW))
        assertFalse(trustedUpdateAsset("https://github.com/other/project/releases/download/v9/a.apk","v9","a.apk"))
        assertFalse(trustedUpdateAsset("https://github.com/$UPDATE_REPOSITORY/releases/download/v9/a.apk?x=1","v9","a.apk"))
        assertFalse(trustedUpdateAsset("http://github.com/$UPDATE_REPOSITORY/releases/download/v9/a.apk","v9","a.apk"))
    }
    @Test fun ambiguousChecksumIsRejected(){
        val hash="ab".repeat(32)
        assertEquals(hash,checksumFor("$hash  app.apk\n${"cd".repeat(32)}  other.apk","app.apk"))
        assertThrows(UpdateException::class.java){checksumFor("$hash  app.apk\n$hash  app.apk","app.apk")}
        assertThrows(UpdateException::class.java){checksumFor("$hash  other.apk","app.apk")}
    }
    @Test fun structuredHeadingsDoNotGuessCategoriesFromWords(){
        val sections=releaseSections("## Features\n- Fix is a feature name here.\n## Fixes\n- Added a safe check.\n## Performance\n- Less allocation.\n## Unknown\nExtra text")
        assertEquals(listOf(ChangeCategory.FEATURES,ChangeCategory.FIXES,ChangeCategory.PERFORMANCE,ChangeCategory.GENERAL),sections.map{it.category})
        assertEquals(ChangeCategory.GENERAL,releaseSections("- Исправлена ошибка без заголовка").single().category)
        assertEquals(emptyList<ReleaseSection>(),releaseSections(" \n"))
    }
    @Test fun semverOrdersNumericPrereleasesAndRejectsMalformedNumbers(){
        assertTrue(numericVersion("1.0.0-alpha.10")!!>numericVersion("1.0.0-alpha.2")!!)
        assertTrue(numericVersion("1.0.0")!!>numericVersion("1.0.0-rc.9")!!)
        assertEquals(numericVersion("0.3.10-alpha"),numericVersion("v0.3.10-alpha-debug"))
        for(raw in listOf("01.0.0","1.0.0-alpha.01","1.0.0.1","999999999999.0.0","1.0","latest"))assertNull(raw,numericVersion(raw))
    }
    @Test fun manifestVersionMustAgreeWithReleaseTag()=runBlocking {
        val http=FixtureUpdateHttp();val entry=http.release()
        val key=entry.getJSONArray("assets").getJSONObject(0).getString("browser_download_url")
        http.manifests[key]=JSONObject(http.manifests.getValue(key)).put("version_name","10.0.0").toString()
        assertNull(GithubUpdateSource(http).check("1.0.0",1,36,UpdateChannel.STABLE))
    }
    @Test fun malformedApiResponseProducesTypedFailure()=runBlocking {
        val http=object:UpdateHttp{
            override suspend fun text(url:String,limit:Int)="not JSON"
            override suspend fun download(url:String,target:File,expectedSize:Long,progress:(Long)->Unit)=error("Unused")
        }
        try{GithubUpdateSource(http).check("1.0.0",1,36,UpdateChannel.STABLE);fail()}
        catch(error:UpdateException){assertEquals(UpdateIssue.BAD_RESPONSE,error.issue)}
    }
}
