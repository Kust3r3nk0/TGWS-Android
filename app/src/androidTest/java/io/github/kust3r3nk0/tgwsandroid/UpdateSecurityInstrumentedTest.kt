package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UpdateSecurityInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private fun file(name:String)=File(context.cacheDir,name).also{f->instrumentation.context.assets.open("$name.apk").use{input->f.outputStream().use{input.copyTo(it)}}}
    private fun release(file:File):GithubRelease {
        val tag="v9.0.0-debug";val name="TGWS-Android-9.0.0-debug.apk"
        fun url(name:String)="https://github.com/$UPDATE_REPOSITORY/releases/download/$tag/$name"
        return GithubRelease(99,tag,"9.0.0",99,36,UpdateChannel.PREVIEW,"## Fixes\n- Synthetic security fixture.",UpdateAsset(name,url(name),file.length()),UpdateAsset("SHA256SUMS",url("SHA256SUMS"),200))
    }
    @Test fun validNewApkAndForwardRotationAreAccepted(){
        for(name in listOf("valid","rotated")){val apk=file(name)
            try{AndroidUpdateApkVerifier(context).verify(apk,release(apk))}finally{apk.delete()}}
    }
    @Test fun wrongSignerAndRotationWithoutInstalledDataCapabilityAreRejected(){
        for(name in listOf("wrong-key","rotation-denied")){val apk=file(name)
            try{try{AndroidUpdateApkVerifier(context).verify(apk,release(apk));fail("Untrusted signing identity accepted")}
                catch(e:UpdateException){assertEquals(UpdateIssue.SIGNATURE,e.issue)}}finally{apk.delete()}}
    }
    @Test fun modifiedApkAndForeignPackageAreRejected(){
        for((name,issue) in listOf("tampered" to UpdateIssue.INVALID_APK,"wrong-package" to UpdateIssue.WRONG_PACKAGE)){
            val apk=file(name);try{try{AndroidUpdateApkVerifier(context).verify(apk,release(apk));fail("Invalid APK accepted")}
                catch(e:UpdateException){assertEquals(issue,e.issue)}}finally{apk.delete()}}
    }
    @Test fun newerManifestCannotAdvertiseAnOldOrDifferentApk(){
        val apk=file("valid")
        try{val release=release(apk).copy(versionCode=100)
            try{AndroidUpdateApkVerifier(context).verify(apk,release);fail()}catch(e:UpdateException){assertEquals(UpdateIssue.OLD_VERSION,e.issue)}}finally{apk.delete()}
    }
    @Test fun cachedApkIsReverifiedAfterRecreationAndTamperingDoesNotTouchProxySettings()=runBlocking {
        val original=file("valid");val release=release(original);val sha=sha256(original)
        val folder=File(context.cacheDir,"verified_updates").apply{mkdirs()}
        val cached=File(folder,"update-$sha.apk");original.copyTo(cached,overwrite=true);original.delete()
        val prefs=context.getSharedPreferences("tgws_update_download",Context.MODE_PRIVATE)
        val proxy=context.getSharedPreferences("settings",Context.MODE_PRIVATE).all.toMap()
        try{
            assertTrue(prefs.edit().putString("release",encodeRelease(release).toString()).putString("sha",sha).commit())
            assertNotNull(UpdateDownloads(context).ready(release));assertNotNull(UpdateDownloads(context).ready(release))
            cached.appendBytes(byteArrayOf(1));assertNull(UpdateDownloads(context).ready(release));assertFalse(cached.exists())
            assertEquals(proxy,context.getSharedPreferences("settings",Context.MODE_PRIVATE).all)
        }finally{cached.delete();prefs.edit().clear().commit()}
    }
    @Test fun installerReceivesOnlyNarrowContentUriAndReadGrant(){
        val file=File(context.cacheDir,"verified_updates/intent-fixture.apk").apply{parentFile!!.mkdirs();writeBytes(byteArrayOf(1))}
        try{val installer=UpdateInstaller(context);val intent=installer.installIntent(file)
            assertEquals(Intent.ACTION_VIEW,intent.action);assertEquals("content",intent.data?.scheme)
            assertEquals(context.packageName+".updates",intent.data?.authority)
            assertEquals("application/vnd.android.package-archive",intent.type)
            assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION!=0)
            assertEquals(0,intent.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            assertEquals("android.settings.MANAGE_UNKNOWN_APP_SOURCES",installer.permissionIntent().action)
            val outside=File(context.cacheDir,"pending_updates/not-verified.apk").apply{parentFile!!.mkdirs();writeBytes(byteArrayOf(1))}
            try{assertThrows(IllegalArgumentException::class.java){installer.installIntent(outside)}}finally{outside.delete()}
        }finally{file.delete()}
    }
}
