package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** In-process transient state; networking runs in ProxyService. */
object ProxyState {
    var running by mutableStateOf(false)
    var description by mutableStateOf("Остановлен")
    var sessions by mutableStateOf(0)
}
