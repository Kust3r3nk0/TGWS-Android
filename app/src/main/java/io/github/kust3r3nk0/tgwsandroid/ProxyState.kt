package io.github.kust3r3nk0.tgwsandroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** UI adapter only. The core never imports Compose or writes this state directly. */
object ProxyState {
    var snapshot by mutableStateOf(ProxySnapshot())
        internal set
}
