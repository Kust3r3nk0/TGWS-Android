package io.github.kust3r3nk0.tgwsandroid

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class UpdateRepositoryTest {
    @Test fun rejectedCachedApkCanBeDownloadedAgain()=runBlocking {
        val repo=UpdateRepository(MemoryUpdatePersistence(),GithubUpdateSource(FixtureUpdateHttp()),"1.0.0",1,36)
        repo.initialize();repo.ready(true);repo.permission(true)
        repo.error(UpdateIssue.INVALID_APK)
        assertFalse(repo.state.value.ready)
        assertFalse(repo.state.value.installPermissionNeeded)
        assertEquals(UpdateIssue.INVALID_APK,repo.state.value.issue)
        repo.downloading(500)
        assertTrue(repo.state.value.downloading);assertNull(repo.state.value.issue)
    }
    @Test fun changingSettingsDoesNotWaitForSlowGithub()=runBlocking {
        val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val http=object:UpdateHttp {
            override suspend fun text(url:String,limit:Int):String{started.complete(Unit);finish.await();return "[]"}
            override suspend fun download(url:String,target:java.io.File,expectedSize:Long,progress:(Long)->Unit)=error("Unused")
        }
        val storage=MemoryUpdatePersistence();val repo=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36)
        repo.initialize();val job=launch{repo.check()}
        try{started.await();withTimeout(500){repo.options(UpdateOptions(auto=false))};assertFalse(storage.options().auto)}
        finally{finish.complete(Unit);job.join()}
    }
    @Test fun oldChannelResponseCannotOverwriteNewChannelSelection()=runBlocking {
        val fixture=FixtureUpdateHttp().apply{release("9.0.0-alpha",99,true)}
        val started=CompletableDeferred<Unit>();val finish=CompletableDeferred<Unit>()
        val http=object:UpdateHttp {
            override suspend fun text(url:String,limit:Int):String{started.complete(Unit);finish.await();return fixture.text(url,limit)}
            override suspend fun download(url:String,target:java.io.File,expectedSize:Long,progress:(Long)->Unit)=error("Unused")
        }
        val storage=MemoryUpdatePersistence().apply{preferences=UpdateOptions(channel=UpdateChannel.PREVIEW)}
        var notified=0
        val repo=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36,notify={notified++;true})
        repo.initialize();val job=launch{repo.check()}
        started.await();repo.options(UpdateOptions(channel=UpdateChannel.STABLE));finish.complete(Unit);job.join()
        assertNull(repo.state.value.release);assertEquals(UpdateChannel.STABLE,repo.state.value.options.channel)
        assertEquals(UpdateCheckResult.NEVER,repo.state.value.result);assertEquals(0,notified)
    }
    @Test fun checksAreThrottledAndNotificationsAreDeduplicated()=runBlocking {
        val http=FixtureUpdateHttp().apply{release()};val storage=MemoryUpdatePersistence();var now=100000L;var delivered=0
        val repo=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36,{now},notify={delivered++;true})
        repo.initialize();repo.check();val requests=http.requests
        repo.check();assertEquals(requests,http.requests);assertEquals(1,delivered)
        now+=61000;repo.check();assertEquals(1,delivered)
        assertEquals(now,repo.state.value.lastSuccess)
    }
    @Test fun deniedNotificationCanBeDeliveredLaterWithoutDisablingChecks()=runBlocking {
        val http=FixtureUpdateHttp().apply{release()};val storage=MemoryUpdatePersistence();var now=100000L;var allowed=false;var delivered=0
        val repo=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36,{now},notify={if(allowed){delivered++;true}else false})
        repo.initialize();repo.check();assertTrue(storage.notifications.isEmpty())
        allowed=true;now+=61000;repo.check();assertEquals(1,delivered)
        now+=61000;repo.options(repo.state.value.options.copy(notify=false,wifiOnly=true,auto=false));repo.check()
        assertEquals(UpdateCheckResult.AVAILABLE,repo.state.value.result);assertEquals(1,delivered)
    }
    @Test fun rateLimitPersistsAcrossRepositoryRecreation()=runBlocking {
        val http=FixtureUpdateHttp().apply{failure=UpdateException(UpdateIssue.RATE_LIMIT,1000000)};val storage=MemoryUpdatePersistence()
        val repo=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36,{100000})
        repo.initialize();try{repo.check();fail()}catch(_:UpdateException){}
        assertEquals(UpdateIssue.RATE_LIMIT,repo.state.value.issue)
        val requests=http.requests
        val recreated=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36,{200000})
        recreated.initialize();recreated.check();assertEquals(requests,http.requests)
        assertEquals(UpdateCheckResult.FAILED,recreated.state.value.result)
    }
    @Test fun cachedResultSurvivesRecreationAndChannelChangesClearIt()=runBlocking {
        val http=FixtureUpdateHttp().apply{release("9.0.0-alpha",99,true)};val storage=MemoryUpdatePersistence().apply{preferences=UpdateOptions(channel=UpdateChannel.PREVIEW)}
        val changes=mutableListOf<UpdateOptions>()
        val repo=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36,{100000},schedule={changes+=it})
        repo.initialize();repo.check()
        val recreated=UpdateRepository(storage,GithubUpdateSource(http),"1.0.0",1,36,{100000})
        recreated.initialize();assertNotNull(recreated.state.value.release)
        repo.options(UpdateOptions(auto=false,minutes=15));assertNull(repo.state.value.release)
        assertEquals(listOf(UpdateOptions(auto=false,minutes=15)),changes)
    }
    @Test fun repeatedInitializationCannotEraseActiveDownload()=runBlocking {
        val repo=UpdateRepository(MemoryUpdatePersistence(),GithubUpdateSource(FixtureUpdateHttp()),"1.0.0",1,36)
        repo.initialize();repo.downloading(500);repo.progress(123);repo.initialize()
        assertTrue(repo.state.value.downloading);assertEquals(123L,repo.state.value.downloaded)
    }
}
