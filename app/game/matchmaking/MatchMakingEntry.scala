package game.matchmaking

import akka.actor.ActorRef

case class MatchMakingEntry(playerId: Long, playerMMR: Long, player: ActorRef, timestamp: Long)
