package game.model

import akka.actor.{Actor, ActorRef, Props}
import game.model.OnlinePlayer.{JoinServer, StatusChange, UpdateOutput}
import models.BattlePlayer

class OnlinePlayer(player: BattlePlayer) extends Actor {
  private var status: OnlinePlayerStatus.Value = OnlinePlayerStatus.OFFLINE
  private var server: Option[ActorRef] = None
  private var out: Option[ActorRef] = None

  override def receive: Receive = {
    case StatusChange(status) => this.status = status
    case UpdateOutput(newOut) => this.out = Some(newOut)
    case JoinServer(server) => this.server = Some(server)
  }

}

object OnlinePlayer {
  def props(player: BattlePlayer) = Props(classOf[OnlinePlayer], player)

  case class StatusChange(status: OnlinePlayerStatus.Value)

  case class UpdateOutput(out: ActorRef)

  case class JoinServer(server: ActorRef)

}

object OnlinePlayerStatus extends Enumeration {
  val ONLINE, INVISIBLE, OFFLINE, LOOKING_FOR_GAME = Value
}
