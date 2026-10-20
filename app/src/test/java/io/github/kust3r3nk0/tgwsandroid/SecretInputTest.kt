package io.github.kust3r3nk0.tgwsandroid

import io.github.kust3r3nk0.tgwsandroid.core.TelegramSecret
import org.junit.Assert.*
import org.junit.Test

class SecretInputTest {
    private val secret="00112233445566778899aabbccddeeff"
    @Test fun ddAndEePastesPreserveTheCompleteSecretAndFakeTlsDomain(){
        for(domain in listOf("","example.com")){
            val decoded=SecretInput.parse(TelegramSecret.encode(secret,domain))
            assertEquals(secret,decoded.secret);assertEquals(domain,decoded.domain)
        }
    }
    @Test fun malformedLongValueCannotTurnIntoAValidKey(){
        val decoded=SecretInput.parse(secret+"broken")
        assertFalse(SettingsValidation.validSecret(decoded.secret));assertEquals(secret+"broken",decoded.secret)
        assertNull(decoded.domain);assertFalse(decoded.toString().contains(secret))
    }
}
