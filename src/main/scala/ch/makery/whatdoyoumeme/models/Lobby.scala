package ch.makery.whatdoyoumeme.models

import scala.collection.mutable.ListBuffer

case class Lobby(LobbyIDS: String, nameS: String) extends Serializable {

  private var lobbyID: String = LobbyIDS
  private var players: ListBuffer[GameClientModel] = ListBuffer.empty[GameClientModel]
  private var numberOfPlayer: Int = 1
  private val lobbyMasterName: String = nameS

  def getLobbyID: String = {
    lobbyID
  }

  def getLobbyMasterName: String = {
    lobbyMasterName
  }

  def getNumberOfPlayer: Int = {
    numberOfPlayer
  }

  def setNumberOfPlayer(number: Int): Unit = {
    numberOfPlayer += number
  }

  def addPlayer(player: GameClientModel): Unit = {
    players += player
  }

  def removePlayer(player: GameClientModel): Unit = {
    players -= player
  }

  def getPlayers: ListBuffer[GameClientModel] = {
    players
  }

  def validateCode(code: String): Boolean = {
    code == lobbyID
  }

  def createCode(code: String): Unit = {
    lobbyID = code
  }
}
