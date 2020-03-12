package game.server

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import game.server.GameRoom._
import models.BattlePlayer
import play.api.libs.json.JsValue
import services.{BoardDataParser, GameBox, GameEntryStatus, GameLogic, GamePiece, GameState, GameStatus}

import scala.collection.mutable

case class GameRoomEntry(battlePlayer: BattlePlayer, playerActor: ActorRef)

class GameRoom(gameId: String, player1: GameRoomEntry, player2: GameRoomEntry) extends Actor {
  var playersAccepted: mutable.Set[BattlePlayer] = mutable.Set[BattlePlayer]();
  var gameState: Option[GameState] = None
  var spectators: mutable.Map[String, ActorRef] = mutable.Map.empty

  override def postStop(): Unit = {
    player1.playerActor ! GameCancelled(gameId)
    player2.playerActor ! GameCancelled(gameId)
  }

  override def receive: Receive = {
    case StartGame() =>
      player1.playerActor ! GameRequest(gameId)
      player2.playerActor ! GameRequest(gameId)
      context.become(gamePreparing.orElse(commonMessages))

  }

  def gamePreparing: Receive = {

    case PlayerAccepted(player) =>
      playersAccepted.add(player)
      if (playersAccepted.size == 2) {
        player1.playerActor ! GameStarted(gameId, player2.battlePlayer)
        player2.playerActor ! GameStarted(gameId, player1.battlePlayer)
        val entry1 = GameLogic.createGameEntry(player1.battlePlayer, 15)
        val entry2 = GameLogic.createGameEntry(player2.battlePlayer, 15)
        gameState = Some(GameState(gameId, entry1, entry2, entry1.player.id))
        context.become(gameStarted.orElse(commonMessages))
      }

    case PlayerRejected(player) =>
      //cancel game , remove Players form MM
      player1.playerActor ! GameCancelled(gameId)
      player2.playerActor ! GameCancelled(gameId)
      context.parent ! GameServer.CancelGame(gameId)
  }


  def gameStarted: Receive = {
    case GameRoom.BoardReady(player) =>
      val _sender = sender()
      for {
        state <- gameState
      } yield {
        val newEntry = state.getEntry(player).copy(ready = true, status = GameEntryStatus.WAITING_FOR_OTHER_PLAYER)
        val newState = state.setEntry(player, newEntry)
        gameState = Some(newState)
        if (newState.p1.ships.size >= 9 && newState.p1.ready && newState.p2.ships.size >= 9 && newState.p2.ready) {
          context.become(battleStarted.orElse(commonMessages))
          self ! StartBattle()
        } else {
          sendStateupdate(gameState)
        }
      }
      _sender ! 1

    case GameRoom.AddPiece(player, pieceData) =>
      val newState = for {
        piece <- BoardDataParser.parsePiece(pieceData)
        state <- gameState
        if state.getEntry(player).ships.size < 9
      } yield {
        val pieceId = UUID.randomUUID().toString
        val entry = state.getEntry(player)
        val boxes = for {
          position <- piece.positions
        } yield GameBox(position.x, position.y, hit = false, Some(pieceId))
        val newEntry = GameLogic.placeShip(entry, GamePiece(pieceId, boxes, alive = true, boxes.size))
        state.setEntry(player, newEntry)
      }
      newState match {
        case st@Some(state) =>
          gameState = st
          sender() ! 1
          sendStateupdate(st)

        case None => sender() ! 0
      }

  }

  def battleStarted: Receive = {
    case StartBattle() =>
      println("STARTING BATTLE!!")
      gameState = for {
        state <- gameState
      } yield {
        state.copy(
          status = GameStatus.PLAYING,
          p1 = state.p1.copy(status = GameEntryStatus.READY),
          p2 = state.p2.copy(status = GameEntryStatus.READY)
        )
      }
      sendStateupdate(gameState)

    case HitBox(player, x, y) =>
      gameState = for {
        state <- gameState
      } yield {
        val entry = state.getEnemyEntry(player)
        val newEnemyEntry = GameLogic.applyBoxHit(entry, GameBox(x, y, hit = true))
        state.setEnemyEntry(player, newEnemyEntry)
          .copy(turnPlayer = newEnemyEntry.player.id, turn = state.turn + 1)
      }
      sender() ! 1
      sendStateupdate(gameState)
      self ! GameRoom.CheckGameStatus()

    case GameRoom.CheckGameStatus() =>
      for {
        state <- gameState
        if state.turn > 3
      } yield {
        var newState = processRunningGameStatus(state)
        newState = if (state.turn == 5)
          state.copy(status = GameStatus.COMPLETED, winner = Some(state.p1.player))
        else newState
        gameState = Some(newState)
        if (newState.status == GameStatus.COMPLETED) {
          context.become(gameFinished.orElse(commonMessages))
          self ! GameRoom.GameFinished()
        }
      }

  }

  def gameFinished: Receive = {

    case GameRoom.GameFinished() =>
      // publish update to players
      self ! GameRoom.RequestStateUpdate()
      // TODO: publish data to external services , scoreboard , player mmr update etc
      // trigger game cancel flow
      self ! GameRoom.CancelGame(None)

  }

  def commonMessages: Receive = {
    case GameRoom.RequestStateUpdate() =>
      sendStateupdate(gameState)

    case GameRoom.CancelGame(player) =>
      context.parent ! GameServer.CancelGame(gameId)

    case GameRoom.AddSpectator(outPut, id) => spectators.put(id, outPut)

    case GameRoom.RemoveSpectator(id) => spectators.remove(id)
  }

  private def processRunningGameStatus(state: GameState) =
    if (state.p1.ships.flatMap(_.boxes).forall(shipBox => shipBox.hit)) {
      state.copy(status = GameStatus.COMPLETED, winner = Some(state.p1.player))
    } else if (state.p2.ships.flatMap(_.boxes).forall(shipBox => shipBox.hit)) {
      state.copy(status = GameStatus.COMPLETED, winner = Some(state.p2.player))
    } else state

  private def sendStateupdate(gameState: Option[GameState]) {
    gameState.foreach(state => {
      player1.playerActor ! GameRoom.GameStateUpdate(state)
      player2.playerActor ! GameRoom.GameStateUpdate(state)
      spectators.values.foreach(_ ! GameRoom.GameStateUpdate(state))
    })
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

  case class CancelGame(battlePlayer: Option[BattlePlayer]) extends GameRoomMessage

  case class GameStarted(gameId: String, enemy: BattlePlayer) extends GameRoomMessage

  case class BoardReady(player: BattlePlayer) extends GameRoomMessage

  case class StartBattle() extends GameRoomMessage

  case class AddPiece(player: BattlePlayer, pieceData: JsValue) extends GameRoomMessage

  case class HitBox(player: BattlePlayer, x: Int, y: Int) extends GameRoomMessage

  case class CheckGameStatus() extends GameRoomMessage

  case class GameFinished() extends GameRoomMessage

  case class GameStateUpdate(override val gameState: GameState) extends GameRoomUpdate(gameState)

  case class RequestStateUpdate() extends GameRoomMessage

  case class AddSpectator(outPut: ActorRef, id: String)

  case class RemoveSpectator(id: String)

}
