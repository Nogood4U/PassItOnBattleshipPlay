package controllers

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.pattern._
import cats.data.OptionT
import cats.implicits._
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import game.player._
import game.server.GameRoom
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

  def cancelMatchMaking: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey)
      .flatMap(player =>
        player.map(doCancelMatchMaking).getOrElse(Future.successful(NotFound))
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

  def readyCheck(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    (for {
      player <- OptionT(battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey))
      status <- OptionT.liftF(doSendGameRequestMessage(player, GameRoom.BoardReady(player)))
    } yield status).value.map {
      case Some(value) => value
      case None => NotFound
    }.recover {
      case _ => InternalServerError
    }
  }

  def addPiece(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    val placeResult = request.body.asJson
    (for {
      jsonData <- OptionT(Future.successful(placeResult))
      player <- OptionT(battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey))
      status <- OptionT.liftF(doSendGameRequestMessage(player, GameRoom.AddPiece(player, jsonData)))
    } yield status).value.map {
      case Some(value) => value
      case None => NotFound
    }.recover {
      case _ => InternalServerError
    }
  }

  def requestServerUpdate(): Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    (for {
      player <- OptionT(battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey))
      status <- OptionT.liftF(doSendGameMessage(player, GameRoom.RequestStateUpdate()))
    } yield status).value.map {
      case Some(value) => value
      case None => NotFound
    }.recover {
      case _ => InternalServerError
    }
  }


  private def doJoinMatchMaking(player: BattlePlayer) = {
    val playerActorFuture = battlePlayerService.getOrCreateActor(player)

    (for {
      playerActor <- playerActorFuture
    } yield {
      (playerActor ? OnlinePlayer.JoinServerMatchMaking()) (FiniteDuration(10, TimeUnit.SECONDS)).mapTo[Int]
        .map(_ => Ok)
        .recover { case _ => InternalServerError }
    }).flatten
  }

  private def doCancelMatchMaking(player: BattlePlayer) = {
    val playerActorFuture = battlePlayerService.getOrCreateActor(player)

    (for {
      playerActor <- playerActorFuture
    } yield {
      (playerActor ? OnlinePlayer.CancelServerMatchMaking()) (FiniteDuration(10, TimeUnit.SECONDS)).mapTo[Int]
        .map(_ => Ok)
        .recover { case _ => InternalServerError }
    }).flatten
  }

  private def doRejectGameRequest(player: BattlePlayer, requestId: String) = {
    doSendGameRequestMessage(player, OnlinePlayer.RejectGame(requestId))
  }

  private def doAcceptGameRequest(player: BattlePlayer, requestId: String) = {
    doSendGameRequestMessage(player, OnlinePlayer.AcceptGame(requestId))
  }

  private def doSendGameRequestMessage(player: BattlePlayer, f: => Any) = {
    val playerActorFuture = battlePlayerService.getOrCreateActor(player)

    (for {
      playerActor <- playerActorFuture
    } yield {
      (playerActor ? f) (FiniteDuration(10, TimeUnit.SECONDS)).mapTo[Int]
        .map(_ => Ok)
        .recover { case _ => InternalServerError }
    }).flatten
  }

  private def doSendGameMessage(player: BattlePlayer, f: => Any) = {
    val playerActorFuture = battlePlayerService.getOrCreateActor(player)

    (for {
      playerActor <- playerActorFuture
    } yield playerActor ! f).map(_ => Ok)
  }

}
