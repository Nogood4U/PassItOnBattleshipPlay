package services

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, ActorSystem}
import akka.pattern.ask
import akka.util.Timeout
import game.player.OnlinePlayer
import game.server.GameServer.AddPlayer
import javax.inject.Inject
import models.{BattlePlayer, Tables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}

class BattlePlayerService @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {
  implicit val timeout: Timeout = Timeout(3.seconds)

  def saveBattlePlayer(player: BattlePlayer): Future[BattlePlayer] = {
    db.run(
      for {
        _ <- Tables.battlePlayers.insertOrUpdate(player)
        inserted <- Tables.battlePlayers.filter(_player => _player.battleUserInfoId === player.userInfoId).result.head
      } yield {
        inserted
      })
  }

  def getBattlePlayer(authId: String): Future[Option[BattlePlayer]] = db.run((for {
    player <- Tables.battlePlayers if player.battleUserInfoId === authId
  } yield player).result.headOption)

  def getOrCreateActor(player: BattlePlayer)(implicit actorSystem: ActorSystem): Future[ActorRef] =
    actorSystem
      .actorSelection(s"/user/Player_${player.id}")
      .resolveOne(FiniteDuration(2, TimeUnit.SECONDS))
      .recover { case _ => actorSystem.actorOf(OnlinePlayer.props(player), s"Player_${player.id}") }

  def joinServer(player: BattlePlayer, playerActor: ActorRef)(implicit actorSystem: ActorSystem): Future[ActorRef] = {
    actorSystem.actorSelection(s"/user/Server_Main") // will be changed
      .resolveOne(FiniteDuration(1, TimeUnit.MINUTES))
      .map(server => {
        server ! AddPlayer(playerActor, player)
        playerActor
      })
  }

  def getPlayerStatus(player: BattlePlayer)(implicit actorSystem: ActorSystem): Future[OnlinePlayer.PlayerStatus] =
    for {
      actor <- getOrCreateActor(player)
      result <- actor ? OnlinePlayer.PlayerStatus(player, None)
    } yield result match {
      case msg: OnlinePlayer.PlayerStatus => msg
    }

}
