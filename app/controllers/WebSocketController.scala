package controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import akka.stream.scaladsl._
import com.mohiva.play.silhouette.api._
import game.model.OnlinePlayer
import javax.inject.Inject
import models.BattlePlayer
import play.api.libs.streams.ActorFlow
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

class WebSocketController @Inject()(cc: ControllerComponents, silhouette: Silhouette[DefaultEnv])
                                   (implicit exec: ExecutionContext, mat: Materializer, actorSystem: ActorSystem) extends AbstractController(cc) {


  def ws = WebSocket.acceptOrResult[String, String] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette.SecuredRequestHandler { securedRequest =>
      Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
    }.map {
      case HandlerResult(r, Some(value)) => Right(ActorFlow.)
      case HandlerResult(r, None) => Left(r)
    }
  }

  private def getWebsocketFlow(player: BattlePlayer) = {
    val playerActor = actorSystem.actorOf(OnlinePlayer.props(player), s"Player-${player.id}")

    Sink.actorRef(playerActor, 1)
    /* val (outActor, publisher) = Source
       .actorRef[Out](bufferSize, overflowStrategy)
       .toMat(Sink.asPublisher(false))(Keep.both)
       .run()*/

    // We read from sink
    // Play read from source and sends to client
  }

}
