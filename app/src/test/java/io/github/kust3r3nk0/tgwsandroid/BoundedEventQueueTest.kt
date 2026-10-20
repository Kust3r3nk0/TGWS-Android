package io.github.kust3r3nk0.tgwsandroid

import org.junit.Assert.*
import org.junit.Test

class BoundedEventQueueTest {
    @Test fun blockedMainLoopRetainsOnlyLastBoundedEventsAndOneScheduledTask(){
        val tasks=java.util.ArrayDeque<()->Unit>();val consumed=mutableListOf<Int>()
        val queue=BoundedEventQueue<Int>(128,{tasks.addLast(it)},{consumed+=it})
        repeat(10000){queue.offer(it)}
        assertEquals(1,tasks.size)
        tasks.removeFirst().invoke()
        assertEquals((9872..9999).toList(),consumed);assertTrue(tasks.isEmpty())
    }
    @Test fun eventsProducedWhileDrainingYieldToANewBoundedMainTask(){
        val tasks=java.util.ArrayDeque<()->Unit>();val consumed=mutableListOf<Int>()
        lateinit var queue:BoundedEventQueue<Int>
        queue=BoundedEventQueue(3,{tasks.addLast(it)},{value->consumed+=value;if(value==0)(1..10).forEach(queue::offer)})
        queue.offer(0);tasks.removeFirst().invoke()
        assertEquals(listOf(0),consumed);assertEquals(1,tasks.size)
        tasks.removeFirst().invoke()
        assertEquals(listOf(0,8,9,10),consumed);assertTrue(tasks.isEmpty())
    }
    @Test fun stopDrainsBeforePendingCallbacksAreRemovedAndCannotDuplicateEvents(){
        val tasks=java.util.ArrayDeque<()->Unit>();val consumed=mutableListOf<Int>()
        val queue=BoundedEventQueue<Int>(8,{tasks.addLast(it)},{consumed+=it})
        queue.offer(42);queue.drain()
        assertEquals(listOf(42),consumed)
        tasks.removeFirst().invoke();assertEquals(listOf(42),consumed)
        queue.offer(43);tasks.removeFirst().invoke();assertEquals(listOf(42,43),consumed)
    }
}
