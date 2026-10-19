package io.github.kust3r3nk0.tgwsandroid

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NetworkMonitorInstrumentedTest {
    @Test fun suspensionAndResumeOfTheSameNetworkInvalidateAccessWithoutValidationGate(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            // Synthetic OS facts, no phone identifiers, radio access or external traffic.
            val network=android.net.Network.fromNetworkHandle((1234L shl 32) or 0xcafed00dL)
            val available=NetworkAvailability(internet=true,captivePortal=false,suspended=false)
            val changes=mutableListOf<Boolean>()
            AndroidNetworkMonitor(instrumentation.targetContext,Handler(Looper.getMainLooper())){changed,_->changes+=changed}.use{monitor->
                monitor.update(network,network,null,android.net.LinkProperties(),available)
                val before=requireNotNull(monitor.access)
                assertTrue(before.usable)
                assertFalse(before.validated)
                monitor.update(network,network,null,android.net.LinkProperties(),available.copy(suspended=true))
                assertTrue(changes.last())
                assertFalse(requireNotNull(monitor.access).usable)
                assertNotSame(before,monitor.access)
                val during=monitor.access
                monitor.update(network,network,null,android.net.LinkProperties(),available)
                assertTrue(changes.last())
                assertTrue(requireNotNull(monitor.access).usable)
                assertNotSame(during,monitor.access)
                monitor.update(network,network,null,android.net.LinkProperties(),available)
                assertFalse(changes.last())
            }
        }
    }
    @Test fun repeatedMonitorRegistrationAndCloseRejectLateCallbacksWithoutPhonePrompts(){
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val callbacks=AtomicInteger()
        repeat(5){
            lateinit var monitor:AndroidNetworkMonitor
            instrumentation.runOnMainSync{
                monitor=AndroidNetworkMonitor(instrumentation.targetContext,Handler(Looper.getMainLooper())){_,_->callbacks.incrementAndGet()}
                monitor.start();monitor.refresh();monitor.close();monitor.close();assertNull(monitor.access)
            }
            val after=callbacks.get();instrumentation.waitForIdleSync();assertEquals(after,callbacks.get())
        }
        @Suppress("DEPRECATION")
        val requested=instrumentation.targetContext.packageManager.getPackageInfo(instrumentation.targetContext.packageName,PackageManager.GET_PERMISSIONS).requestedPermissions.orEmpty().toSet()
        assertTrue(requested.contains(android.Manifest.permission.READ_BASIC_PHONE_STATE))
        for(permission in listOf(android.Manifest.permission.READ_PHONE_STATE,android.Manifest.permission.READ_PHONE_NUMBERS,
            android.Manifest.permission.READ_SMS,android.Manifest.permission.READ_CONTACTS,android.Manifest.permission.ACCESS_FINE_LOCATION))assertFalse(requested.contains(permission))
    }
}
