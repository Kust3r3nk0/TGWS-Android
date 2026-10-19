package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class UpdateWorkInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private fun await(condition:()->Boolean){val end=SystemClock.elapsedRealtime()+5000
        while(!condition()){check(SystemClock.elapsedRealtime()<end){"Update work state deadline"};SystemClock.sleep(20)}}
    @Test fun everySelectedIntervalIsScheduledAndDisablingCancelsUniqueWork(){
        val manager=WorkManager.getInstance(context);val scheduler=UpdateScheduler(context)
        manager.cancelUniqueWork(UpdateScheduler.NAME).result.get(5,TimeUnit.SECONDS)
        var id:java.util.UUID?=null
        try{for(minutes in UpdateOptions.periods){
            scheduler.configure(UpdateOptions(minutes=minutes,wifiOnly=true,notify=false))
            await{manager.getWorkInfosForUniqueWork(UpdateScheduler.NAME).get().any{it.state==WorkInfo.State.ENQUEUED && it.periodicityInfo?.repeatIntervalMillis==minutes*60000}}
            val work=manager.getWorkInfosForUniqueWork(UpdateScheduler.NAME).get().single{it.state==WorkInfo.State.ENQUEUED}
            assertEquals(NetworkType.CONNECTED,work.constraints.requiredNetworkType)
            if(id!=null)assertEquals(id,work.id);id=work.id
        }
            scheduler.configure(UpdateOptions(auto=false))
            await{manager.getWorkInfosForUniqueWork(UpdateScheduler.NAME).get().none{it.state==WorkInfo.State.ENQUEUED}}
        }finally{manager.cancelUniqueWork(UpdateScheduler.NAME).result.get(5,TimeUnit.SECONDS)}
    }
    @Test fun allUpdatePreferencesAndCachedResultsSurviveStoreRecreation(){
        val prefs=context.getSharedPreferences("tgws_update_options",Context.MODE_PRIVATE)
        val saved=prefs.all.toMap()
        try{
            val options=UpdateOptions(false,10080,false,true,UpdateChannel.PREVIEW)
            UpdateOptionsStore(context).save(options)
            assertEquals(options,UpdateOptionsStore(context).options())
            val history=UpdateHistory(12345,12340,13000,UpdateCheckResult.FAILED,UpdateIssue.RATE_LIMIT)
            UpdateOptionsStore(context).save(history)
            assertEquals(history,UpdateOptionsStore(context).history())
        }finally{prefs.edit().clear().commit();val edit=prefs.edit();saved.forEach{(k,v)->when(v){is String->edit.putString(k,v);is Boolean->edit.putBoolean(k,v);is Long->edit.putLong(k,v);is Set<*>->{@Suppress("UNCHECKED_CAST")edit.putStringSet(k,v as Set<String>)}}};edit.commit()}
    }
}
