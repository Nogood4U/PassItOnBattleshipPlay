package controllers

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.pattern._
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import game.player._
import game.server.GameServer.{AcceptGame, GameRequestMessage, JoinServerMatchMaking, RejectGame}
import javax.inject.Inject
import models.BattlePlayer
import play.api.libs.json._
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import services.BattlePlayerService

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}


class GameController @Inject()(cc: ControllerComponents,
                               battlePlayerService: BattlePlayerService,
                               silhouette: Silhouette[DefaultEnv])(implicit actorSystem: ActorSystem, exec: ExecutionContext) extends AbstractController(cc) {

  implicit val roleWrites: Writes[OnlinePlayerStatus] = {
    case ONLINE => JsString(ONLINE.toString)
    case INVISIBLE => JsString(INVISIBLE.toString)
    case OFFLINE => JsString(OFFLINE.toString)
    case LOOKING_FOR_GAME => JsString(LOOKING_FOR_GAME.toString)
  }
  implicit val playerWrites: OWrites[BattlePlayer] = Json.writes[BattlePlayer]
  implicit val playerOWrites: OWrites[OnlinePlayer.PlayerStatus] = Json.writes[OnlinePlayer.PlayerStatus]

  def joinMatchMaking: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey)
      .flatMap(player =>
        player.map(doJoinMatchMaking).getOrElse(Future.successful(NotFound))
      ).recover {
      case _ => NotFound
    }
  }

  def rejectGameRequest(requestId: String): Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey)
      .flatMap(player =>
        player.map(_player => doRejectGameRequest(_player, requestId)).getOrElse(Future.successful(NotFound))
      ).recover {
      case _ => NotFound
    }
  }

  def acceptGameRequest(requestId: String): Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey)
      .flatMap(player =>
        player.map(_player => doAcceptGameRequest(_player, requestId)).getOrElse(Future.successful(NotFound))
      ).recover {
      case _ => NotFound
    }
  }


  private def doJoinMatchMaking(player: BattlePlayer) = {
    val playerActorFuture = battlePlayerService.getOrCreateActor(player)
    val serverActorFuture = actorSystem.actorSelection(s"/user/Server_Main")
      .resolveOne(FiniteDuration(1, TimeUnit.MINUTES))

    (for {
      _ <- playerActorFuture
      serverActor <- serverActorFuture
    } yield {
      (serverActor ? JoinServerMatchMaking(player)) (FiniteDuration(10, TimeUnit.SECONDS)).mapTo[Int]
        .map(_ => Ok)
        .recover { case _ => InternalServerError }
    }).flatten
  }

  private def doRejectGameRequest(player: BattlePlayer, requestId: String) = {
    doSendGameRequestMessage(player, requestId, RejectGame(player, requestId))
  }

  private def doAcceptGameRequest(player: BattlePlayer, requestId: String) = {
    doSendGameRequestMessage(player, requestId, AcceptGame(player, requestId))
  }

  private def doSendGameRequestMessage(player: BattlePlayer, requestId: String, f: => GameRequestMessage) = {
    val playerActorFuture = battlePlayerService.getOrCreateActor(player)
    val serverActorFuture = actorSystem.actorSelection(s"/user/Server_Main")
      .resolveOne(FiniteDuration(1, TimeUnit.MINUTES))
    (for {
      _ <- playerActorFuture
      serverActor <- serverActorFuture
    } yield {
      (serverActor ? f) (FiniteDuration(10, TimeUnit.SECONDS)).mapTo[Int]
        .map(_ => Ok)
        .recover { case _ => InternalServerError }
    }).flatten
  }

}
