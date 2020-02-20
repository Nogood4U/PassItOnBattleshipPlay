package controllers

import akka.NotUsed
import akka.actor.{ActorRef, ActorSystem}
import akka.stream.scaladsl._
import akka.stream.{Materializer, OverflowStrategy}
import com.mohiva.play.silhouette.api.{HandlerResult, Silhouette}
import game.player.OnlinePlayer
import javax.inject.Inject
import org.reactivestreams.Publisher
import play.api.libs.json.{JsValue, Json}
import play.api.mvc._
import services.BattlePlayerService

import scala.concurrent.{ExecutionContext, Future}

class
WebSocketController @Inject()(cc: ControllerComponents,
                              battlePlayerService: BattlePlayerService,
                              silhouette: Silhouette[DefaultEnv])
                             (implicit exec: ExecutionContext, mat: Materializer, actorSystem: ActorSystem) extends AbstractController(cc) {


  def ws(uid: String): WebSocket = WebSocket.acceptOrResult[String, String] { request =>
    implicit val req = Request(request, AnyContentAsEmpty)
    silhouette.SecuredRequestHandler { securedRequest =>
      Future.successful(HandlerResult(Ok, Some(securedRequest.identity)))
    }.flatMap {
      case HandlerResult(r, Some(identity)) =>
        battlePlayerService.getBattlePlayer(identity.loginInfo.providerKey).map(player => {
          player.map(_player => _player -> battlePlayerService.getOrCreateActor(_player))
            .map(actor => actor._2
              .flatMap(_actor => battlePlayerService.joinServer(actor._1, _actor))
              .map(_actor => getWebsocketFlow(_actor))
              .map(Right(_)))
        }).flatMap(_.getOrElse(Future.successful(Right(Flow.fromSinkAndSourceCoupled(Sink.ignore, Source.empty)))))
          .recover {
            case e => println(e)
              Right(Flow.fromSinkAndSourceCoupled(Sink.ignore, Source.empty))
          }
      case HandlerResult(r, None) => Future.successful(Left(Forbidden))
    }
  }


  private def getWebsocketFlow(playerActor: ActorRef) = {
    import scala.concurrent.duration._
    // We read from sink
    // Play read from source and sends to client

    // Flow1 - Sends data that gets to ActorRef to playerActor
    val mySource = Source.actorRef[String](100, OverflowStrategy.dropTail)
    val mySink = Sink.actorRef(playerActor, "{\"disconnected\":true}")
    //parse JsonInto ProperMessages
    val flow: Flow[String, JsValue, NotUsed] = Flow[String].map(data => Json.parse(data)) //map to Actor Message
    // ActorRef = sink to send to play
    val g: (ActorRef, NotUsed) = flow.runWith(mySource, mySink)
    // Flow2
    // Sink you can subscribe and receive message
    val publisherSink = Sink.asPublisher[String](fanout = false)
    // data send to this actor ref will be send to publisherSink
    val mySource2 = Source.actorRef[JsValue](100, OverflowStrategy.dropTail)
    // transforms JsValue to String
    val flow2: Flow[JsValue, String, NotUsed] = Flow[JsValue].map(data => Json.stringify(data))

    //ActorRed is out we can write to play , publisher is to create a source from , that will send data sent to ActorRef to Play
    val both: (ActorRef, Publisher[String]) = flow2.runWith(mySource2, publisherSink)

    playerActor ! OnlinePlayer.UpdateOutput(both._1)

    Flow.fromSinkAndSourceCoupled(Sink.actorRef[String](g._1, "{\"disconnected\":true}"), Source.fromPublisher[String](both._2)
      .keepAlive(1.minutes, () => "{}"))
  }

}
