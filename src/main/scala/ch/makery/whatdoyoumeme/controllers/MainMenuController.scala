package ch.makery.whatdoyoumeme.controllers

import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.adapter.ClassicActorSystemOps
import ch.makery.whatdoyoumeme.Actor.{GameClient, LobbyServer}
import ch.makery.whatdoyoumeme.Client.{clientGuardianActor, mainSystem}
import ch.makery.whatdoyoumeme.models.{GameClientModel, Lobby, ServerLobbyItem, User}
import ch.makery.whatdoyoumeme.{Client, ClientGuardian, util}
import ch.makery.whatdoyoumeme.util.MusicPlayer
import javafx.fxml.FXML
import scalafx.animation.TranslateTransition
import scalafx.event.ActionEvent
import scalafx.scene.control.{Button, Label, TextField}
import scalafx.scene.image.{Image, ImageView}
import scalafx.scene.layout.{AnchorPane, ColumnConstraints, GridPane, RowConstraints, VBox}
import scalafx.scene.text.Text
import scalafxml.core.macros.sfxml
import scalafx.util.Duration
import scalafx.Includes._
import scalafx.application.Platform
import scalafx.event.ActionEvent
import java.awt.Desktop
import java.net.URI


@sfxml
class MainMenuController (@FXML private val logo1: ImageView,
                          @FXML private val logo2: ImageView,
                          @FXML private var volumeButton: Button,
                          @FXML var Username: Text,
                          @FXML var RoomCode: TextField,
                          @FXML var LobbyContainer: VBox,
                          @FXML var viewLobby: AnchorPane,
                          private var isMusicPlaying: Boolean = true) {

  // Initialize variables
  var clientGuardianActor: Option[ActorRef[ClientGuardian.Command]] = None
  private var currentLobbies: Iterable[ServerLobbyItem] = None

  // Method to initialize main menu info
  def initialize(): Unit = {

    // get all newest lobbies, set currentLobbies variable
    Client.clientGuardianActor ! ClientGuardian.GetAllLobbies()

    // Call method to play animation and music
    logoAnimation()
    startBGM()

    // Show username
    Username.text = Client.clientUserModel.getPlayerName()

    // Set lobby view to invisible
    viewLobby.visible = false
  }


  // Method to set zoom in transition to logo
  private def logoAnimation(): Unit = {

    // Set up transition
    val slideLeft = new TranslateTransition(Duration(700))
    slideLeft.setNode(logo1)
    slideLeft.setFromX(-800)
    slideLeft.setToX(0)
    slideLeft.play()

    // Set up transition
    val slideRight = new TranslateTransition(Duration(700))
    slideRight.setNode(logo2)
    slideRight.setFromX(800)
    slideRight.setToX(0)
    slideRight.play()
  }


  // Method to play background music
  private def startBGM(): Unit = {

    // If client does not off music
    if(!Client.musicOff){

      // Play music
      val file = getClass.getResource("/BGM/mainmenubgm.wav")
      MusicPlayer.playBackgroundMusic(file)

      // Set image to music playing
      val image = new Image("Images/Icons/soundOn.png") // Replace with your muted icon image path
      volumeButton.setGraphic(new ImageView(image))

      // Set boolean
      isMusicPlaying = true

    } else {

      // Set boolean
      isMusicPlaying = false

      // Stop background music from playing
      MusicPlayer.stopBackgroundMusic()

      // Change image to music muted
      val image = new Image("Images/Icons/soundOff.png") // Replace with your muted icon image path
      volumeButton.setGraphic(new ImageView(image))
    }
  }


  // Method to handle git button clicked
  def handleGitButton(action: ActionEvent): Unit = {

    // Set up link
    val gitHubLink = "https://github.com/cheryl-toh/What-Do-Your-Meme"

    // Check if browsing is allowed
    if (Desktop.isDesktopSupported && Desktop.getDesktop.isSupported(Desktop.Action.BROWSE)) {

      // Browse link
      Desktop.getDesktop.browse(new URI(gitHubLink))

    } else {

      // Print message
      println("Desktop browsing is not supported on this platform.")
    }
  }


  // Method to handle volume button clicked
  def handleVolumeButtonClicked(action: ActionEvent): Unit = {

    if (isMusicPlaying) {

      // Set boolean values
      Client.musicOff = true
      isMusicPlaying = false

      // Stop music
      MusicPlayer.stopBackgroundMusic()

      // Set image to music muted
      val image = new Image("Images/Icons/soundOff.png") // Replace with your muted icon image path
      volumeButton.setGraphic(new ImageView(image))

    } else {

      // Set boolean value
      Client.musicOff = false

      // Get music
      val file = getClass.getResource("/BGM/mainmenubgm.wav")

      // Play music
      MusicPlayer.playBackgroundMusic(file)

      // Set boolean value
      isMusicPlaying = true

      // Set image to music playing
      val image = new Image("Images/Icons/soundOn.png") // Replace with your muted icon image path
      volumeButton.setGraphic(new ImageView(image))
    }
  }


  // Given current lobbies, add them to be seen in UI
  def addLobbyToUI(lobbyList: Iterable[ServerLobbyItem]): Unit = {

    // Reset lobby list
    currentLobbies = lobbyList


    if (LobbyContainer != null || LobbyContainer.children != null) {

      // Clear all children from the LobbyContainer except the first one
      LobbyContainer.children.remove(1, LobbyContainer.children.size())

    }

    // Reset existing lobby UI
    currentLobbies.foreach(lobby => {

      val gridPane = new GridPane {
        alignment = scalafx.geometry.Pos.Center
        prefHeight = 30.0
        prefWidth = 505.0

        columnConstraints = List.tabulate(4) { i =>
          new ColumnConstraints {
            halignment = scalafx.geometry.HPos.Center
            hgrow = scalafx.scene.layout.Priority.Sometimes
            maxWidth = if (i == 3) 363.0 else 479.0
            minWidth = 10.0
            prefWidth = if (i == 3) 251.0 else 322.0
          }
        }

        rowConstraints = List(new RowConstraints {
          minHeight = 10.0
          prefHeight = 30.0
          valignment = scalafx.geometry.VPos.Center
          vgrow = scalafx.scene.layout.Priority.Sometimes
        })
      }

      val lobbyIdLabel = new Label {
        id = "LobbyId"
        text = lobby.LobbyIDS
      }

      val lobbyMasterLabel = new Label {
        id = "LobbyMasterName"
        text = lobby.nameS
      }

      val playerNumberLabel = new Label {
        id = "PlayerNumber"
        text = s"${lobby.numberOfPlayer}/6"
      }

      val joinButton = new Button {
        mnemonicParsing = false
        onAction = (event: scalafx.event.ActionEvent) => handleJoin(event, lobby.LobbyIDS)
        prefHeight = 12.0
        prefWidth = 101.0
        text = "Join"
        style = "-fx-background-color: #9F82EE; -fx-text-fill: white; -fx-font-weight: bold;"
      }

      // Add the components to the GridPane
      gridPane.add(lobbyMasterLabel, 0, 0)
      gridPane.add(lobbyIdLabel, 1, 0)
      gridPane.add(playerNumberLabel, 2, 0)
      gridPane.add(joinButton, 3, 0)

      // Add the GridPane to the lobbyContainer (VBox)
      LobbyContainer.children.add(gridPane)
    })
  }


  // Method to show existing lobbies to join
  def handleShowLobby(action: ActionEvent): Unit = {
    refreshLobbyList()

    //open lobby dialog
    viewLobby.visible = true

  }


  // Method to handle back button clicked
  def handleBack(action: ActionEvent): Unit = {
    viewLobby.visible = false
  }


  // Method for create new lobby button
  def handleNewLobby(action: ActionEvent): Unit = {
    // Get code for new lobby
    val roomCode = RoomCode.text.value

    // Check if existing lobby has the same code, if yes don't create
    for(existingLobby <- currentLobbies){
      if(existingLobby.LobbyIDS == roomCode){
        println(s"Cannot create lobby server, room code '$roomCode' already exist and taken by another lobby server")
        return
      }
    }

    // Create Game Client model and actor First
    Client.gameClientModel = GameClientModel(Client.clientUserModel.getPlayerName())

    Client.gameClientActor = Some(Client.mainSystem.spawn(GameClient(), "lobbyMember"))
    Client.gameClientModel.gameClientRef = Client.gameClientActor.get

    // start up game client actor
    Client.gameClientActor.get ! GameClient.StartActor(Client.gameClientModel)

    // Create new LobbyServerActor for all Clients to run on (To add owner of lobby ref here)
    Client.lobbyServerActor = Some(Client.mainSystem.spawn(LobbyServer(roomCode, Client.clientGuardianActor,
      Client.gameClientActor.get, Client.clientUserModel.getPlayerName()), "lobbyServer"))

    // Create Lobby Entry into server (LobbyRef)
    clientGuardianActor foreach ((x) => x ! ClientGuardian.CreateLobby(
      roomCode, Client.lobbyServerActor.get,
      Client.clientUserModel.getPlayerName())
      )

    // adding current client into his own lobby
    Client.gameClientActor map ((x) => x ! GameClient.StartJoin(Client.lobbyServerActor.get))

    // show lobby scene
    Client.showLobbyScene(Client.clientUserModel.getPlayerName())
  }

  // let player join selected lobby
  def handleJoin(action: ActionEvent, lobbyID: String): Unit = {

    // Check if the lobby exists in the available lobbies
    val selectedLobby = currentLobbies.find(_.LobbyIDS == lobbyID)

    // will account for empty selected lobby
    selectedLobby.foreach { lobby =>
      if(selectedLobby.get.numberOfPlayer < 6 && !selectedLobby.get.inGameStatus) {

        // Create game client model and actor first
        Client.gameClientModel = GameClientModel(Client.clientUserModel.getPlayerName())

        Client.gameClientActor = Some(Client.mainSystem.spawn(GameClient(), "lobbyMember"))
        Client.gameClientModel.gameClientRef = Client.gameClientActor.get

        // start up game client actor
        Client.gameClientActor.get ! GameClient.StartActor(Client.gameClientModel) // start up lobby member actor

        // Send a request to join selected lobby
        Client.gameClientActor map ((x) => x ! GameClient.StartJoin(lobby.lobbyServerRef))

        // Show the lobby scene
        Client.showLobbyScene(lobby.nameS)
      }
    }
  }

  // Method to handle how to play button clicked
  def handleHowToPlay(action: ActionEvent): Unit = {

    //play button clicked sound
    val file = getClass.getResource("/Sounds/click.wav")
    MusicPlayer.playSoundEffect(file)

    // stop background music
    MusicPlayer.stopBackgroundMusic()

    // Show how to play dialog
    Client.showHowToPlayDialog()
  }

  // Method to request lobby list
  def refreshLobbyList() : Unit = Platform.runLater {
    clientGuardianActor.foreach(_ ! ClientGuardian.GetAllLobbies())
  }
}