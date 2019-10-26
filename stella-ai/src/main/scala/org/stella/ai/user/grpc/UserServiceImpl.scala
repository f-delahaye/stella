package org.stella.ai.user.grpc

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import org.stella.ai.tv.TvProgramClassifier.{SendClassifierDataTraining, UserTrainingConnectionEstablished}

class UserServiceImpl(val classifier: ActorRef) extends UserService {
  
  /**
   * Client sends trained data and received untrained data that will get sent back to server as trained data.
   * Hence this is not a classical request / response scenario but more of a Flow (as in Akka Flow)
   */
  def trainClassifier(in: Source[TrainedData, NotUsed]): Source[UntrainedData, NotUsed] = {
	  in.map(trained => List((trained.summary, trained.summary))).map(SendClassifierDataTraining)

    val out: Source[UntrainedData, NotUsed] =
      Source
        .queue[List[String]](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)
        .mapConcat(list => list)
        .map(untrained => UntrainedData(untrained))
        .mapMaterializedValue { queue =>
          classifier ! UserTrainingConnectionEstablished(queue)
          // don't expose the queue anymore
          NotUsed
        }
    out
  }
  
}
