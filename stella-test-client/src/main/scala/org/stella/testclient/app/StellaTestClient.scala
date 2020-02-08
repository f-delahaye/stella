package org.stella.testclient.app

import java.nio.CharBuffer
import java.nio.charset.StandardCharsets
import java.util.Collections
import java.util.function.Consumer

import io.netty.buffer.ByteBufAllocator
import io.rsocket.Payload
import io.rsocket.metadata.{CompositeMetadataFlyweight, TaggingMetadataFlyweight, WellKnownMimeType}
import io.rsocket.transport.netty.client.TcpClientTransport
import org.reactivestreams.Publisher
import reactor.core.publisher.{Flux, FluxSink}

object StellaTestClient {

  import io.rsocket.RSocketFactory
  import io.rsocket.util.DefaultPayload

  private val rsocket = RSocketFactory.connect.transport(TcpClientTransport.create(7878)).start.block

  def dispose() = rsocket.dispose()

  def configure(trainedProgramsEmitter: Consumer[FluxSink[String]]): Unit = {

    def buildPayloads(first: String, all: Flux[String]): Publisher[Payload] = Flux.just(createPayloadWithRoute(first, "program.training"))
      .concatWith(all.skip(1).map(DefaultPayload.create))

    val trainedProgramsSource: Flux[Payload] = Flux.create[String](trainedProgramsEmitter).log().
      switchOnFirst((first, all) => buildPayloads(first.get, all))
//      .subscribeOn(Schedulers.newSingle("test"), true)

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

  def sendTrainedProgramsFromConsole(emitter: FluxSink[String]) = {
    Iterator.continually {
      System.out.println("Enter trained program:")
      scala.io.StdIn.readLine
    }.takeWhile(_.nonEmpty).foreach { line =>
      System.out.println(s"Emitting $line")
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

