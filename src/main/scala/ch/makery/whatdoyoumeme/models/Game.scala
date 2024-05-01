package ch.makery.whatdoyoumeme.models

import com.hep88.protocol.JsonSerializable

// store the state of the game
case class Game (playersS: List[GameClientModel], memeDeck: MemeDeck, promptDeck: PromptDeck) {

  // initialize game attributes
  var players: List[GameClientModel] = playersS
  var gameFinished: Boolean = false
  var round: Int = 0
  var currentPrompt: PromptCard = null

  // Method to start game
  def startGame(): Unit = {

    memeDeck.initializeDeck()
    promptDeck.initializeDeck()
    promptDeck.shuffle()

    distributeCards()
    startNextRound()
  }

  // Method to distribute cards
  private def distributeCards(): Unit = {

    /// get number of players
    val totalPlayers = players.length

    // Ensure there are enough cards in the deck
    if (memeDeck.getCards().length < totalPlayers * 6) {
      throw new RuntimeException("Not enough cards in the deck for all players.")
    }

    memeDeck.shuffle()

    // Distribute 6 cards to each player
    for (i <- 0 until 6) {
      for (j <- 0 until totalPlayers) {
        players(j).addCardToHand(memeDeck.drawCard().getOrElse(
          throw new RuntimeException("Not enough cards in the deck for all players.")
        ))
      }
    }
  }

  // Method to get current round's prompt
  def getCurrentPrompt(): PromptCard = currentPrompt

  // Method to set current round's prompt
  def setCurrentPrompt(prompt: PromptCard): Unit = {
    currentPrompt = prompt
  }

  // Method to update round
  def nextRound(): Unit = {
    round = round + 1
  }

  // Method to get current round number
  def getCurrentRound(): Int = {
    round
  }

  // Method to start next turn of game
  def startNextRound(): Unit = {
    nextRound()
    setCurrentPrompt(promptDeck.drawCard().get)
  }

  // Method to end game
  def endGame(): Unit = {
    gameFinished = true
  }
}
