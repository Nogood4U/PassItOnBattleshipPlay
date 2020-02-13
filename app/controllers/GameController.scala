package controllers

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import com.mohiva.play.silhouette.api.Silhouette
import com.mohiva.play.silhouette.api.actions.SecuredRequest
import game.model.GameServer.{AddPlayer, JoinServerMatchMaking}
import javax.inject.Inject
import models.BattlePlayer
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents, Result}
import services.BattlePlayerService

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

class GameController @Inject()(cc: ControllerComponents,
                               battlePlayerService: BattlePlayerService,
                               silhouette: Silhouette[DefaultEnv])(implicit actorSystem: ActorSystem, exec: ExecutionContext) extends AbstractController(cc) {

  def joinMatchMaking: Action[AnyContent] = silhouette.SecuredAction.async { implicit request: SecuredRequest[DefaultEnv, AnyContent] =>
    battlePlayerService.getBattlePlayer(request.identity.loginInfo.providerKey)
      .flatMap(player =>
        player.map(doJoinMatchMaking).getOrElse(Future.successful(NotFound))
      ).recover {
      case _ => NotFound
    }
  }

  private def doJoinMatchMaking(player: BattlePlayer) = {
    import akka.pattern._

    val playerActorFuture = battlePlayerService.getOrCreateActor(player)
    val serverActorFuture = actorSystem.actorSelection(s"/user/Server_Main")
      .resolveOne(FiniteDuration(1, TimeUnit.MINUTES))

    (for {
      playerActor <- playerActorFuture
      serverActor <- serverActorFuture
    } yield {
      (serverActor ? JoinServerMatchMaking(player)) (FiniteDuration(10, TimeUnit.SECONDS)).mapTo[Int]
        .map(_ => Ok)
        .recover { case _ => InternalServerError }
    }).flatten
  }
}
