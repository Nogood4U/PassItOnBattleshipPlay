package game.matchmaking

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import game.matchmaking.MatchMaking._
import game.matchmaking.MatchMakingBracket.AddEntry
import game.player.{LOOKING_FOR_GAME, OnlinePlayer}
import models.BattlePlayer

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class MatchMaking extends Actor {

  val bracketActors: mutable.Map[Long, ActorRef] = mutable.Map[Long, ActorRef]()
  val bracketPlayers: mutable.Map[Long, Int] = mutable.Map[Long, Int]()
  var config: Option[MMConfig] = None


  override def receive: Receive = {
    case InitMatchMaking(baseStep, increment, limit, maxTimeInBracket) =>
      config = Some(MMConfig(baseStep, increment, limit))
      Range(baseStep, Int.MaxValue, increment)
        .take(limit)
        .foreach(step => {
          val actor = context.actorOf(MatchMakingBracket.props(step, FiniteDuration(maxTimeInBracket, TimeUnit.SECONDS)))
          bracketActors.put(step, actor)
        })
      val lastBracket = context.actorOf(MatchMakingBracket.props(9999, FiniteDuration(maxTimeInBracket, TimeUnit.SECONDS)))
      bracketActors.put(9999, lastBracket)
      println("MatchMaking Process Initialized")
      println(s"Config $config")
      context.become(activeMatchmaking)
  }

  def activeMatchmaking: Receive = {
    case JoinMatchMaking(player, playerActor) if !bracketPlayers.contains(player.id) =>
      playerActor ! OnlinePlayer.StatusChange(LOOKING_FOR_GAME)
      for {
        _config <- config
        _actor <- bracketActors.get(_config.baseStep)
      } yield {
        _actor ! AddEntry(MatchMakingEntry(player, player.mmr, playerActor, System.currentTimeMillis()))
        bracketPlayers.put(player.id, _config.baseStep)
        println(s"${player.playerTag} joined Match Making")
      }


    case ChangeBracket(entry, prevBracket) =>
      println(s"Changing Bracket from ${prevBracket}")
      for {
        _config <- config
        _actor <- bracketActors.get(_config.increment + prevBracket).orElse(bracketActors.get(9999))
      } yield {
        _actor ! AddEntry(entry.copy(timestamp = System.currentTimeMillis()))
        bracketPlayers.put(entry.player.id, _config.increment + prevBracket)
      }

    case msg@RemovePlayer(_) =>
      bracketActors.foreach(_._2 ! msg)
      bracketPlayers.remove(msg.player.id)

    case msg@MatchFound(player1, player2, bracket) =>
      println(s"Found match in bracket $bracket =)> Player #${player1.player} will face Player #${player2.player}")
      bracketPlayers.remove(player1.player.id)
      bracketPlayers.remove(player2.player.id)
      bracketActors.remove(player1.player.id)
      bracketActors.remove(player2.player.id)
      context.parent ! msg

  }

}

object MatchMaking {

  def props: Props = Props[MatchMaking]

  case class InitMatchMaking(baseStep: Int, increment: Int, limit: Int, maxTimeInBracket: Long)

  case class JoinMatchMaking(player: BattlePlayer, playerActor: ActorRef)

  case class MatchFound(player1: MatchMakingEntry, player2: MatchMakingEntry, bracket: Int)

  case class MMConfig(baseStep: Int, increment: Int, limit: Int)

  case class ChangeBracket(entry: MatchMakingEntry, prevBracket: Int)

  case class RemovePlayer(player: BattlePlayer)

  case class GetPlayerInBracket(player: BattlePlayer)

}








