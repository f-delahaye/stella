package org.stella.testclient.app

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.function.Consumer

import io.netty.buffer.ByteBufAllocator
import io.rsocket.Payload
import io.rsocket.metadata.{CompositeMetadataFlyweight, TaggingMetadataFlyweight, WellKnownMimeType}
import io.rsocket.transport.netty.client.TcpClientTransport
import reactor.core.publisher.{Flux, FluxSink, Mono}
import reactor.core.scheduler.Schedulers

object StellaTestClient {

  import io.rsocket.RSocketFactory
  import io.rsocket.util.DefaultPayload

  private val rsocket = RSocketFactory.connect.transport(TcpClientTransport.create(7878)).start.block

  def dispose(): Unit = rsocket.dispose()

  def configure(trainedProgramsEmitter: Consumer[FluxSink[String]]): Unit = {

    val trainedProgramsSource: Flux[Payload] =
// Using switchOnFirst is neater: all client does is emitting items, and all server does is receiving them, with the route medata being added under the hoods in the first message
// However, using switchOnFirst along with FluxSink is tricky: fluxSink will only be available when subscribe() is called, and subscribe will only be called when the first item is sent which requires the emitter?
// So maybe that is possible but i couldn't find out how. Instead, we use a dummy first item with no data, just the routing metadata, which the server will have to ignore.
// The switchOnFirst logic is retained below for documentation purposes.
//      Flux.create[String](trainedProgramsEmitter).switchOnFirst[Payload]((first, all) => Flux.just(createPayloadWithRoute(first.get, "program.training")).concatWith(all.skip(1).map(DefaultPayload.create)))
      Mono.just(createPayloadWithRoute("", "program.training"))
        .concatWith(Flux.create[String](trainedProgramsEmitter).map(DefaultPayload.create))
        .subscribeOn(Schedulers.newSingle("subscribe thread"), false)

    rsocket.requestChannel(trainedProgramsSource.doOnNext(p => System.out.println(p.getDataUtf8)))
      .doOnNext(p => System.out.println(p.getDataUtf8))
      .blockLast()
  }

  // Surely, there's an easier way to create a DefaultPayload ?!
  private def createPayloadWithRoute(data: String, route: String) =
    DefaultPayload.create(StandardCharsets.UTF_8.encode(CharBuffer.wrap(data)), createMetadata(route))


  private def createMetadata(route: String) = {
    val metadataBuffer = ByteBufAllocator.DEFAULT.compositeBuffer;
    val routeBuffer = TaggingMetadataFlyweight.createRoutingMetadata(ByteBufAllocator.DEFAULT, Collections.singletonList(route)).getContent
    CompositeMetadataFlyweight.encodeAndAddMetadata(metadataBuffer, ByteBufAllocator.DEFAULT, WellKnownMimeType.MESSAGE_RSOCKET_ROUTING, routeBuffer)
    metadataBuffer.nioBuffer()
  }

  def sendTrainedProgramsFromConsole(emitter: FluxSink[String]): Unit = {
    Iterator.continually {
      System.out.println("Enter trained program:")
      scala.io.StdIn.readLine
    }.takeWhile(_.nonEmpty).foreach { line =>
      emitter.next(line)
    }
    emitter.complete()
  }

  final def main(args: Array[String]): Unit = {
    val client = StellaTestClient
    try {
      client.configure(sendTrainedProgramsFromConsole)
    } finally {
      client.dispose()
    }

  }

}

