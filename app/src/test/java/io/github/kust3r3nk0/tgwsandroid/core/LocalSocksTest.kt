package io.github.kust3r3nk0.tgwsandroid.core

import org.junit.Assert.*
import org.junit.Test
import java.net.*
import java.io.*
import java.util.concurrent.*

class LocalSocksTest {
    private val loop=InetAddress.getByAddress(byteArrayOf(127,0,0,1))
    private fun read(input:InputStream,n:Int)=ByteArray(n).also{b->var pos=0;while(pos<n){val count=input.read(b,pos,n-pos);check(count>0);pos+=count}}
    private fun access()=object:NetworkAccess {override val validated=true
        override fun addresses(host:String)=listOf(InetAddress.getByAddress(byteArrayOf(1,2,3,4)))
        override fun socket():Socket=error("Direct socket bypassed local proxy")
    }
    @Test(timeout=5000) fun ipv6DestinationAndDomainBoundAddressAreParsedWithoutConsumingPayload(){
        val address=InetAddress.getByAddress(ByteArray(16){if(it==15)1 else 0})
        val network=object:NetworkAccess {override val validated=true
            override fun addresses(host:String)=listOf(address)
            override fun socket():Socket=error("Direct IPv6 bypass")
        }
        ServerSocket(0,1,loop).use{server->val executor=Executors.newSingleThreadExecutor()
            try{val future=executor.submit{server.accept().use{p->
                p.soTimeout=2000;read(p.getInputStream(),3);p.getOutputStream().write(byteArrayOf(5,0))
                val request=read(p.getInputStream(),22)
                assertArrayEquals(byteArrayOf(5,1,0,4)+address.address+byteArrayOf(1,-69),request)
                p.getOutputStream().write(byteArrayOf(5,0,0,3,3,97,98,99,1,2,42))
            }}
                NetworkConnector(server.localPort){network}.use{c->c.connect("dc.example",443,2000){}.use{assertEquals(42,it.getInputStream().read())}}
                future.get(3,TimeUnit.SECONDS)
            }finally{executor.shutdownNow()}
        }
    }
    @Test fun fragmentedSocksReplyAndStreamPayloadUseOnlyLoopbackProxy(){
        ServerSocket(0,1,loop).use{server->
            val executor=Executors.newSingleThreadExecutor()
            val future=executor.submit{
                server.accept().use{peer->peer.soTimeout=3000;val input=peer.getInputStream();val out=peer.getOutputStream()
                    assertArrayEquals(byteArrayOf(5,1,0),read(input,3));out.write(byteArrayOf(5,0));out.flush()
                    assertArrayEquals(byteArrayOf(5,1,0,1,1,2,3,4,1,-69),read(input,10))
                    for(b in byteArrayOf(5,0,0,1,127,0,0,1,1,2)){out.write(b.toInt());out.flush()}
                    val body=read(input,65536);out.write(body);out.flush()
                }
            }
            try{val a=access();NetworkConnector(server.localPort){a}.use{connector->
                connector.connect("dc.example",443,3000){}.use{socket->
                    val body=ByteArray(65536){it.toByte()};socket.getOutputStream().write(body)
                    assertArrayEquals(body,read(socket.getInputStream(),body.size));assertEquals(0,socket.soTimeout)
                }
            };future.get(5,TimeUnit.SECONDS)}finally{executor.shutdownNow()}
        }
    }
    @Test fun unavailableProxyNeverDialsDestination(){
        val port=ServerSocket(0,1,loop).use{it.localPort};val a=access()
        NetworkConnector(port){a}.use{c->try{c.connect("dc.example",443,300){};fail()}catch(_:IOException){}}
    }
    @Test fun stopInterruptsStalledSocksNegotiation(){
        ServerSocket(0,1,loop).use{server->
            val a=access();val c=NetworkConnector(server.localPort){a};val executor=Executors.newSingleThreadExecutor()
            try{val result=executor.submit<Boolean>{try{c.connect("dc.example",443,5000){};false}catch(_:IOException){true}}
                server.accept().use{peer->peer.soTimeout=1000;read(peer.getInputStream(),3);c.close();assertTrue(result.get(2,TimeUnit.SECONDS))}
            }finally{c.close();executor.shutdownNow()}
        }
    }
    @Test fun rejectedMethodAndMalformedRepliesFailClosed(){
        for(reply in listOf(byteArrayOf(5,2),byteArrayOf(4,0),byteArrayOf(5,-1))){
            ServerSocket(0,1,loop).use{server->val executor=Executors.newSingleThreadExecutor()
                try{val future=executor.submit{server.accept().use{p->read(p.getInputStream(),3);p.getOutputStream().write(reply)}}
                    val a=access();NetworkConnector(server.localPort){a}.use{c->try{c.connect("dc.example",443,1000){};fail()}catch(_:IOException){}}
                    future.get(2,TimeUnit.SECONDS)
                }finally{executor.shutdownNow()}
            }
        }
    }
}
