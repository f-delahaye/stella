package org.stella.testclient.app

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Collections

import io.netty.buffer.ByteBufAllocator
import io.rsocket.Payload
import io.rsocket.metadata.{CompositeMetadataFlyweight, TaggingMetadataFlyweight, WellKnownMimeType}
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.core.publisher.{Flux, FluxSink}

object StellaTestClient extends App {

  import io.rsocket.RSocketFactory
  import io.rsocket.util.DefaultPayload

  val client = RSocketFactory.connect.transport(TcpClientTransport.create(7878)).start.block

  val source: Flux[Payload] = Flux.create[String]((emitter: FluxSink[String]) => {
    emitter.next("item1=ok")
    emitter.next("item2=ok")
    emitter.complete()
  }).switchOnFirst((first, all) =>
    Flux.just(createPayloadWithRoute(first.get(), "program.training"))
      .concatWith(all.skip(1).map(DefaultPayload.create)))

  try {
    client.requestChannel(source.doOnNext(p => System.out.println(p.getDataUtf8))). //
      doOnNext(p => System.out.println(p.getDataUtf8)).blockLast()
  } finally {
    client.dispose()
  }

  // Surely, there's an easier way to create a DefaultPayload ?!
  def createPayloadWithRoute(data: String, route: String) =
    DefaultPayload.create(StandardCharsets.UTF_8.encode(CharBuffer.wrap(data)), createMetadata(route))

  def createMetadata(route: String) = {
    val metadataBuffer = ByteBufAllocator.DEFAULT.compositeBuffer;
    val routeBuffer = TaggingMetadataFlyweight.createRoutingMetadata(ByteBufAllocator.DEFAULT, Collections.singletonList(route)).getContent
    CompositeMetadataFlyweight.encodeAndAddMetadata(metadataBuffer, ByteBufAllocator.DEFAULT, WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, routeBuffer)
    metadataBuffer.nioBuffer()
  }
}
