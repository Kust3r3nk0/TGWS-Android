package io.github.kust3r3nk0.tgwsandroid

/** Many producers, one lifecycle/main-thread consumer. One bounded batch per posted task. */
internal class BoundedEventQueue<T:Any>(private val capacity:Int,
    private val post:(()->Unit)->Unit,private val consume:(T)->Unit) {
    private val lock=Any()
    private val queue=java.util.ArrayDeque<T>()
    private var scheduled=false
    init { require(capacity>0) }
    fun offer(value:T){
        val notify=synchronized(lock){
            if(queue.size==capacity)queue.removeFirst()
            queue.addLast(value)
            if(scheduled)false else{scheduled=true;true}
        }
        if(notify)post(::drain)
    }
    /** Also called explicitly after the producer stops, before main callbacks are removed. */
    fun drain(){
        val batch=synchronized(lock){queue.toList().also{queue.clear()}}
        try { batch.forEach(consume) }
        finally {
            val again=synchronized(lock){scheduled=queue.isNotEmpty();scheduled}
            if(again)post(::drain)
        }
    }
}
