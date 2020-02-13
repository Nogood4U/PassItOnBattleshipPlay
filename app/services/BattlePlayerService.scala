package services

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import game.model.GameServer.AddPlayer
import game.model.OnlinePlayer
import javax.inject.{Inject, Singleton}
import models.{BattlePlayer, Tables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration.FiniteDuration


class BattlePlayerService @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {

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

  def getOrCreateActor(player: BattlePlayer)(implicit actorSystem: ActorSystem) =
    actorSystem
      .actorSelection(s"/user/Player_${player.id}")
      .resolveOne(FiniteDuration(2, TimeUnit.SECONDS))
      .recover { case _ =>
        val actorRef = actorSystem.actorOf(OnlinePlayer.props(player), s"Player_${player.id}")
        actorSystem.actorSelection(s"/user/Server_Main") // will be changed
          .resolveOne(FiniteDuration(1, TimeUnit.MINUTES)).foreach(_ ! AddPlayer(actorRef, player))
        actorRef
      }
}
