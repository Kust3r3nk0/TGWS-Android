package io.github.kust3r3nk0.tgwsandroid

import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class UpdateHttpTest {
    @Test fun forbiddenNotFoundAndRateLimitAreTypedAndBounded()=runBlocking {
        MockWebServer().use{server->
            val http=GithubUpdateHttp()
            for((code,issue) in listOf(403 to UpdateIssue.FORBIDDEN,404 to UpdateIssue.NOT_FOUND,429 to UpdateIssue.RATE_LIMIT)){
                server.enqueue(MockResponse().setResponseCode(code).setHeader("Retry-After","120"))
                try{http.text(server.url("/").toString());fail()}catch(e:UpdateException){assertEquals(issue,e.issue)
                    if(code!=404)assertTrue(e.retryAt>System.currentTimeMillis()+60_000)}
            }
        }
    }
    @Test fun redirectCannotSwitchToPlaintextOrUnknownHost()=runBlocking {
        MockWebServer().use{server->
            for(url in listOf("http://github.com/repo/file","https://evil.example/app.apk")){
                server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",url))
                try{GithubUpdateHttp().text(server.url("/").toString());fail()}catch(e:UpdateException){assertEquals(UpdateIssue.BAD_RESPONSE,e.issue)}
            }
        }
    }
    @Test fun responseBodyCannotExceedItsDeclaredMemoryBudget()=runBlocking {
        MockWebServer().use{server->server.enqueue(MockResponse().setBody("a".repeat(4096)))
            try{GithubUpdateHttp().text(server.url("/").toString(),1024);fail()}catch(e:UpdateException){assertEquals(UpdateIssue.BAD_RESPONSE,e.issue)}}
    }
    @Test fun downloadReportsRealBytesAndRejectsTruncation()=runBlocking {
        val file=Files.createTempFile("tgws-update-", ".apk").toFile()
        try{MockWebServer().use{server->
            val data=ByteArray(192*1024){it.toByte()};val progress=mutableListOf<Long>()
            server.enqueue(MockResponse().setBody(Buffer().write(data)))
            GithubUpdateHttp().download(server.url("/").toString(),file,data.size.toLong()){progress+=it}
            assertArrayEquals(data,file.readBytes());assertEquals(data.size.toLong(),progress.last());assertTrue(progress.zipWithNext().all{it.first<it.second})
            server.enqueue(MockResponse().setBody("short"))
            try{GithubUpdateHttp().download(server.url("/").toString(),file,100){};fail()}catch(e:UpdateException){assertEquals(UpdateIssue.INVALID_APK,e.issue)}
        }}finally{file.delete()}
    }
    @Test(timeout=5000) fun cancellationClosesAStreamingHttpCall()=runBlocking {
        val file=Files.createTempFile("tgws-cancel-", ".apk").toFile()
        try{MockWebServer().use{server->
            val data=ByteArray(512*1024);val first=CompletableDeferred<Unit>()
            server.enqueue(MockResponse().setBody(Buffer().write(data)).throttleBody(8192,100,TimeUnit.MILLISECONDS))
            val job=launch{GithubUpdateHttp().download(server.url("/").toString(),file,data.size.toLong()){first.complete(Unit)}}
            withTimeout(2000){first.await()};job.cancelAndJoin()
            assertTrue(job.isCancelled);assertTrue(file.length()<data.size)
        }}finally{file.delete()}
    }
    @Test(timeout=5000) fun unresponsiveGithubHasFiniteTimeout()=runBlocking {
        MockWebServer().use{server->server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val http=GithubUpdateHttp(OkHttpClient.Builder().callTimeout(150,TimeUnit.MILLISECONDS).build())
            try{http.text(server.url("/").toString());fail()}catch(e:UpdateException){assertEquals(UpdateIssue.TIMEOUT,e.issue)}}
    }
    @Test fun sha256DetectsAnActualByteChange(){
        val file=Files.createTempFile("tgws-sha-", ".apk").toFile()
        try{file.writeBytes(byteArrayOf(1,2,3));val original=sha256(file);verifyUpdateChecksum(file,original);file.appendBytes(byteArrayOf(4))
            val failure=assertThrows(UpdateException::class.java){verifyUpdateChecksum(file,original)}
            assertEquals(UpdateIssue.CHECKSUM,failure.issue);assertEquals(64,original.length)}finally{file.delete()}
    }
    @Test fun compressedManifestBombIsRejectedBeforeParsing(){
        val file=Files.createTempFile("tgws-zip-", ".apk").toFile()
        try{java.util.zip.ZipOutputStream(file.outputStream()).use{zip->
            zip.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml"));zip.write(ByteArray(2*1024*1024+1));zip.closeEntry()}
            assertTrue(file.length()<10000)
            assertEquals(UpdateIssue.INVALID_APK,assertThrows(UpdateException::class.java){validateUpdateArchive(file)}.issue)
        }finally{file.delete()}
    }
}
