package org.stella.brain.programs

import java.time.{LocalDate, LocalDateTime}

import akka.actor.testkit.typed.Effect.{MessageAdapter, Spawned}
import akka.actor.testkit.typed.scaladsl.{BehaviorTestKit, ScalaTestWithActorTestKit, TestInbox}
import org.junit.runner.RunWith
import org.scalatest.WordSpecLike
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import org.stella.brain.programs.ProgramCollector.ProgramsByDate

@RunWith(classOf[JUnitRunner])
class ProgramCollectorSpec extends ScalaTestWithActorTestKit with WordSpecLike  with MockitoSugar {

  "program collector" must {

    "query all channels" in {
      val testKit = BehaviorTestKit(ProgramCollector(List("channel1", "channel2")))
      val probe = TestInbox[ProgramsByDate]()

      testKit.run(ProgramCollector.ProgramsByDateRequest(LocalDate.now, probe.ref))
      testKit.expectEffectType[MessageAdapter[Any, Any]]
      // Spawned asserts equality on childName, Props ... and behavior using reference equality
      // so we can't use expectEffect(Spawned(LInternateOverviewCrawler(), <name>) as LInternauteOverviewCrawler will create a new instance
      // which  while functionally equivalent to the one actually used at runtime, is not ===.
      testKit.expectEffectPF {case Spawned(_, name, _) if name == "LInternauteCrawlerchannel1" =>}
      testKit.expectEffectPF {case Spawned(_, name, _) if name == "LInternauteCrawlerchannel2" =>}

    }

    "wait for all channels programs" in {
      val testKit = BehaviorTestKit(ProgramCollector(List("channel1", "channel2")))
      val probe = TestInbox[ProgramsByDate]()

      val channel1Programs = List(Program(LocalDateTime.now(), "channel1", "foo1.1", "bar1.1"), Program(LocalDateTime.now(), "channel1", "foo1.2", "bar1.2"))
      val channel2Programs = List(Program(LocalDateTime.now(), "channel2", "foo2.1", "bar2.1"))
      testKit.run(ProgramCollector.ProgramsByDateRequest(LocalDate.now, probe.ref))
      testKit.run(ProgramCollector.ProgramsByDateAndChannelAdapted(LocalDate.now, "channel1", channel1Programs))

      assert(!probe.hasMessages)

      testKit.run(ProgramCollector.ProgramsByDateAndChannelAdapted(LocalDate.now, "channel2", channel2Programs))

      assert(probe.hasMessages)
      probe.expectMessage(ProgramsByDate(LocalDate.now(), channel1Programs ::: channel2Programs))

    }
  }
}
