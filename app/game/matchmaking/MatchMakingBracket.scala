package game.matchmaking

import java.util.UUID
import java.util.concurrent.TimeUnit

import akka.actor.{Actor, Props, Timers}
import game.matchmaking.MatchMaking.{ChangeBracket, MatchFound}
import game.matchmaking.MatchMakingBracket.{AddEntry, AddedEntry, ClearBracket, MatchPlayers}

import scala.collection.mutable
import scala.concurrent.duration.FiniteDuration

class MatchMakingBracket(step: Int, maxBracketTime: FiniteDuration) extends Actor with Timers {

  private val queue = mutable.Queue[MatchMakingEntry]()

  override def preStart(): Unit = {
    super.preStart()
    restartTimer()
  }

  def restartTimer(): Unit = {
    timers.cancelAll()
    timers.startPeriodicTimer(UUID.randomUUID(), MatchPlayers(), FiniteDuration(18, TimeUnit.SECONDS))
    timers.startPeriodicTimer(UUID.randomUUID(), ClearBracket(), FiniteDuration(20, TimeUnit.SECONDS))
  }

  override def receive: Receive = {

    case AddEntry(entry) if !queue.exists(_.playerId == entry.playerId) =>
      queue.enqueue(entry)
      sender() ! AddedEntry(entry)
      self ! MatchPlayers()
      restartTimer()
      println(s"Added entry $entry to bracket $step")

    case MatchPlayers() =>
      val matchResult = doMatch(queue, step, None, List())
      for {
        matched <- matchResult._1
      } yield {
        context.parent ! MatchFound(matched._1, matched._2)
      }
      queue.enqueue(matchResult._2: _*)

    case ClearBracket() =>
//      println(s"cleaning up bracket $step for $queue")
      queue.dequeueAll(entry => {
        val passed = System.currentTimeMillis() - entry.timestamp
        println(s"${FiniteDuration(TimeUnit.MILLISECONDS.toSeconds(passed), TimeUnit.SECONDS)} greater than $maxBracketTime")
        FiniteDuration(TimeUnit.MILLISECONDS.toSeconds(passed), TimeUnit.SECONDS) > maxBracketTime
      }).foreach(entry => {
        context.parent ! ChangeBracket(entry, step)
      })
      if (queue.isEmpty) timers.cancelAll()

  }

  def doMatch(q: mutable.Queue[MatchMakingEntry], _step: Int, found: Option[(MatchMakingEntry, MatchMakingEntry)], notFound: List[MatchMakingEntry]): (Option[(MatchMakingEntry, MatchMakingEntry)], List[MatchMakingEntry]) = {
    if (found.isDefined || q.isEmpty) return (found -> notFound)
    val base = q.dequeue()
    val _found = findMatch(base, q, _step)
    doMatch(q, _step, _found, if (_found.isEmpty) base :: notFound else notFound)
  }

  private def findMatch(base: MatchMakingEntry, q: mutable.Queue[MatchMakingEntry], _step: Int): Option[(MatchMakingEntry, MatchMakingEntry)] =
    q.dequeueFirst(entry => entry.playerId != base.playerId && subs(entry.playerMMR, base.playerMMR) < _step)
      .map(entry => entry -> base)

  def subs(a: Long, b: Long): Long = Math.max(a, b) - Math.min(a, b)
}

object MatchMakingBracket {

  def props(step: Int, maxTime: FiniteDuration) = Props(classOf[MatchMakingBracket], step, maxTime)

  case class AddEntry(entry: MatchMakingEntry)

  case class AddedEntry(entry: MatchMakingEntry)

  case class MatchPlayers()

  case class ClearBracket()

}
