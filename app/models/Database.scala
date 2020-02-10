package models

import controllers.BattleUser


//db classes
case class BattlePlayer(id: Long,
                        playerTag: String,
                        mmr: Long,
                        userInfoId: String
                       )

//slick mappings

import slick.jdbc.H2Profile.api._

class BattlePlayerTable(tag: Tag) extends Table[BattlePlayer](tag, "BATTLE_PLAYER") {
  def id = column[Long]("ID", O.PrimaryKey, O.AutoInc)

  def playerTag = column[String]("PLAYER_TAG")

  def mmr = column[Long]("MMR")

  def battleUserInfoId = column[String]("AUTH_USER_INFO_ID")

  override def * = (id, playerTag, mmr, battleUserInfoId) <> (BattlePlayer.tupled, BattlePlayer.unapply)
}

object Tables {
  val battlePlayers = TableQuery[BattlePlayerTable]

}
