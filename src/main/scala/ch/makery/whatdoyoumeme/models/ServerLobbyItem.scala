package ch.makery.whatdoyoumeme.models

import akka.actor.typed.ActorRef
import ch.makery.whatdoyoumeme.Actor.LobbyServer

case class ServerLobbyItem(LobbyIDS: String, lobbyServerRef: ActorRef[LobbyServer.Command], nameS: String) {

  var numberOfPlayer: Int = 0
  var inGameStatus = false

  def changePlayerCount(numb: Int): Unit = {
    numberOfPlayer += numb
  }
}
