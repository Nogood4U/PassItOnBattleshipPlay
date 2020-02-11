package game.model

import akka.actor.{Actor, ActorRef, Props}
import game.model.GameServer.{AddPlayer, AddedPlayer, FromPlayers, ToPlayers, UpdateSettings}
import models.BattlePlayer

import scala.collection.mutable

class GameServer extends Actor {
  private val settings = mutable.Map[String, String]()

  override def receive: Receive = {
    case AddPlayer(out, player) =>
      context.actorOf(GameServer.proxyProps(out), s"PlayerProxy-${player.id}")
      out ! AddedPlayer(settings.toMap)

    case UpdateSettings(settings) => settings.foreach { case (key, value) => this.settings.put(key, value) }
  }
}

object GameServer {

  def props: Props = Props[GameServer]

  def proxyProps(proxyPlayer: ActorRef) = Props(classOf[PlayerProxy], proxyPlayer)

  sealed trait FromPlayers

  sealed trait ToPlayers

  case class AddPlayer(player: ActorRef, battlePlayer: BattlePlayer) extends FromPlayers

  case class AddedPlayer(serverSettings: Map[String, String]) extends ToPlayers

  case class UpdateSettings(serverSettings: Map[String, String]) extends FromPlayers

}

class PlayerProxy(proxyPlayer: ActorRef) extends Actor {
  override def receive: Receive = {
    case _: ToPlayers => proxyPlayer ! _
    case _: FromPlayers => context.parent ! _
  }
}