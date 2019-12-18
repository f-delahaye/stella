package org.stella.brain.programs

import akka.actor.typed.scaladsl.Behaviors

/**
 * The main controller.
 *
 * - Works out which day should be collected
 * - Calls relevant actors to collect data
 * - Passes collected data to the classifier as well as to the event stream where they may be picked up by any user connection sitting there
 */
object ProgramArea {

  def apply() =
    Behaviors.setup(context =>
    )
}
