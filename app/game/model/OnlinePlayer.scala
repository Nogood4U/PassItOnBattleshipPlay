package game.model

import akka.actor.{Actor, ActorRef, Props}
import game.model.OnlinePlayer.{JoinServer, StatusChange, UpdateOutput}
import models.BattlePlayer
import play.api.libs.json.{JsValue, Json}

class OnlinePlayer(player: BattlePlayer) extends Actor {
  private var status: OnlinePlayerStatus.Value = OnlinePlayerStatus.OFFLINE
  private var server: Option[ActorRef] = None
  private var out: Option[ActorRef] = None

  override def receive: Receive = {
    case StatusChange(status) => {
      this.status = status
      println(s"Status set as $status")
    }
    case UpdateOutput(newOut) =>
      this.out = Some(newOut)
      out.map(_ ! Json.obj("providers" -> "test"))
    case JoinServer(server) => this.server = Some(server)
    case e: Int => println(e)
    case j: JsValue => println(j)
  }

}

object OnlinePlayer {
  def props(player: BattlePlayer): Props = Props(classOf[OnlinePlayer], player)

  case class StatusChange(status: OnlinePlayerStatus.Value)

  case class UpdateOutput(out: ActorRef)

  case class JoinServer(server: ActorRef)

}

object OnlinePlayerStatus extends Enumeration {
  val ONLINE, INVISIBLE, OFFLINE, LOOKING_FOR_GAME = Value
}
