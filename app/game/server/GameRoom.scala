package game.server

import akka.actor.{Actor, ActorRef, Props}
import game.server.GameRoom.{GameRequest, PlayerAccepted, StartGame}
import models.BattlePlayer

case class GameRoomEntry(battlePlayer: BattlePlayer, playerActor: ActorRef)

class GameRoom(gameId: String, player1: GameRoomEntry, player2: GameRoomEntry) extends Actor {


  override def receive: Receive = {
    case StartGame() =>
      player1.playerActor ! GameRequest(gameId)
      player2.playerActor ! GameRequest(gameId)
      context.become(gameStarted)

  }

  def gameStarted: Receive = {

    case PlayerAccepted(newPlayer) =>

  }
}

object GameRoom {

  def props(gameId: String, player1: GameRoomEntry, player2: GameRoomEntry) = Props(classOf[GameRoom], gameId, player1, player2)

  case class PlayerAccepted(battlePlayer: BattlePlayer)

  case class GameRequest(gameId: String)

  case class StartGame()

}
