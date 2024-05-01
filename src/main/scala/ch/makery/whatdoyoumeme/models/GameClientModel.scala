package ch.makery.whatdoyoumeme.models

import akka.actor.typed.ActorRef
import ch.makery.whatdoyoumeme.Actor.GameClient
import com.hep88.protocol.JsonSerializable

import scala.collection.mutable.ListBuffer

case class GameClientModel(playerNameS: String) extends Serializable {

  private val playerName = playerNameS
  var gameClientRef: ActorRef[GameClient.Command] = null
  private var hand: ListBuffer[MemeCard] = ListBuffer.empty[MemeCard]
  private var isTurn: Boolean = false
  private var shuffled:Boolean = false
  private var dealtCard: MemeCard = null
  private var isReady: Boolean = false

  // Method to get player's name
  def getPlayerName(): String = {
    playerName
  }

  // Method to add a card to player's hand
  def addCardToHand(cardToAdd: MemeCard): Unit = {
    hand += cardToAdd
  }

  // Method to remove a card from player's hand
  def removeCardFromHand(cardToRemove: MemeCard): Unit = {
    hand -= cardToRemove
  }

  // Method to clear player's hand
  def clearHand(): Unit = {
    hand = ListBuffer.empty[MemeCard]
  }

  // Method to get player's hand
  def showHand(): ListBuffer[MemeCard] = {
    hand
  }

  // Method to set player's turn
  def setTurn(turn: Boolean): Unit = {
    isTurn = turn
  }

  // get player's turn boolean
  def getTurn(): Boolean = {
    isTurn
  }

  // Method to get player passed status
  def getHasShuffled(): Boolean = {
    shuffled
  }

  // Method to set player's passed status
  def setHasShuffled(shuffle: Boolean): Unit = {
    shuffled = shuffle
  }

  // Method to get dealt cards of player
  def getDealtCard(): MemeCard = {
    dealtCard
  }

  // Method to set player's dealt cards
  def setDealtCard(card: MemeCard): Unit = {
    dealtCard = card
  }

  // empty dealt card list
  def clearDealtCard(): Unit = {
    dealtCard = null
  }

  // Method to get ready state
  def getReadyState(): Boolean = {
    isReady
  }

  // Method to set ready state
  def setReadyState(state: Boolean): Unit = {
    isReady = state
  }
}
