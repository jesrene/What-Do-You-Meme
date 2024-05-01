package ch.makery.whatdoyoumeme.utils

import ch.makery.whatdoyoumeme.{Client, GameServer}
import ch.makery.whatdoyoumeme.models.{Game, GameClientModel, MemeCard, MemeDeck}
import scalafx.animation.AnimationTimer

// game loop
class GameLogic(gameS: Game) {

  // initialize variables
  var game: Game = gameS
  var gameInProgress = false
  private var playersDealt: Set[GameClientModel] = Set()
  var playersDealtCard: Map[GameClientModel, MemeCard] = Map()
  private var thisRoundEnd: Boolean = false
  private var playersVote: Map[GameClientModel, Int] = Map()
  var voteRoundEnd: Boolean = false
  var votes: Int = 0

  // Method to initialize game
  def initializeGame(): Unit = {

    // set game and game status
    gameInProgress = true

    //start game loop
    game.startGame()
    startGameLoop()

    // Initialize the playersDealtCard map with all player IDs
    playersDealtCard = game.players.map(player => player -> null).toMap

    // test line for game loop
    println("loop started")

  }

  // Method to shuffle cards
  def shuffleCards(player: GameClientModel): Unit = {

    game.players.find(_ == player).foreach { player =>

      // Check if the player has already shuffled in this round
      if (!player.getHasShuffled) {
        val playerHandLength = player.showHand().length

        // Add cards back to deck
        player.showHand().foreach { card =>
          game.memeDeck.addCard(card)
          player.removeCardFromHand(card)
        }

        // Shuffle the meme deck
        game.memeDeck.shuffle()

        // Add new cards to hand
        for (i <- 0 until playerHandLength) {

          val newCard =  game.memeDeck.drawCard().get
          println(newCard.getMemeID())
          player.addCardToHand(newCard)

        }

        // Mark the player as having dealt a card for the current round
        playersDealt += player

        // Update the dealt card in the playersDealtCard map
        playersDealtCard += (player -> null)

        // Mark the player as having shuffled
        player.setHasShuffled(true)
      }
    }
  }

  // Method to deal cards
  def dealCard(playerGiven: GameClientModel, cardIndex: Int): Unit = {

    game.players.find(_ == playerGiven).foreach { player =>

      // Check if the provided index is valid
      if (cardIndex >= 0 && cardIndex < player.showHand().length) {
        val selectedCard = player.showHand()(cardIndex)

        // Update the game state and remove the dealt card from the player's hand
        player.setDealtCard(selectedCard)

        // Mark the player as having dealt a card for the current round
        playersDealt += player

        // Update the dealt card in the playersDealtCard map
        playersDealtCard += (player -> selectedCard)

      } else {
        println("Invalid card index")
      }
    }
  }

  // Method to get game instance
  def getUpdatedGame(): Game = {
    game
  }

  // Method to add vote to player
  def addVote(player: GameClientModel): Unit = {

    val currentVote = playersVote.getOrElse(player, 0)
    val newVote = currentVote + 1
    playersVote += (player -> newVote)
    votes += 1

    println("voted for " + player.getPlayerName())
  }

  // Method to check player dealt status
  def checkPlayerDealStatus(): Boolean = {
    if (playersDealt.size == game.players.size) {

      // Clear the playersDealt set for the next round
      playersDealt = Set()
      true
    } else {
      false
    }
  }

  // Method to check voting end
  def checkVotingEnd(): Boolean = {
    println("votes: " + votes + " Players: " + game.players.size)
    votes == game.players.size
  }

  // Method to go to next round
  def updateRound(): Unit = {
    playersVote = Map()
    thisRoundEnd = true
    voteRoundEnd = false
  }

  // Method to get winning player of the round
  def getWinningPlayer(): Unit = {

    // Find the player with the maximum votes
    val winningPlayer = playersVote.maxBy(_._2)._1
    println("Winning player: " + winningPlayer.getPlayerName())

    // If there is a winning player
    if(winningPlayer != null){

      // Get dealt card
      val dealtCard = winningPlayer.getDealtCard()

      println("Winning player dealt: " + dealtCard.getMemeID())

      // If there is a dealt card
      if (dealtCard != null) {

        // remove the card from hand
        winningPlayer.removeCardFromHand(dealtCard)
        println(s"Player ${winningPlayer.getPlayerName()} wins with ${playersVote(winningPlayer)} votes!")
      }
    } else {
      println("Its a Tie!")
    }

  }

  // Method to handle player's turns
  def handlePlayerTurns(): Unit = {

    votes = 0

    getWinningPlayer()
    println("winning player ran")

    // Reset state
    game.players.foreach(player => {
      player.setHasShuffled(false)
      player.clearDealtCard()
    })

    // Go to next round
    updateRound()
    game.startNextRound()

  }

  // Method to end game
  def endGame(): Unit = {
    game.endGame()
  }

  // Method of main game loop
  private def startGameLoop(): Unit = {

    // game loop
    val gameLoop: AnimationTimer = AnimationTimer { deltaTime =>

      // check statement for game loop
      if (gameInProgress) {

        // stop game loop if game is finished
        if (game.gameFinished) {
          gameInProgress = false

          // test line for loop stopping
          println("loop stopped")
        }
      }
    }

    // start game loop
    gameLoop.start()

  }
}
