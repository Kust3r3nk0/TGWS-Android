package io.github.kust3r3nk0.tgwsandroid.core

/**
 * Concurrent collections can shrink between size and iterator access. Kotlin
 * Collection.toList() has a size==1 shortcut calling next() without hasNext().
 * Iterate without consulting size so cancellation/stop can safely take a snapshot.
 */
internal fun <T> Iterable<T>.snapshotEntries(): List<T> = buildList {
    for (entry in this@snapshotEntries) add(entry)
}
