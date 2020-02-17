package game.server

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import game.matchmaking.MatchMaking
import game.matchmaking.MatchMaking.{InitMatchMaking, JoinMatchMaking}
import game.player.OnlinePlayer
import game.server.GameRoom.StartGame
import game.server.GameServer._
import models.BattlePlayer

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class GameServer extends Actor {
  private val settings = mutable.Map[String, String]()
  private val games = mutable.Map[String, ActorRef]()
  private val mmActor = context.actorOf(MatchMaking.props)

  mmActor ! InitMatchMaking(100, 500, 4, FiniteDuration(30, TimeUnit.SECONDS).toSeconds)

  override def receive: Receive = {

    case AddPlayer(out, player) =>
      val a = context.child(s"PlayerProxy-${player.id}")
        .getOrElse(context.actorOf(GameServer.proxyProps(out), s"PlayerProxy-${player.id}"))
      //      a ! out
      println(s"Added Player created PlayerProxy-${player.id}")
      out ! OnlinePlayer.JoinServer(self)

    case RemovePlayer(player) =>
      context.child(s"PlayerProxy-${player.id}").foreach(child => child ! PoisonPill)
      mmActor ! MatchMaking.RemovePlayer(player)

    case UpdateSettings(settings) => settings.foreach { case (key, value) => this.settings.put(key, value) }

    case JoinServerMatchMaking(player) =>
      context.child(s"PlayerProxy-${player.id}").foreach(child => mmActor ! JoinMatchMaking(player, child))
      sender() ! 1

    case MatchMaking.MatchFound(player1, player2, bracket) =>
      val gameId = UUID.randomUUID().toString
      val p1 = GameRoomEntry(player1.player, player1.playerActor)
      val p2 = GameRoomEntry(player2.player, player2.playerActor)
      val gameActor = context.actorOf(GameRoom.props(gameId, p1, p2), s"Game-${gameId}")
      games.put(gameId, gameActor)
      gameActor ! GameRoom.StartGame()
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

  case class RemovePlayer(battlePlayer: BattlePlayer) extends FromPlayers

}

class PlayerProxy(var proxyPlayer: ActorRef) extends Actor {
  override def receive: Receive = {
    //    case _: ToPlayers => proxyPlayer ! _
    //    case _: FromPlayers => context.parent ! _
    //    case actorRef: ActorRef => proxyPlayer = actorRef
    case e => proxyPlayer ! e
  }
}
