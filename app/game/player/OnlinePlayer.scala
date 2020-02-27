package game.player

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import akka.pattern._
import akka.util.Timeout
import game.player.OnlinePlayer._
import game.server.GameRoom.{GameRoomMessage, GameRoomUpdate}
import game.server.{GameRoom, GameServer}
import models.BattlePlayer
import play.api.libs.json.{JsValue, Json}

import scala.concurrent.duration.FiniteDuration
import scala.util.Failure

class OnlinePlayer(player: BattlePlayer) extends Actor {
  private var status: OnlinePlayerStatus = ONLINE
  private var server: Option[ActorRef] = None
  private var out: Option[ActorRef] = None
  private var gameRoomId: Option[String] = None

  implicit val timeout: Timeout = Timeout(FiniteDuration(1, TimeUnit.SECONDS))

  import context._

  var gameRoomActor: Option[ActorRef] = None

  import services.BoardDataParser._

  override def receive: Receive = {
    case StatusChange(status) =>
      this.status = status
      println(s"Status set as $status")

    case UpdateOutput(newOut) =>
      this.out = Some(newOut)
      out.foreach(_ ! Json.obj("providers" -> "test"))
      if (status == GAME_REQUEST) {
        out.map(_ ! Json.obj("request" -> gameRoomId))
      }

    case JoinServer(server) => this.server = Some(server)

    case GameRoom.GameRequest(gameRoom) => println(gameRoom);
      gameRoomId = Some(gameRoom)
      out.map(_ ! Json.obj("request" -> gameRoomId))


    case msg@OnlinePlayer.PlayerStatus(player, _) => sender() ! msg.copy(status = Option(status))

    case Disconnected() => println("Disconnecting"); this.server.foreach(_ ! GameServer.RemovePlayer(player))

    case j: JsValue if (j \ "disconnected").toOption.isDefined => self ! Disconnected()

    case StatusChange(status) => println(status); this.status = status

    case GameRoom.GameCancelled(gameId) =>
      out.foreach(_ ! Json.obj("canceled" -> gameId))
      status = ONLINE

    case GameRoom.GameStarted(gameId, enemy) => out.map(_ ! Json.obj("started" -> gameId, "enemy" -> Json.toJson(enemy)))

    case JoinServerMatchMaking() =>
      val _sender = sender()
      if (status == ONLINE)
        server.foreach(s => (s ? GameServer.JoinServerMatchMaking(player)) map (_sender ! _))
      else sender() ! 1

    case CancelServerMatchMaking() =>
      val _sender = sender()
      if (status == LOOKING_FOR_GAME) {
        server.foreach(s => (s ? GameServer.CancelServerMatchMaking(player)) map (_sender ! _))
        status = ONLINE
      } else sender() ! 1

    case AcceptGame(gameId) =>
      val _sender = sender()
      server.foreach(s => (s ? GameServer.AcceptGame(player, gameId)).mapTo[ActorRef] foreach (rt => {
        status = WAIT_FOR_PLAYERS
        gameRoomActor = Some(rt)
        _sender ! 1
      }))

    case RejectGame(gameId) =>
      val _sender = sender()
      server.foreach(s => s ? GameServer.RejectGame(player, gameId) foreach (rt => {
        gameRoomId = None
        _sender ! rt
      }))

    case msg: GameRoomMessage =>
      val _sender = sender()
      gameRoomActor.foreach(ar => ar ? msg foreach (rt => _sender ! rt))

    case msg: GameRoomUpdate =>
      val filteredEntries = List(msg.gameState.p1, msg.gameState.p2)
        .map(entry => {
          if (entry.player != this.player) {
            val newShipsList = entry.ships.map(ship => ship.copy(boxes = List.empty))
            entry.copy(ships = newShipsList)
          } else entry
        })
      val toSend = Json.toJson(msg.gameState.copy(p1 = filteredEntries.head, p2 = filteredEntries.last))
      out.foreach(ar => ar ! toSend)

    case e => e match {
      case Failure(f) => f.printStackTrace()
      case _ => println(e)
    }


  }

}

object OnlinePlayer {
  def props(player: BattlePlayer): Props = Props(classOf[OnlinePlayer], player)

  case class StatusChange(status: OnlinePlayerStatus)

  case class UpdateOutput(out: ActorRef)

  case class JoinServer(server: ActorRef)

  case class PlayerStatus(player: BattlePlayer, status: Option[OnlinePlayerStatus])

  case class JoinMatch()

  case class Disconnected()

  case class JoinServerMatchMaking()

  case class CancelServerMatchMaking()

  case class AcceptGame(gameId: String)

  case class RejectGame(gameId: String)

}

sealed abstract class OnlinePlayerStatus(value: String) {
  override def toString: String = value
}

case object ONLINE extends OnlinePlayerStatus("ONLINE")

case object INVISIBLE extends OnlinePlayerStatus("INVISIBLE")

case object OFFLINE extends OnlinePlayerStatus("OFFLINE")

case object LOOKING_FOR_GAME extends OnlinePlayerStatus("LOOKING_FOR_GAME")

case object GAME_REQUEST extends OnlinePlayerStatus("GAME_REQUEST")

case object WAIT_FOR_PLAYERS extends OnlinePlayerStatus("WAIT_FOR_PLAYERS")

