package game.matchmaking

import java.util.concurrent.TimeUnit

import akka.actor.{Actor, ActorRef, Props}
import game.matchmaking.MatchMaking._
import game.matchmaking.MatchMakingBracket.AddEntry
import game.model.{OnlinePlayer, OnlinePlayerStatus}
import models.BattlePlayer

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class MatchMaking extends Actor {


  val bracketActors: mutable.Map[Int, ActorRef] = mutable.Map[Int, ActorRef]()
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
    case JoinMatchMaking(player, playerActor) =>
      playerActor ! OnlinePlayer.StatusChange(OnlinePlayerStatus.LOOKING_FOR_GAME)
      for {
        _config <- config
        _actor <- bracketActors.get(_config.baseStep)
      } yield _actor ! AddEntry(MatchMakingEntry(player.id, player.mmr, playerActor, System.currentTimeMillis()))

    case ChangeBracket(entry, prevBracket) =>
      println(s"Changing Bracket from ${prevBracket}")
      for {
        _config <- config
        _actor <- bracketActors.get(_config.increment + prevBracket)
      } yield _actor ! AddEntry(entry.copy(timestamp = System.currentTimeMillis()))

    case MatchFound(player1, player2) => ???
  }
}

object MatchMaking {

  def props: Props = Props[MatchMaking]

  case class InitMatchMaking(baseStep: Int, increment: Int, limit: Int, maxTimeInBracket: Long)

  case class JoinMatchMaking(player: BattlePlayer, playerActor: ActorRef)

  case class MatchFound(player1: MatchMakingEntry, player2: MatchMakingEntry)

  case class MMConfig(baseStep: Int, increment: Int, limit: Int)

  case class ChangeBracket(entry: MatchMakingEntry, prevBracket: Int)

}








