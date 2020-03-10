package services

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
    def addShipId(boardBox: GameBox) = {
      boardBox.copy(hit = true, shipId = entry.ships.flatMap(_.boxes).find(f => f.x == box.x && f.y == box.y).flatMap(_.shipId))
    }

    val _boxList = if (entry.board.boxes.exists(boardBox => boardBox.x == box.x && boardBox.y == box.y)) {
      for {
        boardBox <- entry.board.boxes
        if boardBox.x == box.x && boardBox.y == box.y && !boardBox.hit
      } yield {
        addShipId(boardBox)
      }
    } else {
      addShipId(box) :: entry.board.boxes
    }

    val newBoard = entry.board.copy(boxes = _boxList)

    syncShipStatus(entry.copy(board = newBoard), _boxList.filter(_.hit == true))
  }

  private def syncShipStatus(entry: GameEntry, hitBoxList: List[GameBox]): GameEntry = {
    val newShips = entry.ships.map(ship => {
      val newBoxes = ship.boxes.map(box => {
        hitBoxList.find(_box => box.x == _box.x && box.y == _box.y && !box.hit)
          .map(_.copy(hit = true)).getOrElse(box)
      })
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
                     turnPlayer: Long,
                     status: GameStatus.Status = GameStatus.PREPARING,
                     turn: Int = 0,
                     winner: Option[BattlePlayer] = None) {

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

case class GamePiece(id: String, boxes: List[GameBox], alive: Boolean, size: Int)

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
