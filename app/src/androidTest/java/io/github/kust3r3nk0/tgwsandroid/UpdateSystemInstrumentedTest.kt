package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateSystemInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val device get()=UiDevice.getInstance(instrumentation)
    private fun grantInstallerPermission() {
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "appops set ${context.packageName} REQUEST_INSTALL_PACKAGES allow")).use{it.readBytes()}
    }
    private fun await(condition:()->Boolean){val end=SystemClock.elapsedRealtime()+5000
        while(!condition()){check(SystemClock.elapsedRealtime()<end){"Android update handoff deadline"};SystemClock.sleep(20)}}
    @Test fun notificationHasCorrectTextAndOpensTheExistingUpdateCenter()=runBlocking<Unit> {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        val http=FixtureUpdateHttp().apply{release("0.6.3-alpha",25,true)}
        val storage=MemoryUpdatePersistence().apply{preferences=UpdateOptions(channel=UpdateChannel.PREVIEW)}
        val repo=UpdateRepository(storage,GithubUpdateSource(http),"0.6.2",24,android.os.Build.VERSION.SDK_INT)
        repo.initialize();val release=repo.check()!!;UpdateGraph.useForTests(repo)
        val manager=context.getSystemService(NotificationManager::class.java)
        val id=(release.id and 0x7fffffff).toInt()
        try{ActivityScenario.launch(MainActivity::class.java).use{scenario->
            var launchIntent:android.content.Intent?=null
            scenario.onActivity{launchIntent=android.content.Intent(it.intent)}
            assertTrue(UpdateNotifier(context).show(release));assertTrue(UpdateNotifier(context).show(release))
            await{manager.activeNotifications.any{it.id==id}}
            val posted=manager.activeNotifications.filter{it.id==id};assertEquals(1,posted.size)
            val notification=posted.single().notification
            assertEquals(context.getString(R.string.update_notification_title),notification.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(context.getString(R.string.update_notification_text,release.version),notification.extras.getString(Notification.EXTRA_TEXT))
            assertTrue(notification.flags and Notification.FLAG_ONLY_ALERT_ONCE!=0)
            notification.contentIntent.send()
            assertTrue(device.wait(Until.hasObject(By.pkg(context.packageName).res("updates.hero")),10000))
            scenario.onActivity{assertTrue(it.intent.filterEquals(launchIntent))}
        }}finally{manager.cancel(id);UpdateGraph.useForTests(null)}
    }
    @Test fun verifiedApkReachesAndroidInstallerWithoutAutomaticConfirmation(){
        val file=File(context.cacheDir,"verified_updates/system-fixture.apk").apply{parentFile!!.mkdirs()}
        instrumentation.context.assets.open("valid.apk").use{input->file.outputStream().use{input.copyTo(it)}}
        val release=GithubRelease(99,"v9.0.0-debug","9.0.0",99,36,UpdateChannel.PREVIEW,"",UpdateAsset("TGWS-Android-9.0.0-debug.apk","",file.length()),UpdateAsset("SHA256SUMS","",1))
        try{
            AndroidUpdateApkVerifier(context).verify(file,release)
            // Grant after Gradle installs its final signed APK. The host restores
            // this after instrumentation; revoking here kills the owning UID.
            grantInstallerPermission()
            val installer=UpdateInstaller(context);assertTrue(installer.allowed())
            val intent=installer.installIntent(file)
            ActivityScenario.launch(MainActivity::class.java).use{activity->
                activity.onActivity{it.startActivity(intent)}
                await{device.currentPackageName?.let{it.contains("packageinstaller") || it.contains("permissioncontroller")}==true}
                assertTrue(device.wait(Until.hasObject(By.text(java.util.regex.Pattern.compile("(?i)(update|install|обновить|установить)"))),5000))
                device.pressBack()
            }
            val info=context.packageManager.getPackageInfo(context.packageName,0)
            assertTrue(info.longVersionCode<99)
        }finally{file.delete()}
    }
}
