package io.github.kust3r3nk0.tgwsandroid

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File

/** Separate authority keeps exported reports outside the verified-APK install scope. */
class DiagnosticFileProvider:FileProvider()

/** Explicit, narrow read-only sharing. The cache contains redacted reports only. */
internal object DiagnosticExport {
    fun prepare(context:Context,report:String):Intent {
        require(report.toByteArray(Charsets.UTF_8).size<=256*1024)
        val directory=File(context.cacheDir,"diagnostic_reports").apply{check(mkdirs() || isDirectory)}
        directory.listFiles()?.sortedByDescending{it.lastModified()}?.drop(3)?.forEach{it.delete()}
        val file=File.createTempFile("tgws-report-",".json",directory)
        try {
            file.writeText(report,Charsets.UTF_8)
            val uri=FileProvider.getUriForFile(context,"${context.packageName}.diagnostics",file)
            return Intent(Intent.ACTION_SEND).setType("application/json").putExtra(Intent.EXTRA_STREAM,uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .apply{clipData=ClipData.newRawUri("TGWS diagnostic report",uri)}
        }catch(error:Exception){file.delete();throw error}
    }
}
