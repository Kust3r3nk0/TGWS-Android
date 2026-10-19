package io.github.kust3r3nk0.tgwsandroid

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.android.apksig.ApkVerifier
import java.io.File
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

internal interface UpdateApkVerifier { fun verify(file:File,release:GithubRelease) }
/** Checks bytes cryptographically before trusting any package/certificate metadata. */
internal class AndroidUpdateApkVerifier(private val context:Context):UpdateApkVerifier {
    override fun verify(file:File,release:GithubRelease){
        validateUpdateArchive(file)
        val verification=try{ApkVerifier.Builder(file).setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
            .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT).build().verify()}
            catch(_:Exception){throw UpdateException(UpdateIssue.INVALID_APK)}
        if(!verification.isVerified || !(verification.isVerifiedUsingV2Scheme || verification.isVerifiedUsingV3Scheme || verification.isVerifiedUsingV31Scheme))
            throw UpdateException(UpdateIssue.INVALID_APK)
        val pm=context.packageManager
        val flags=PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
        val installed=pm.getPackageInfo(context.packageName,flags)
        val old=installed.signingInfo?.apkContentsSigners?.map{signature->
            CertificateFactory.getInstance("X.509").generateCertificate(signature.toByteArray().inputStream()) as X509Certificate
        }?:throw UpdateException(UpdateIssue.SIGNATURE)
        val incoming=verification.signerCertificates
        if(old.isEmpty() || incoming.isEmpty())throw UpdateException(UpdateIssue.SIGNATURE)
        val exact=old.size==incoming.size && old.all{certificate->incoming.any{it.encoded.contentEquals(certificate.encoded)}}
        if(!exact){
            // Multiple-signer identities require exact sets. Rotation is forward only,
            // with a verified v3 lineage authorizing the installed data of this signer.
            val lineage=verification.signingCertificateLineage?:throw UpdateException(UpdateIssue.SIGNATURE)
            val chain=lineage.certificatesInLineage
            if(old.size!=1 || incoming.size!=1 || chain.isEmpty() ||
                !chain.last().encoded.contentEquals(incoming.single().encoded) ||
                !lineage.isCertificateInLineage(old.single()) ||
                !lineage.getSignerCapabilities(old.single()).hasInstalledData())throw UpdateException(UpdateIssue.SIGNATURE)
        }
        // Parse package resources only after authenticating the installed identity.
        val archive=pm.getPackageArchiveInfo(file.absolutePath,flags)?:throw UpdateException(UpdateIssue.INVALID_APK)
        if(archive.packageName!=context.packageName)throw UpdateException(UpdateIssue.WRONG_PACKAGE)
        val archiveVersion=numericVersion(archive.versionName.orEmpty())?:throw UpdateException(UpdateIssue.INVALID_APK)
        val installedVersion=numericVersion(installed.versionName.orEmpty())?:throw UpdateException(UpdateIssue.INVALID_APK)
        if(archive.longVersionCode!=release.versionCode || archive.longVersionCode<=installed.longVersionCode ||
            archiveVersion!=numericVersion(release.version) || archiveVersion<=installedVersion)throw UpdateException(UpdateIssue.OLD_VERSION)
        if(archive.applicationInfo?.minSdkVersion!=release.minSdk || release.minSdk !in 36..Build.VERSION.SDK_INT)
            throw UpdateException(UpdateIssue.INCOMPATIBLE)
    }
}

internal class UpdateInstaller(private val context:Context){
    fun allowed()=context.packageManager.canRequestPackageInstalls()
    fun permissionIntent()=Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${context.packageName}"))
    fun installIntent(file:File):Intent {
        val uri=FileProvider.getUriForFile(context,context.packageName+".updates",file)
        return Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply{clipData=android.content.ClipData.newRawUri("APK",uri)}
    }
}
