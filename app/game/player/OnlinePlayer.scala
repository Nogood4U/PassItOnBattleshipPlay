package game.player

import akka.actor.{Actor, ActorRef, Props}
import game.player.OnlinePlayer.{Disconnected, JoinServer, StatusChange, UpdateOutput}
import game.server.{GameRoom, GameServer}
import models.BattlePlayer
import play.api.libs.json.{JsString, JsValue, Json}

import scala.util.Failure

class OnlinePlayer(player: BattlePlayer) extends Actor {
  private var status: OnlinePlayerStatus = OFFLINE
  private var server: Option[ActorRef] = None
  private var out: Option[ActorRef] = None

  override def receive: Receive = {
    case StatusChange(status) =>
      this.status = status
      println(s"Status set as $status")

    case UpdateOutput(newOut) =>
      this.out = Some(newOut)
      out.map(_ ! Json.obj("providers" -> "test"))

    case JoinServer(server) => this.server = Some(server)

    case GameRoom.GameRequest(gameRoom) => println(gameRoom); out.map(_ ! Json.obj("request" -> gameRoom))

    case msg@OnlinePlayer.PlayerStatus(player, _) => sender() ! msg.copy(status = Option(status))

    case Disconnected() => println("Disconnecting"); this.server.foreach(_ ! GameServer.RemovePlayer(player))

    case j: JsValue if (j \ "disconnected").toOption.isDefined => self ! Disconnected()

    case StatusChange(status) => println(status);this.status = status

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

}

sealed abstract class OnlinePlayerStatus(value: String) {
  override def toString: String = value
}

case object ONLINE extends OnlinePlayerStatus("ONLINE")

case object INVISIBLE extends OnlinePlayerStatus("INVISIBLE")

case object OFFLINE extends OnlinePlayerStatus("OFFLINE")

case object LOOKING_FOR_GAME extends OnlinePlayerStatus("LOOKING_FOR_GAME")

