package game.matchmaking

import akka.actor.ActorRef
import models.BattlePlayer

case class MatchMakingEntry(player: BattlePlayer, playerMMR: Long, playerActor: ActorRef, timestamp: Long)
