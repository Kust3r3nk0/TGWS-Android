package io.github.kust3r3nk0.tgwsandroid.core

import java.security.KeyStore
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

/** Deliberately public localhost identity. No trust exceptions; client trusts only the fixture CA. */
internal object TestTls {
    fun trustManager():javax.net.ssl.X509TrustManager {
        val store=KeyStore.getInstance("PKCS12")
        javaClass.getResourceAsStream("/websocket/localhost.p12")!!.use{store.load(it,"test-only-password".toCharArray())}
        val trust=KeyStore.getInstance("PKCS12").apply{load(null,null);setCertificateEntry("localhost",store.getCertificate("localhost"))}
        return TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply{init(trust)}
            .trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().single()
    }
    fun contexts(): Pair<SSLContext, SSLContext> {
        val password = "test-only-password".toCharArray()
        val store = KeyStore.getInstance("PKCS12")
        javaClass.getResourceAsStream("/websocket/localhost.p12")!!.use { store.load(it, password) }
        val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, password) }
        val server = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, null, null) }
        val trustStore = KeyStore.getInstance("PKCS12").apply { load(null, null); setCertificateEntry("localhost", store.getCertificate("localhost")) }
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply { init(trustStore) }
        val client = SSLContext.getInstance("TLS").apply { init(null, trust.trustManagers, null) }
        return server to client
    }
}
