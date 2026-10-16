package io.github.kust3r3nk0.tgwsandroid.core

/** Native transport <-> HTTP /api body, matching cf_h2.py without upstream replay logic. */
internal object HttpMtprotoFraming {
    private val random=java.security.SecureRandom()
    private fun le(data:ByteArray,offset:Int)= (0..3).fold(0L){n,i->n or ((data[offset+i].toLong() and 255) shl (8*i))}
    fun body(frame:ByteArray,tag:ByteArray):ByteArray {
        require(frame.isNotEmpty())
        val abridged=tag[0]==0xef.toByte()
        val prefix=if(abridged && (frame[0].toInt() and 0x7f) != 0x7f)1 else 4
        require(frame.size>=prefix)
        val declared=if(abridged){if(prefix==1)(frame[0].toInt() and 0x7f).toLong()*4 else
            ((frame[1].toLong() and 255) or ((frame[2].toLong() and 255) shl 8) or ((frame[3].toLong() and 255) shl 16))*4
        }else le(frame,0) and 0x7fffffff
        require(declared==frame.size-prefix.toLong() && declared in 24..(PacketSplitter.MAX_PAYLOAD_BYTES+256).toLong())
        val payload=frame.copyOfRange(prefix,frame.size)
        val body=if(tag[0]==0xdd.toByte()){
            val plain=payload.take(8).all{it==0.toByte()}
            val size=if(plain)20+le(payload,16)else 24+((payload.size-24)/16)*16L
            val padding=payload.size-size
            require(size in 24..payload.size.toLong() && padding<=if(plain)256 else 15)
            payload.copyOf(size.toInt())
        }else payload
        require(body.size%4==0 && body.size<=PacketSplitter.MAX_PAYLOAD_BYTES)
        return body
    }
    fun reply(body:ByteArray,tag:ByteArray,padding:Int=random.nextInt(4),paddingBytes:(Int)->ByteArray={n->ByteArray(n).also(random::nextBytes)}):ByteArray {
        require(body.isNotEmpty() && body.size%4==0 && body.size<=PacketSplitter.MAX_PAYLOAD_BYTES && padding in 0..3)
        if(tag[0]==0xef.toByte()){
            val words=body.size/4
            return (if(words<127)byteArrayOf(words.toByte())else byteArrayOf(127,words.toByte(),(words shr 8).toByte(),(words shr 16).toByte()))+body
        }
        val n=if(tag[0]==0xdd.toByte())padding else 0
        val size=body.size+n
        return byteArrayOf(size.toByte(),(size shr 8).toByte(),(size shr 16).toByte(),(size shr 24).toByte())+body+paddingBytes(n).also{require(it.size==n)}
    }
}
