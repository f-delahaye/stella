package org.stella.brain.programs

import java.io.ByteArrayInputStream
import java.time.LocalDate

import akka.actor.testkit.typed.FishingOutcome
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import org.stella.brain.programs.LInternauteOverviewCrawler.{ProgramsLoadedFromCacheAdapted, RequestLInternautePrograms, RequestMode, SendLInternautePrograms, URLStreamProvider}

import scala.concurrent.duration._

@RunWith(classOf[JUnitRunner])
class LInternateOverviewCrawlerSpec extends ScalaTestWithActorTestKit with AnyWordSpecLike with MockitoSugar {

  implicit val actorSystem = testKit.system
  val date = LocalDate.of(2020, 1, 1)
  val channel = "channel"
  val urlStreamProvider: URLStreamProvider = mock[URLStreamProvider]
  val programCache = testKit.createTestProbe[ProgramCache.Command]()
// Always pass in a urlStreamProvider. Unless it is stubbed, otherwise it will cause a NPE if called, so this allows us to fail in case of unexpected calls to the url mode.
  val crawler = testKit.spawn(LInternauteOverviewCrawler(programCache.ref, urlStreamProvider))

  "LInternaute overview crawler" must {

    "query cache when so asked" in {
      crawler ! RequestLInternautePrograms(date, channel, null, RequestMode.CacheOrUrl)

      programCache.fishForMessage(1.second)(msg => msg match {
        case ProgramCache.LoadProgramsFromCache(date_, channel_, _) if (date == date_ && channel_ == channel) => FishingOutcome.Complete
        case _ =>   FishingOutcome.Fail(s"Unexpected message:$msg")
      })
    }

    "query url upon cache miss" in {
      val replyTo = testKit.createTestProbe[SendLInternautePrograms]()
      // Return anything in order to avoid NPE
      Mockito.when(urlStreamProvider.apply(date, channel)).thenReturn(new ByteArrayInputStream( Array[Byte]()))

      crawler ! ProgramsLoadedFromCacheAdapted(date, channel, None, replyTo.ref)


      // 1. The URL should be queried
      Mockito.verify(urlStreamProvider, Mockito.times(1))
      // 2. Then the programs should be stored in to the cache
      programCache.expectMessage(ProgramCache.StoreProgramsInCache(date, channel, List()))
      // 3. And also sent back to client
      replyTo.expectMessage(1.second, SendLInternautePrograms(date, channel, List()))

    }

    "send cached programs to client upon cache hit" in {
      val replyTo = testKit.createTestProbe[SendLInternautePrograms]()

      val programs = List(Program(date.atTime(14, 0), channel, "title", "summary"))
      crawler ! ProgramsLoadedFromCacheAdapted(date, channel, Some(programs), replyTo.ref)

      replyTo.expectMessage(1.second, SendLInternautePrograms(date, channel, programs))
    }

  }
}
