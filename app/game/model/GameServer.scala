package game.model

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import game.matchmaking.MatchMaking
import game.matchmaking.MatchMaking.{InitMatchMaking, JoinMatchMaking}
import game.model.GameServer._
import models.BattlePlayer

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class GameServer extends Actor {
  private val settings = mutable.Map[String, String]()

  val mmActor = context.actorOf(MatchMaking.props)
  mmActor ! InitMatchMaking(100, 300, 10, FiniteDuration(30, TimeUnit.SECONDS).toSeconds)

  override def receive: Receive = {
    case AddPlayer(out, player) =>
      context.actorOf(GameServer.proxyProps(out), s"PlayerProxy-${player.id}")
      println(s"Added Player created PlayerProxy-${player.id}")
      out ! AddedPlayer(settings.toMap)

    case UpdateSettings(settings) => settings.foreach { case (key, value) => this.settings.put(key, value) }

    case JoinServerMatchMaking(player) =>
      context.child(s"PlayerProxy-${player.id}").foreach(child => mmActor ! JoinMatchMaking(player, child))
      sender() ! 1
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

  case class JoinServerMatchMaking(player: BattlePlayer)

}

class PlayerProxy(proxyPlayer: ActorRef) extends Actor {
  override def receive: Receive = {
    case _: ToPlayers => proxyPlayer ! _
    case _: FromPlayers => context.parent ! _
    case _ => proxyPlayer ! _
  }
}
