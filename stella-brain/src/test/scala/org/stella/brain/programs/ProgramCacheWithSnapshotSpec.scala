package org.stella.brain.programs

import org.stella.brain.programs.ProgramCache
import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.junit.runner.RunWith
import org.mockito.Mockito
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.junit.JUnitRunner
import org.scalatestplus.mockito.MockitoSugar
import java.time.LocalDate
import java.{util => ju}
import java.time.LocalDateTime

/**
 * Synchronous testng for persistence not yet available, but is being tracked by https://github.com/akka/akka/issues/23712
 * So we go with asynchronous testing, and as one of the comments puts it anyway, whats there we can't do with asynchronous testing?
 * 
 * All tests in this class expect no snapshot. Tests which do are in a separate test class. Can snapshot be configured per test and not at ScalaTestWithActorTestKit level?
 */
@RunWith(classOf[JUnitRunner])
class ProgramCacheWithSnapshotSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.snapshot-store.plugin = "akka.persistence.snapshot-store.local"
      akka.persistence.snapshot-store.local.dir = "target/snapshot-${ju.UUID.randomUUID().toString}"
    """
) with AnyWordSpecLike {

    "ProgramCache" must {

        "handle Load requests" in {
            val probe = createTestProbe[ProgramCache.ProgramsLoadedFromCache]()
            val ref = spawn(ProgramCache("1"))
            val date = LocalDate.now
            val program1 = Program(LocalDateTime.now, "channel1", "title1", "summary1")
            val program2 = Program(LocalDateTime.now, "channel2", "title2", "summary2")
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(date, "channel1", None))
            ref ! ProgramCache.StoreProgramsInCache(date, "channel", List(program1, program2))
            ref ! ProgramCache.LoadProgramsFromCache(date, "channel1", probe.ref)
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(date, "channel1", Some(List(program1))))
        }
    }
}
