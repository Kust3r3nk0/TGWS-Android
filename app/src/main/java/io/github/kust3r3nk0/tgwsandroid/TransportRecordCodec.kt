package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.TransportSettings
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** New record/AAD leaves the existing secret and relay formats backward compatible. */
internal object TransportRecordCodec {
    private val aad="tgws-transport-v1".toByteArray(Charsets.US_ASCII)
    fun encrypt(key:SecretKey,value:TransportSettings):String {
        val plain=value.encode().toByteArray(Charsets.US_ASCII);require(plain.size<=TransportSettings.MAX_ENCODED)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key);cipher.updateAAD(aad)
        return Base64.getEncoder().encodeToString(cipher.iv+cipher.doFinal(plain))
    }
    fun decrypt(key:SecretKey,value:String):TransportSettings {
        require(value.length in 40..((TransportSettings.MAX_ENCODED+30)*4/3+4))
        val bytes=Base64.getDecoder().decode(value);require(bytes.size in 28..TransportSettings.MAX_ENCODED+28)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key,GCMParameterSpec(128,bytes.copyOfRange(0,12)));cipher.updateAAD(aad)
        return TransportSettings.decode(cipher.doFinal(bytes.copyOfRange(12,bytes.size)).toString(Charsets.US_ASCII))
    }
}
