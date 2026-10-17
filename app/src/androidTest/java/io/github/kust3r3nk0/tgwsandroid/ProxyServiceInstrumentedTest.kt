package io.github.kust3r3nk0.tgwsandroid

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.kust3r3nk0.tgwsandroid.core.HandshakeRejection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ConnectException
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Local Android lifecycle tests. These do not claim a real Telegram connection. */
@RunWith(AndroidJUnit4::class)
class ProxyServiceInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val loopback = InetAddress.getByName("127.0.0.1")
    private val sample = "0123456789abcdef0123456789abcdef"
    private val notifications get() = context.getSystemService(NotificationManager::class.java)

    @Before fun prepare() {
        stop()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
        // Recovery is explicit here because tests must also work after corrupt-data test runs.
        SettingsStore(context).recover(freePort(), sample)
    }

    @After fun cleanup() { stop() }

    private fun freePort(): Int = ServerSocket(0, 1, loopback).use { it.localPort }
    private fun assertPortReleased(port: Int) {
        assertThrows(ConnectException::class.java) {
            Socket().use { it.connect(InetSocketAddress(loopback, port), 1_000) }
        }
        // Match production SO_REUSEADDR, allowing accepted sockets' TIME_WAIT after active close.
        ServerSocket().use {
            it.reuseAddress = true
            it.bind(InetSocketAddress(loopback, port))
            assertTrue(it.isBound)
        }
    }
    private fun start() = instrumentation.runOnMainSync {
        context.startForegroundService(Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_START))
    }
    private fun stop() {
        instrumentation.runOnMainSync { context.stopService(Intent(context, ProxyService::class.java)) }
        eventually { !snapshot().running && notifications.activeNotifications.none { it.id == 1443 } }
    }
    private fun snapshot(): ProxySnapshot {
        var result = ProxySnapshot()
        instrumentation.runOnMainSync { result = ProxyState.snapshot }
        return result
    }
    private fun eventually(condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000
        while (!condition()) {
            if (SystemClock.elapsedRealtime() >= deadline) fail("Timed out waiting for local lifecycle state")
            SystemClock.sleep(20)
        }
    }

    @Test fun repeatedStartDoesNotRestartListenerOrCloseExistingClient() {
        ActivityScenario.launch(MainActivity::class.java).use {
            start()
            eventually { snapshot().localAvailable }
            val port = SettingsStore(context).port
            Socket(loopback, port).use { client ->
                eventually { snapshot().pending == 1 }
                start()
                instrumentation.waitForIdleSync()
                client.soTimeout = 200
                // A duplicate-start restart would close the pending accepted socket and return EOF.
                assertThrows(SocketTimeoutException::class.java) { client.getInputStream().read() }
                assertTrue(snapshot().localAvailable)
                assertEquals(1, snapshot().pending)
                assertTrue(notifications.activeNotifications.any {
                    it.id == 1443 && it.notification.flags and Notification.FLAG_FOREGROUND_SERVICE != 0
                })
            }
            stop()
            assertPortReleased(port)
        }
    }

    @Test fun backgroundAndActivityRecreationKeepLocalServiceAlive() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            start()
            eventually { snapshot().localAvailable }
            scenario.moveToState(Lifecycle.State.CREATED)
            assertTrue(snapshot().localAvailable)
            Socket(loopback, SettingsStore(context).port).close()
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            eventually { snapshot().localAvailable }
            assertEquals(0, snapshot().sessions)
            stop()
        }
    }

    @Test fun occupiedPortIsReportedAndForegroundNotificationIsRemoved() {
        ServerSocket(0, 1, loopback).use { occupied ->
            SettingsStore(context).save(occupied.localPort, sample)
            ActivityScenario.launch(MainActivity::class.java).use {
                start()
                eventually { snapshot().phase == ProxyPhase.ERROR }
                assertEquals(ProxyIssue.PORT, snapshot().issue)
                eventually { notifications.activeNotifications.none { it.id == 1443 } }
                assertTrue(occupied.isBound)
                assertFalse(occupied.isClosed)
            }
        }
    }

    @Test fun repeatedStartStopCyclesReleasePortAndPendingClients() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val port = SettingsStore(context).port
            repeat(3) {
                start()
                eventually { snapshot().localAvailable }
                Socket(loopback, port).use { client ->
                    eventually { snapshot().pending == 1 }
                    stop()
                    client.soTimeout = 1_000
                    assertEquals(-1, client.getInputStream().read())
                }
                assertEquals(0, snapshot().sessions)
                assertEquals(0, snapshot().pending)
                assertPortReleased(port)
            }
        }
    }

    @Test fun malformedHandshakeIsRejectedWithoutStoppingLocalListener() {
        ActivityScenario.launch(MainActivity::class.java).use {
            start()
            eventually { snapshot().localAvailable }
            Socket(loopback, SettingsStore(context).port).use { client ->
                client.getOutputStream().write(ByteArray(64))
                client.getOutputStream().flush()
                client.soTimeout = 2_000
                assertEquals(-1, client.getInputStream().read())
            }
            eventually { snapshot().pending == 0 }
            eventually { snapshot().rejectedHandshakes == 1 }
            assertEquals(HandshakeRejection.RESERVED_PREFIX, snapshot().lastHandshakeRejection)
            assertNull(snapshot().issue)
            assertTrue(snapshot().localAvailable)
            assertEquals(0, snapshot().sessions)
        }
    }

    @Test fun explicitStopClearsCurrentSessionErrorAndRestartClearsLastRunHistory() {
        ActivityScenario.launch(MainActivity::class.java).use {
            start()
            eventually { snapshot().localAvailable }
            val port = SettingsStore(context).port
            Socket(loopback, port).use { client ->
                client.getOutputStream().write(ByteArray(64))
                client.getOutputStream().flush()
                client.soTimeout = 2_000
                assertEquals(-1, client.getInputStream().read())
            }
            eventually { snapshot().rejectedHandshakes == 1 && snapshot().pending == 0 }
            // Seed a transport diagnostic only; this test exercises Android stop/restart,
            // independently of the availability of an external Telegram route.
            instrumentation.runOnMainSync {
                ProxyState.snapshot = ProxyState.snapshot.copy(issue = ProxyIssue.SESSION)
                context.startService(Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
            }
            eventually { snapshot().phase == ProxyPhase.STOPPED }
            eventually { notifications.activeNotifications.none { it.id == 1443 } }
            assertNull(snapshot().issue)
            assertEquals(1, snapshot().rejectedHandshakes)
            assertEquals(HandshakeRejection.RESERVED_PREFIX, snapshot().lastHandshakeRejection)
            assertPortReleased(port)
            start()
            eventually { snapshot().localAvailable }
            assertNull(snapshot().issue)
            assertEquals(0, snapshot().rejectedHandshakes)
            assertNull(snapshot().lastHandshakeRejection)
        }
    }

    @Test fun probeWithoutRunningServerStopsTheEmptyService() {
        ActivityScenario.launch(MainActivity::class.java).use {
            instrumentation.runOnMainSync {
                context.startService(Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_PROBE))
            }
            instrumentation.waitForIdleSync()
            assertFalse(snapshot().running)
            assertTrue(notifications.activeNotifications.none { it.id == 1443 })
            var serviceWasStillRunning = false
            instrumentation.runOnMainSync {
                serviceWasStillRunning = context.stopService(Intent(context, ProxyService::class.java))
            }
            assertFalse("A route probe must not leave a service running without its listener", serviceWasStillRunning)
            assertPortReleased(SettingsStore(context).port)
        }
    }

    @Test fun userStopDuringStartupCancelsPendingListenerAndNotification() {
        ActivityScenario.launch(MainActivity::class.java).use {
            val port = SettingsStore(context).port
            instrumentation.runOnMainSync {
                context.startForegroundService(Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_START))
                context.startService(Intent(context, ProxyService::class.java).setAction(ProxyService.ACTION_STOP))
            }
            eventually { snapshot().phase == ProxyPhase.STOPPED }
            eventually { notifications.activeNotifications.none { it.id == 1443 } }
            // Cross the settings executor boundary before checking no listener started late.
            eventually { Thread.getAllStackTraces().keys.none { it.name == "TGWS-settings" && it.isAlive } }
            instrumentation.waitForIdleSync()
            assertPortReleased(port)
            assertEquals(0, snapshot().sessions)
            assertEquals(0, snapshot().pending)
        }
    }
}
