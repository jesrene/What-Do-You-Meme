package ch.makery.whatdoyoumeme.models

import scala.util.Random

class MemeDeck extends Serializable{

  //list to store all 52 cards
  private var cards: List[MemeCard] = Nil

  // Method to initialize card objects
  def initializeDeck(): Unit = {

    // initialize card ID range
    val cardID = 1 to 44

    // add all cards to deck
    cards = (for {
      id <- cardID

      cardImage = s"/Images/Memes/${id}.png"
    } yield new MemeCard(id, cardImage)).toList


  }

  // Method to shuffle deck
  def shuffle(): Unit = {

    // Shuffle the meme cards
    cards = Random.shuffle(cards)

  }

  // Method to add a single card to the deck
  def addCard(card: MemeCard): Unit = {
    cards = cards :+ card
  }

  // Method to draw top card of the deck
  def drawCard(): Option[MemeCard] = {
    cards match {
      case Nil => None
      case head :: tail =>
        cards = tail
        Some(head)
    }
  }

  // Method to get meme cards
  def getCards(): List[MemeCard] = {
    cards
  }

}
