package services

import game.player.OnlinePlayer.PlayerStatus
import models.BattlePlayer

object GameLogic {

  def createGameEntry(player: BattlePlayer, size: Int): GameEntry = {
    GameEntry(player, GameBoard(List.empty), List(), GameEntryStatus.PREPARING)
  }

  def placeShip(entry: GameEntry, _ship: GamePiece): GameEntry = {
    val newBoardBoxes = _ship.boxes.map(b => {
      b.copy(shipId = Some(_ship.id))
    })
    val newBoard = entry.board.copy(boxes = newBoardBoxes ++ entry.board.boxes)
    entry.copy(board = newBoard, ships = _ship :: entry.ships)
  }

  def applyBoxHit(entry: GameEntry, box: GameBox): GameEntry = {
    val _boxList = for {
      boardBox <- entry.board.boxes
    } yield {
      if (boardBox.x == box.x && boardBox.y == box.y && !boardBox.hit)
        boardBox.copy(hit = true)
      else
        boardBox
    }
    val newBoard = entry.board.copy(boxes = _boxList)
    entry.copy(board = newBoard)
    syncShipStatus(entry, _boxList.filter(_.hit == true))
  }

  private def syncShipStatus(entry: GameEntry, hitBoxList: List[GameBox]): GameEntry = {
    val newShips = entry.ships.map(ship => {
      val newBoxes = for {
        box <- ship.boxes
        _box <- hitBoxList
        if box.x == _box.x && box.y == _box.y && !box.hit
      } yield box.copy(hit = true)
      ship.copy(boxes = newBoxes)
    })
    entry.copy(ships = newShips)
  }
}

case class GameEntry(player: BattlePlayer,
                     board: GameBoard,
                     ships: List[GamePiece],
                     status: GameEntryStatus.Status,
                     ready: Boolean = false
                    )

case class GameState(p1: GameEntry,
                     p2: GameEntry,
                     status: GameStatus.Status = GameStatus.PREPARING) {
  def getEntry(player: BattlePlayer): GameEntry = if (p1.player == player) p1 else p2

  def getEnemyEntry(player: BattlePlayer): GameEntry = if (p1.player != player) p1 else p2

  def setEntry(player: BattlePlayer, newEntry: GameEntry): GameState =
    if (p1.player == player)
      this.copy(p1 = newEntry)
    else
      this.copy(p2 = newEntry)

  def setEnemyEntry(player: BattlePlayer, newEntry: GameEntry): GameState =
    if (p1.player != player)
      this.copy(p1 = newEntry)
    else
      this.copy(p2 = newEntry)
}

case class GameBoard(boxes: List[GameBox])

case class GamePiece(id: String, boxes: List[GameBox], alive: Boolean)

case class GameBox(x: Int, y: Int, hit: Boolean, shipId: Option[String] = None)

case class GameSummary(entry: GameEntry,
                       shipsAlive: List[(Int, Int)],
                       shipsKilled: List[(Int, Int)])

object GameStatus {

  sealed abstract class Status(val status: String)

  case object PREPARING extends Status("PREPARING")

  case object PLAYING extends Status("PLAYING")

  case object COMPLETED extends Status("COMPLETED")

}

object GameEntryStatus {

  sealed abstract class Status(val status: String)

  case object PREPARING extends Status("PREPARING")

  case object WAITING_FOR_OTHER_PLAYER extends Status("WAITING_FOR_OTHER_PLAYER")

  case object READY extends Status("READY")

}
