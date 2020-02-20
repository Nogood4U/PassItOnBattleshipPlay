package game.server

import akka.actor.{Actor, ActorRef, Props}
import game.server.GameRoom.{GameCancelled, GameRequest, GameStarted, PlayerAccepted, PlayerRejected, StartGame}
import models.BattlePlayer

import scala.collection.mutable

case class GameRoomEntry(battlePlayer: BattlePlayer, playerActor: ActorRef)

class GameRoom(gameId: String, player1: GameRoomEntry, player2: GameRoomEntry) extends Actor {
  var playersAccepted: mutable.Set[BattlePlayer] = mutable.Set[BattlePlayer]();

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
        context.become(gameStarted)
        player1.playerActor ! GameStarted(gameId)
        player2.playerActor ! GameStarted(gameId)
      }

    case PlayerRejected(player) =>
      //cancel game , remove Players form MM
      player1.playerActor ! GameCancelled(gameId)
      player2.playerActor ! GameCancelled(gameId)
      context.parent ! GameServer.CancelGame(gameId)
  }


  def gameStarted: Receive = {
    case _ =>
  }
}

object GameRoom {

  def props(gameId: String, player1: GameRoomEntry, player2: GameRoomEntry) = Props(classOf[GameRoom], gameId, player1, player2)

  case class PlayerAccepted(battlePlayer: BattlePlayer)

  case class PlayerRejected(battlePlayer: BattlePlayer)

  case class GameRequest(gameId: String)

  case class StartGame()

  case class GameCancelled(gameId: String)

  case class GameStarted(gameId: String)

}
