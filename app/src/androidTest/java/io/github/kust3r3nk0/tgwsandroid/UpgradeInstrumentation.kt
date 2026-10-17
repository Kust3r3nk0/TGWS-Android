package io.github.kust3r3nk0.tgwsandroid

import android.app.Instrumentation
import android.content.Context
import android.os.Build
import android.os.Bundle
import org.json.JSONObject

/** Separate CI process before and after adb install -r. Contains only public test fixtures. */
class UpgradeInstrumentation : Instrumentation() {
    private var phase = ""
    private var expectedVersion = -1L
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        phase = arguments?.getString("phase").orEmpty()
        expectedVersion = arguments?.getString("version")?.toLongOrNull() ?: -1
        start()
    }
    override fun onStart() {
        try {
            check(Build.MODEL.startsWith("sdk_gphone"))
            val context = targetContext
            check(context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode == expectedVersion)
            val store = SettingsStore(context)
            val preferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            val fixture = context.getSharedPreferences("upgrade_fixture", Context.MODE_PRIVATE)
            val sample = "00112233445566778899aabbccddeeff"
            val relay = "wss://relay.example/upgrade-fixture/apiws"
            when (phase) {
                "seed" -> {
                    check(preferences.all.isEmpty())
                    store.save(8443, sample, relay)
                    AppPreferences(context).apply { theme = ThemeMode.DARK; liquidGlass = true; transparency = true }
                    check(fixture.edit().putString("protected", JSONObject(preferences.all).toString()).commit())
                }
                "verify" -> {
                    val before = JSONObject(requireNotNull(fixture.getString("protected", null)))
                    val saved = store.load()
                    check(saved.secret == sample && saved.port == 8443 && saved.relayUrl == relay)
                    check(saved.transport.mode == io.github.kust3r3nk0.tgwsandroid.core.TransportMode.RELAY_ONLY)
                    // No rewrite of encrypted records: validates both preferences and Keystore continuity.
                    check(preferences.all.size == before.length())
                    check(before.keys().asSequence().all { preferences.all[it] == before.get(it) })
                    AppPreferences(context).let { check(it.theme == ThemeMode.DARK && it.liquidGlass && it.transparency) }
                }
                else -> error("Invalid phase")
            }
            finish(0, Bundle().apply { putString("TGWS_UPGRADE", "$phase:passed") })
        } catch (_: Throwable) {
            // Never serialize configuration or exception text into the CI log.
            finish(1, Bundle().apply { putString("TGWS_UPGRADE", "$phase:failed") })
        }
    }
}
