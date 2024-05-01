package ch.makery.whatdoyoumeme.controllers

import akka.actor.typed.ActorRef
import ch.makery.whatdoyoumeme.Actor.LobbyServer
import ch.makery.whatdoyoumeme.Client
import ch.makery.whatdoyoumeme.models.GameClientModel
import ch.makery.whatdoyoumeme.Actor.GameClient
import javafx.fxml.FXML
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.layout.{ColumnConstraints, GridPane, RowConstraints, VBox}
import scalafx.scene.text.Font
import scalafxml.core.macros.sfxml
import scalafx.application.Platform


@sfxml
class LobbyController(@FXML var PlayerList: VBox,
                      @FXML var chatVBox: VBox,
                      @FXML var Message: TextField,
                      @FXML var ReadyButton: Button,
                      @FXML var SendButton: Button) {

  // Initialize variables
  var members: Iterable[GameClientModel] = null
  var lobbyOwner: String = ""

  // Method to set lobby owner name
  def initialize(name: String): Unit = {
    lobbyOwner = name
  }

  // Method to update members list
  def updatePlayerListing(memberS:  List[GameClientModel]): Unit = {

    //initialize the initial setup of the lobby ui
    members = memberS

    // Clear all children from the LobbyContainer
    PlayerList.children.clear()

    // Initialize a row counter
    var rowCounter = 0

    // Add members info to UI
    members.foreach(player => {
      println(player.getPlayerName())

      // Set up gridpane
      val gridPane = new GridPane {
        alignment = scalafx.geometry.Pos.Center
        prefHeight = 72.0
        prefWidth = 1201.0
        rowCounter += 1

        val rowColor = if (rowCounter % 2 == 0) "#B8A7E8" else "#CEC2EF"
        style = s"-fx-background-color: $rowColor; -fx-background-radius: 8; -fx-border-radius: 8;" // Apply row colors


        // Set column contraints
        columnConstraints = List(
          new ColumnConstraints {
            halignment = scalafx.geometry.HPos.Center
            hgrow = scalafx.scene.layout.Priority.Sometimes
            maxWidth = 964.6666259765625
            minWidth = 10.0
            prefWidth = 580.9999847412109
          },
          new ColumnConstraints {
            halignment = scalafx.geometry.HPos.Center
            hgrow = scalafx.scene.layout.Priority.Sometimes
            maxWidth = 964.6666259765625
            minWidth = 10.0
            prefWidth = 359.66668701171875
          }
        )

        // Set row constraints
        rowConstraints = List(new RowConstraints {
          minHeight = 10.0
          prefHeight = 30.0
          vgrow = scalafx.scene.layout.Priority.Sometimes
        })

        // Set label for player name
        val playerNameLabel = new Label {
          text = player.getPlayerName
          style = "-fx-font-family: 'Concert One', sans-serif; -fx-font-size: 36;"

        }

        // Set label for player type
        val lobbyMasterLabel = new Label {
          if (player.getPlayerName() == lobbyOwner) {
            text = "Lobby Owner"
          } else {
            text = "Member"
          }
          style = "-fx-font-family: 'Concert One', sans-serif; -fx-font-size: 36;"
        }

        // Add labels to the GridPane
        add(playerNameLabel, 0, 0)
        add(lobbyMasterLabel, 1, 0)
      }

      // Add grid pane to UI
      PlayerList.children.add(gridPane)
    })
  }

  // Method to received lobby chat message
  def handleChatMessage(message: String): Unit = {

    // Print testing line
    println(s"LobbyController: Received chat message '$message' to update UI.")

    Platform.runLater(() => {
      try {

        // Print testing line
        println(s"LobbyController (UI Thread): Adding chat message '$message' to the UI.")

        // Set up new label
        val chatMessage = new Label(message) {
          font = Font.font("Poppins", 20)
          style = "-fx-padding: 10 5 0 20px;"
        }

        // Add to chat box
        chatVBox.children.add(chatMessage)

        // Print testing line
        println(s"LobbyController (UI Thread): Chat message '$message' added to the UI.")

      } catch {
        case e: Exception =>

          // Print testing line
          println(s"LobbyController (UI Thread): Exception adding message '$message' to UI.")

          // Print the stack trace of any exception
          e.printStackTrace()
      }
    })
  }

  // Method to handle ready button clicked
  def handleReady(action: ActionEvent): Unit = {

    // Get player
    val currentUser = Client.clientUserModel.getPlayerName()
    val currentPlayerOption = members.find(_.getPlayerName() == currentUser)

    currentPlayerOption match {
      case Some(player) =>

        // Print testing line
        println(s"[LobbyController] Setting ready status of player ${player.getPlayerName()} to true")

        // Set ready state of player
        player.setReadyState(true)

        // Disable the ready button
        ReadyButton.setDisable(true)

        // Use game client actor to tell game server the client is ready
        Client.gameClientActor.foreach { gameClientActor =>
          gameClientActor ! GameClient.Ready
        }

      case None =>

        // Print error message
        println(s"Player $currentUser not found in the lobby")
    }
  }

  // Method to handle quit button clicked
  def handleQuit(action: ActionEvent): Unit = {

    // Get player
    val currentUser = Client.clientUserModel.getPlayerName()

    // Send a chat message for the player leaving the lobby
    handleChatMessage(s"$currentUser has left the lobby.")

    // Use game client actor to tell game server the client wants to quit, and perform required logic on UI and property
    // value setting
    Client.gameClientActor.foreach(_ ! GameClient.QuitLobby)

    // Show main menu
    Client.showMainMenuScene()
  }

  // Handle sending message
  def handleMessage(action: ActionEvent): Unit = {

    // Get message entered
    val input = Message.text.value

    // Reset text field
    Message.text = ""

    // Use game client actor to tell lobby server to broadcast client's message
    Client.gameClientActor.foreach { gameClientActor =>
      gameClientActor ! GameClient.SendChatMessage(input)
    }
  }
}