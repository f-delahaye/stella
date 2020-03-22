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
class ProgramCacheWithoutSnapshotSpec extends ScalaTestWithActorTestKit(s"""
      akka.persistence.journal.plugin = "akka.persistence.journal.inmem"
      akka.persistence.journal.inmem.test-serialization = on
    """
) with AnyWordSpecLike{

    "ProgramCache" must {

        "handle Load requests" in {
            val probe = createTestProbe[ProgramCache.ProgramsLoadedFromCache]()
            val ref = spawn(ProgramCache("1"))
            val date = LocalDate.now
            val program1 = Program(LocalDateTime.now, "channel1", "title1", "summary1")
            val program2 = Program(LocalDateTime.now, "channel2", "title2", "summary2")
            // Cache empty, nothing returned
            ref ! ProgramCache.LoadProgramsFromCache(date, "channel1", probe.ref)
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(date, "channel1", None))

            // Cache populated for requested channel, programs returned
            ref ! ProgramCache.StoreProgramsInCache(date, "channel1", List(program1))
            ref ! ProgramCache.StoreProgramsInCache(date, "channel2", List(program2))
            ref ! ProgramCache.LoadProgramsFromCache(date, "channel1", probe.ref)
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(date, "channel1", Some(List(program1))))

            // Cache populated but not for requested channel, nothing returned
            ref ! ProgramCache.LoadProgramsFromCache(date, "channel3", probe.ref)
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(date, "channel3", None))

        }

        "limit the number of days stored to the configured parameter" in {
            val today = LocalDate.now
            val yesterday = LocalDate.now.minusDays(1)
            val todaysProgram = Program(today.atStartOfDay(), "channel1", "today", "today")
            val yesterdaysProgram = Program(yesterday.atStartOfDay(), "channel1", "yesterday", "yesterday")
            
            val ref = spawn(ProgramCache("2", 1))
            val probe = createTestProbe[ProgramCache.ProgramsLoadedFromCache]()
            
            ref ! ProgramCache.StoreProgramsInCache(yesterday, "channel1", List(yesterdaysProgram))
            ref ! ProgramCache.StoreProgramsInCache(today, "channel1", List(todaysProgram))
            ref ! ProgramCache.LoadProgramsFromCache(yesterday, "channel1", probe.ref)
            // Not in the cache any more
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(yesterday, "channel1", None))

            ref ! ProgramCache.LoadProgramsFromCache(today, "channel1", probe.ref)
            // today still in the cache, though...
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(today, "channel1", Some(List(todaysProgram))))
        }

        //
        "not limit the number of channels" in {
            // 1 is a limit on the number of days allowed, NOT the number of channels, or the number of entries in the cache.
            // Here there will be 2 entries, but both on the same day, so all programs must be stored.
            val ref = spawn(ProgramCache("3", 1))
            val probe = createTestProbe[ProgramCache.ProgramsLoadedFromCache]()

            val date = LocalDate.now

            val program1 = Program(LocalDateTime.now, "channel1", "title1", "summary1")
            val program2 = Program(LocalDateTime.now, "channel2", "title2", "summary2")

            ref ! ProgramCache.StoreProgramsInCache(date, "channel1", List(program1))
            ref ! ProgramCache.StoreProgramsInCache(date, "channel2", List(program2))

            ref ! ProgramCache.LoadProgramsFromCache(date, "channel1", probe.ref)
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(date, "channel1", Some(List(program1))))
            ref ! ProgramCache.LoadProgramsFromCache(date, "channel2", probe.ref)
            probe.expectMessage(ProgramCache.ProgramsLoadedFromCache(date, "channel2", Some(List(program2))))

        }

    }
}
