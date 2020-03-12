package controllers

import akka.NotUsed
import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Broadcast, BroadcastHub, Flow, Sink, Source}
import akka.stream.{Materializer, OverflowStrategy}
import com.google.inject.{AbstractModule, Provides}
import game.server.GameRoom.GameRoomUpdate
import javax.inject.{Named, Singleton}
import net.codingwell.scalaguice.ScalaModule
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import play.api.libs.json.{JsValue, Json}

class SpectatorModule extends AbstractModule with ScalaModule {

  @Provides
  @Named("spectator-socket")
  @Singleton
  def spectatorSocket(mat: Materializer): (ActorRef, Source[String, NotUsed]) = {

    val eventStreamSource: Source[JsValue, ActorRef] = Source.actorRef[JsValue](500, OverflowStrategy.dropTail)
//    val spectatorPublisher: Sink[String, Publisher[String]] = Sink.asPublisher[String](fanout = true)


    val matFlow: (ActorRef, Source[String, NotUsed]) = Flow[JsValue]
      .map(data => Json.stringify(data))
      .runWith(eventStreamSource, BroadcastHub.sink(256))(mat)

    matFlow
  }

  @Provides
  @Named("spectator-actor")
  @Singleton
  def spectatorActor(@Named("spectator-socket") socket: (ActorRef, Source[String, NotUsed]), actorSystem: ActorSystem): ActorRef = {
    val actorProps = Props(new Actor {

      import services.BoardDataParser._

      override def receive: Receive = {
        case msg: GameRoomUpdate => socket._1 ! Json.toJson(msg.gameState)
      }
    })
    actorSystem.actorOf(actorProps)
  }
}
