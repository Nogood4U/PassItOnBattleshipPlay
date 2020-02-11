package services

import javax.inject.{Inject, Singleton}
import models.{BattlePlayer, Tables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._


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
  } yield (player)).result.headOption)
}
