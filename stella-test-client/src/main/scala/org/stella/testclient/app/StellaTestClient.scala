package org.stella.testclient.app

import java.util.Collections

import io.netty.buffer.ByteBufAllocator
import io.rsocket.{ConnectionSetupPayload, Payload}
import io.rsocket.metadata.{CompositeMetadataFlyweight, TaggingMetadataFlyweight, WellKnownMimeType}
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.core.publisher.{Flux, FluxSink}

object StellaTestClient extends App {

  import io.rsocket.RSocketFactory
  import io.rsocket.util.DefaultPayload

  val client = RSocketFactory.connect.transport(TcpClientTransport.create(7878)).start.block



  val source = Flux.create[Payload]((emitter: FluxSink[Payload]) => {
    emitter.next(DefaultPayload.create(ByteBufAllocator.DEFAULT.buffer(), createMetadata("program.training")))

    System.out.println("Routing metadata sent")

    emitter.next(DefaultPayload.create("picasso=ok"))
    System.out.println("Data1 sent")
    emitter.next(DefaultPayload.create("histoire=ok"))
    System.out.println("Data2 sent")
    emitter.complete()
    System.out.println("Completed")
   })
  System.out.println("Opening channel")
  client.requestChannel(source).//
    doOnNext(p => System.out.println(p.getDataUtf8)).blockLast()

  def createMetadata(route: String) = {
    val metadataBuffer = ByteBufAllocator.DEFAULT.compositeBuffer();
    val routeBuffer = TaggingMetadataFlyweight.createRoutingMetadata(ByteBufAllocator.DEFAULT, Collections.singletonList(route)).getContent()
    CompositeMetadataFlyweight.encodeAndAddMetadata(metadataBuffer, ByteBufAllocator.DEFAULT, WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, routeBuffer)
    metadataBuffer
  }
}
