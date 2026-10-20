package io.github.kust3r3nk0.tgwsandroid

import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

/** Real native Compose rendering with deterministic release fixtures; no production demo mode. */
@RunWith(AndroidJUnit4::class)
class UpdateUiInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private lateinit var storage:MemoryUpdatePersistence
    private lateinit var repository:UpdateRepository
    private lateinit var previousLocales:android.os.LocaleList
    private val http=FixtureUpdateHttp()
    @Before fun setup(){
        val localeManager=context.getSystemService(android.app.LocaleManager::class.java)
        previousLocales=localeManager.applicationLocales
        instrumentation.runOnMainSync{localeManager.applicationLocales=android.os.LocaleList.forLanguageTags("ru")}
        instrumentation.waitForIdleSync()
        AppPreferences(context).theme=ThemeMode.DARK
        storage=MemoryUpdatePersistence().apply{preferences=UpdateOptions(channel=UpdateChannel.PREVIEW)}
        http.release("0.4.2-alpha",19,true).put("body","## Новые функции\n- Центр обновлений и фоновая проверка новых версий.\n## Исправления\n- Проверка подписи APK перед установкой.\n## Оптимизация\n- Ограничение буферов и отмена загрузки.")
        repository=UpdateRepository(storage,GithubUpdateSource(http),"0.4.1",18,android.os.Build.VERSION.SDK_INT,{System.currentTimeMillis()-120000})
        runBlocking{repository.initialize()};UpdateGraph.useForTests(repository)
    }
    @After fun cleanup(){
        UpdateGraph.useForTests(null);AppPreferences(context).apply{theme=ThemeMode.SYSTEM;liquidGlass=false;transparency=false}
        instrumentation.runOnMainSync{context.getSystemService(android.app.LocaleManager::class.java).applicationLocales=previousLocales}
        instrumentation.waitForIdleSync()
    }
    private fun scroll(tag:String){
        if(ui.onAllNodesWithTag("updates.scroll").fetchSemanticsNodes().isNotEmpty())ui.onNodeWithTag("updates.scroll").performScrollToNode(hasTestTag(tag))
        else ui.onNodeWithTag(tag).performScrollTo()
    }
    private fun click(tag:String,scroll:Boolean=false){if(scroll)this.scroll(tag);ui.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick){it()};ui.waitForIdle()}
    private fun shot(name:String){
        ui.waitForIdle();Thread.sleep(350)
        val image=requireNotNull(instrumentation.uiAutomation.takeScreenshot())
        try{
            ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("mkdir -p /sdcard/Download/tgws-ui")).use{it.readBytes()}
            val pipes=instrumentation.uiAutomation.executeShellCommandRw("dd of=/sdcard/Download/tgws-ui/$name.png")
            ParcelFileDescriptor.AutoCloseOutputStream(pipes[1]).use{assertTrue(image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it))}
            ParcelFileDescriptor.AutoCloseInputStream(pipes[0]).use{it.readBytes()}
        }finally{image.recycle()}
    }
    @Test fun settingsActionsSurviveActivityRecreationAndNotificationReopensCenter(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            ui.waitUntil(10000){ui.onAllNodesWithTag("settings.loading").fetchSemanticsNodes().isEmpty()}
            click("nav.settings")
            ui.onNodeWithTag("settings.about").performScrollTo()
            val savedScroll=ui.onNodeWithTag("page.scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            click("settings.update.center")
            ui.waitUntil(10000){repository.state.value.release!=null}
            ui.onNodeWithTag("updates.version").assertTextEquals("v0.4.2-alpha")
            shot("updates-center-dark")
            click("updates.settings");ui.onNodeWithTag("updates.period.360").assertIsSelected()
            ui.onNodeWithTag("updates.back").assertIsDisplayed()
            shot("updates-settings-dark")
            click("updates.period.720",true);ui.waitUntil{storage.options().minutes==720L}
            click("updates.auto",true);ui.waitUntil{!storage.options().auto}
            scroll("updates.manual");ui.onNodeWithTag("updates.manual").assertIsEnabled()
            scenario.recreate();ui.waitForIdle()
            scroll("updates.period.720");ui.onNodeWithTag("updates.period.720").assertIsSelected().assertIsNotEnabled()
            var launchIntent:Intent?=null
            scenario.onActivity{launchIntent=Intent(it.intent);it.startActivity(Intent(it,MainActivity::class.java).putExtra(OPEN_UPDATES,true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP))}
            ui.onNodeWithTag("updates.hero").assertExists()
            scenario.onActivity{assertTrue(it.intent.filterEquals(launchIntent))}
            assertEquals(720L,storage.options().minutes);assertFalse(storage.options().auto)
            click("updates.back")
            val restoredScroll=ui.onNodeWithTag("page.scroll").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
            assertEquals(savedScroll,restoredScroll,1f)
        }
    }
    @Test fun lightThemeLatestStateAndEverySwitchRemainFunctional(){
        AppPreferences(context).theme=ThemeMode.LIGHT
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            var state by mutableStateOf(UpdateState(result=UpdateCheckResult.CURRENT,options=UpdateOptions(auto=false)))
            var page by mutableIntStateOf(2);var checks=0
            scenario.onActivity{it.setContent{TgwsTheme(ThemeMode.LIGHT){UpdateHubScreen(page,"0.4.1",state,
                {page=2},{page=3},{state=state.copy(options=it)},{checks++},{},{},{})}}}
            ui.onNodeWithTag("updates.version").assertTextEquals(context.getString(R.string.update_current_title))
            ui.onNodeWithTag("updates.install").assertDoesNotExist();shot("updates-current-light")
            click("updates.settings");click("updates.auto");assertTrue(state.options.auto)
            click("updates.period.15",true);assertEquals(15L,state.options.minutes)
            click("updates.notify",true);assertFalse(state.options.notify)
            click("updates.wifi",true);assertTrue(state.options.wifiOnly)
            click("updates.channel.menu",true);click("updates.channel.PREVIEW");assertEquals(UpdateChannel.PREVIEW,state.options.channel)
            click("updates.manual",true);assertEquals(1,checks)
            ui.onNodeWithTag("updates.scroll").performScrollToIndex(0);shot("updates-settings-light")
        }
    }
    @Test fun largeFontGlassAndTransparencyKeepActionsAccessible(){
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            val release=runBlocking{GithubUpdateSource(http).check("0.4.1",18,36,UpdateChannel.PREVIEW)}!!
            var page by mutableIntStateOf(2);var checks=0
            scenario.onActivity{it.setContent{TgwsTheme(ThemeMode.DARK,true,true){
                val density=LocalDensity.current
                CompositionLocalProvider(LocalDensity provides Density(density.density,1.6f)){
                    UpdateHubScreen(page,"0.4.1",UpdateState(release=release,result=UpdateCheckResult.AVAILABLE),
                        {page=2},{page=3},{},{checks++},{},{},{})
                }
            }}}
            ui.onNodeWithTag("updates.install").assertIsDisplayed()
            shot("updates-large-font-dark")
            click("updates.settings");scroll("updates.manual");ui.onNodeWithTag("updates.manual").assertIsDisplayed()
            click("updates.manual");assertEquals(1,checks)
        }
    }
    @Test fun englishUpdateScreenUsesAndroidResourceLocalization(){
        AppPreferences(context).theme=ThemeMode.LIGHT
        ActivityScenario.launch(MainActivity::class.java).use{scenario->
            val configuration=android.content.res.Configuration(context.resources.configuration).apply{setLocales(android.os.LocaleList.forLanguageTags("en"))}
            val translated=context.createConfigurationContext(configuration)
            scenario.onActivity{it.setContent{CompositionLocalProvider(LocalContext provides translated,LocalConfiguration provides configuration){
                TgwsTheme(ThemeMode.LIGHT){UpdateHubScreen(3,"0.4.1",UpdateState(),{},{},{},{},{},{},{})}
            }}}
            ui.onNodeWithTag("updates.auto").assertContentDescriptionEquals("Automatic checking")
            ui.onNodeWithText("Every 6 hours (default)").assertIsDisplayed()
            shot("updates-settings-english")
        }
    }
}
