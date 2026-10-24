package io.github.kust3r3nk0.tgwsandroid

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutoConfigInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    @Test fun offlineButtonCannotChangeConfigurationAndSurvivesActivityRecreation(){
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val secret="00112233445566778899aabbccddeeff"
        SettingsStore(context).recover(8443,secret)
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            fun content(){scenario.onActivity{activity->
                val form=ViewModelProvider(activity)[ProxyFormModel::class.java]
                val auto=ViewModelProvider(activity)[AutoConfigModel::class.java]
                activity.setContent{TgwsTheme(ThemeMode.DARK,false,false){
                    Column(Modifier.verticalScroll(rememberScrollState())){SmartConnectionSettings(form,true,AccessKind.NONE,auto)}
                }}
            }}
            content();ui.waitForIdle()
            ui.onNodeWithTag("smart.auto.configure").performScrollTo().assertIsNotEnabled()
            scenario.recreate();content();ui.waitForIdle()
            ui.onNodeWithTag("smart.auto.configure").performScrollTo().assertIsNotEnabled()
            Assert.assertEquals(secret,SettingsStore(context).load().secret)
            Assert.assertFalse(SettingsStore(context).load().transport.profiles.enabled)
            Assert.assertFalse(ProxyState.snapshot.running)
        }
    }
}
