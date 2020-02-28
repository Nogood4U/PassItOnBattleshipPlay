package services

import models.BattlePlayer

object GameLogic {

  def createGameEntry(player: BattlePlayer, size: Int): GameEntry = {
    GameEntry(player, GameBoard(List.empty), List())
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
        box.copy(hit = true)
      else
        box
    }
    val newBoard = entry.board.copy(boxes = _boxList)
    entry.copy(board = newBoard)
    syncShipStatus(entry)
  }

  private def syncShipStatus(entry: GameEntry): GameEntry = {
    val _ships = (for {
      ship <- entry.ships
      box <- ship.boxes
      boardBox <- entry.board.boxes
      if boardBox.x == box.x && boardBox.y == box.y
    } yield ship -> box.copy(hit = boardBox.hit))
      .groupBy(_._1).mapValues(_.map(_._2))
      .foldLeft(List[GamePiece]())((acc, elm) => elm._1.copy(boxes = elm._2) :: acc)

    entry.copy(ships = _ships)
  }

}

case class GameEntry(player: BattlePlayer,
                     board: GameBoard,
                     ships: List[GamePiece],
                     ready: Boolean = false
                    )

case class GameState(p1: GameEntry,
                     p2: GameEntry,
                     status: GameStatus.Status = GameStatus.PREPARING) {
  def getEntry(player: BattlePlayer): GameEntry = if (p1.player == player) p1 else p2

  def setEntry(player: BattlePlayer, newEntry: GameEntry): GameState =
    if (p1.player == player)
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
