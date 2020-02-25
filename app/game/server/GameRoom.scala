package game.server

import akka.actor.{Actor, ActorRef, Props}
import game.server.GameRoom._
import models.BattlePlayer
import play.api.libs.json.JsValue
import services.{BoardDataParser, GameBox, GameLogic, GamePiece, GameState}

import scala.collection.mutable

case class GameRoomEntry(battlePlayer: BattlePlayer, playerActor: ActorRef)

class GameRoom(gameId: String, player1: GameRoomEntry, player2: GameRoomEntry) extends Actor {
  var playersAccepted: mutable.Set[BattlePlayer] = mutable.Set[BattlePlayer]();
  var gameState: Option[GameState] = None

  override def receive: Receive = {
    case StartGame() =>
      player1.playerActor ! GameRequest(gameId)
      player2.playerActor ! GameRequest(gameId)
      context.become(gamePreparing)

  }

  def gamePreparing: Receive = {

    case PlayerAccepted(player) =>
      playersAccepted.add(player)
      if (playersAccepted.size == 2) {
        player1.playerActor ! GameStarted(gameId, player2.battlePlayer)
        player2.playerActor ! GameStarted(gameId, player1.battlePlayer)
        val entry1 = GameLogic.createGameEntry(player1.battlePlayer, 15)
        val entry2 = GameLogic.createGameEntry(player2.battlePlayer, 15)
        gameState = Some(GameState(entry1, entry2))
        context.become(gameStarted)
      }

    case PlayerRejected(player) =>
      //cancel game , remove Players form MM
      player1.playerActor ! GameCancelled(gameId)
      player2.playerActor ! GameCancelled(gameId)
      context.parent ! GameServer.CancelGame(gameId)
  }


  def gameStarted: Receive = {
    case GameRoom.BoardAdded(player, boardData) =>
      val newState = (for {
        pieces <- BoardDataParser.parseBoard(boardData)
        state <- gameState
      } yield {
        val gamePieces = pieces.map(piece => {
          val boxes = for {
            position <- piece.positions
          } yield GameBox(position.x, position.y, hit = false)
          GamePiece(boxes, alive = true)
        })
        val playerEntry = state.getEntry(player)
        val newEntry = gamePieces.foldLeft(playerEntry)((entry, gamePiece) => GameLogic.placeShip(entry, gamePiece))
        Some(state.setEntry(player, newEntry))
      }).flatten
      newState match {
        case st@Some(state) =>
          gameState = st
          sendStateupdate(st)
          sender() ! 1
          if (state.p1.ships.size >= 9 && state.p2.ships.size >= 9)
            self ! StartBattle()

        case None => sender() ! 0
      }

    case GameRoom.AddPiece(player, pieceData) =>
      val newState = for {
        piece <- BoardDataParser.parsePiece(pieceData)
        state <- gameState
        if state.p1.ships.size < 9 && state.p2.ships.size < 9
      } yield {
        val entry = state.getEntry(player)
        val boxes = for {
          position <- piece.positions
        } yield GameBox(position.x, position.y, hit = false)
        val newEntry = GameLogic.placeShip(entry, GamePiece(boxes, alive = true))
        state.setEntry(player, newEntry)
      }
      newState match {
        case st@Some(state) =>
          gameState = st
          sendStateupdate(st)
          sender() ! 1
          if (state.p1.ships.size >= 9 && state.p2.ships.size >= 9)
            self ! StartBattle()

        case None => sender() ! 0
      }

    case StartBattle() => println("START TURNS AND WHATNOT!")
  }

  private def sendStateupdate(gameState: Option[GameState]) {
    gameState.foreach(state => player1.playerActor ! GameRoom.GameStateUpdate(state))
  }
}

object GameRoom {

  trait GameRoomMessage

  abstract class GameRoomUpdate(val gameState: GameState)

  def props(gameId: String, player1: GameRoomEntry, player2: GameRoomEntry) = Props(classOf[GameRoom], gameId, player1, player2)

  case class PlayerAccepted(battlePlayer: BattlePlayer) extends GameRoomMessage

  case class PlayerRejected(battlePlayer: BattlePlayer) extends GameRoomMessage

  case class GameRequest(gameId: String) extends GameRoomMessage

  case class StartGame() extends GameRoomMessage

  case class GameCancelled(gameId: String) extends GameRoomMessage

  case class GameStarted(gameId: String, enemy: BattlePlayer) extends GameRoomMessage

  case class BoardAdded(player: BattlePlayer, boardData: JsValue) extends GameRoomMessage

  case class StartBattle() extends GameRoomMessage

  case class AddPiece(player: BattlePlayer, pieceData: JsValue) extends GameRoomMessage

  case class GameStateUpdate(override val gameState: GameState) extends GameRoomUpdate(gameState)

}
