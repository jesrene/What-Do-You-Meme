package ch.makery.whatdoyoumeme.controllers

import ch.makery.whatdoyoumeme.Actor.GameClient
import ch.makery.whatdoyoumeme.Client
import ch.makery.whatdoyoumeme.models.{Game, GameClientModel, MemeCard, MemeDeck, PromptDeck, User}
import ch.makery.whatdoyoumeme.utils.{GameLogic, Timer}
import javafx.fxml.FXML
import scalafx.application.Platform
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout.VBox
import scalafx.scene.shape.Rectangle
import scalafx.scene.text.{Font, Text}
import scalafxml.core.macros.sfxml

@sfxml
class GameController(@FXML var RoundNumber: Text,
                     @FXML var Prompt: Text,
                     @FXML var PrevMeme: Button,
                     @FXML var NextMeme: Button,
                     @FXML var DealButton: Button,
                     @FXML var ShuffleButton: Button,
                     @FXML var MemeImage: ImageView,
                     @FXML var WaitingLabel: Label,
                     @FXML var Shade: Rectangle,
                     @FXML var chatVBox: VBox,
                     @FXML var CardIndexView: Text,
                     @FXML var Message: TextField) {

  // Initialize variables
  private var game: Game = null
  private var currentCard: MemeCard = null
  var currentPlayer: GameClientModel = null


  // Method to get game initialize by server
  def startGame(gameS: Game): Unit = {
    game = gameS

    displayFirstCard()
    showRound()
    showPrompt()
    WaitingLabel.visible = false
    Shade.visible = false
    enableButtons()
  }


  // Method to play distribute card animation
  private def displayFirstCard(): Unit = {

    // Get game client's model tied to this game client from given game object
    game.players.foreach(player => {
      if (player.getPlayerName() == Client.clientUserModel.getPlayerName()) {
        currentPlayer = player
      }
    })

    // Get current player hand
    val currentPlayerHand = currentPlayer.showHand()

    // UI Image view
    val cardImageView = MemeImage

    // Display the first card in the MemeImage ImageView
    if (currentPlayerHand.length != 0) {

      // Get and set image for the first card
      val firstCard = currentPlayerHand.head
      cardImageView.image = new Image(firstCard.getImage())

      // Set current card
      currentCard = firstCard

      // Set card text
      CardIndexView.text = "Card 1/" + currentPlayerHand.length

    } else {

      println("Game has ended in controller")

      // Use game client actor to tell game server to end game
      Client.gameClientActor.foreach { gameClientActor =>
        gameClientActor ! GameClient.EndGame()
      }
    }
  }

  // Method to add chat message to chat box
  def handleChatMessage(message: String): Unit = {

    // Set message as label and add it to chat box
    val chatMessage = new Label(message) {
      font = Font.font("Poppins", 20)
      style = "-fx-padding: 10 5 0 20px;"
    }

    chatVBox.children.add(chatMessage)
  }

  // Show round number in UI
  def showRound(): Unit = {
    RoundNumber.text = "Round " + game.getCurrentRound().toString
  }

  // show prompt in UI
  def showPrompt(): Unit = {
    Prompt.text = game.getCurrentPrompt().getPromptText()
  }

  // Method to chow waiting scene
  def showWaitingLabel(): Unit = {

    // Make waiting scene visible
    WaitingLabel.visible = true
    Shade.visible = true

    // Disable buttons in UI
    PrevMeme.disable = true
    NextMeme.disable = true
    DealButton.disable = true
    ShuffleButton.disable = true
  }

  // To make every buttons clickable when waiting label is not visible
  def enableButtons(): Unit = {
    PrevMeme.disable = false
    NextMeme.disable = false
    DealButton.disable = false
    ShuffleButton.disable = false
  }

  // Method to handle deal button clicked
  def handleDeal(action: ActionEvent): Unit = {

    // Get index of current card on player's hand
    val currentPlayerHand = currentPlayer.showHand()
    val selectedCardIndex = currentPlayerHand.indexOf(currentCard)


    // Use game client actor to tell game server this client has dealt what card
    Client.gameClientActor.foreach { gameClientActor =>
      gameClientActor ! GameClient.DealCard(selectedCardIndex)
    }
  }

  // Method to handle shuffle button clicked
  def handleShuffle(action: ActionEvent): Unit = {

    // Use game client actor to tell game server this client wants to shuffle
    Client.gameClientActor.foreach { gameClientActor =>
      gameClientActor ! GameClient.ShuffleCard()
    }
  }

  // Method to handle previous meme button clicked
  def handlePrevMeme(action: ActionEvent): Unit = {

    // Get player hand cards
    val currentPlayerHand = currentPlayer.showHand()

    // Get UI element
    val cardImageView = MemeImage

    // if there is more card than 1 on their hand
    if (currentPlayerHand.length > 1) {

      // Find the index of the current card
      val currentCardIndex = currentPlayerHand.indexOf(currentCard)

      // Calculate the index of the previous card (circular, looping to the last card if at the beginning)
      val prevCardIndex = (currentCardIndex - 1 + currentPlayerHand.length) % currentPlayerHand.length

      // Get the previous card
      val prevCard = currentPlayerHand(prevCardIndex)

      // Update card index number
      val cardIndex = prevCardIndex + 1
      CardIndexView.text = "Card " + cardIndex + "/" + currentPlayerHand.length

      // Update the current card in the game
      currentCard = prevCard

      // Update the image in the MemeImage ImageView
      cardImageView.image = new Image (currentCard.getImage())

    } else {

      // Do nothing and print line
      println("Player has only one card.")
    }
  }

  // Method to handle next meme button clicked
  def handleNextMeme(action: ActionEvent): Unit = {

    // Get player hand cards
    val currentPlayerHand = currentPlayer.showHand()

    // Get UI element
    val cardImageView = MemeImage

    // Display the next card in the MemeImage ImageView
    if (currentPlayerHand.length > 1) {

      // Find the index of the current card
      val currentCardIndex = currentPlayerHand.indexOf(currentCard)

      // Calculate the index of the next card (circular, looping to the first card if at the end)
      val nextCardIndex = (currentCardIndex + 1) % currentPlayerHand.length

      // Get the next card
      val nextCard = currentPlayerHand(nextCardIndex)

      val cardIndex = nextCardIndex + 1
      CardIndexView.text = "Card " + cardIndex + "/" + currentPlayerHand.length

      // Update the current card in the game
      currentCard = nextCard

      // Update the image in the MemeImage ImageView
      cardImageView.image = new Image (currentCard.getImage())

    } else {

      // Do nothing and print line
      println("Player has only one card")
    }
  }

  // Method to handle send button clicked
  def handleMessage(action: ActionEvent): Unit = {

    // Get message entered
    val input = Message.text.value

    // Reset text field
    Message.text = ""

    // Use game client actor to tell game server to broadcast message
    Client.gameClientActor.get ! GameClient.SendGameChatMessage(input)
  }
}

