package services

import javax.inject.{Inject, Singleton}
import models.{BattlePlayer, Tables}
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits._


class BattlePlayerService @Inject()(protected val dbConfigProvider: DatabaseConfigProvider) extends HasDatabaseConfigProvider[JdbcProfile] {

  def saveBattlePlayer(player: BattlePlayer): Future[Int] = {
    db.run(Tables.battlePlayers.insertOrUpdate(player))
  }

  def getBattlePlayer(authId: String): Future[BattlePlayer] = db.run((for {
    player <- Tables.battlePlayers if player.battleUserInfoId === authId
  } yield (player)).result.head)
}
