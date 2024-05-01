package ch.makery.whatdoyoumeme.Actor

import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.cluster.typed._
import akka.{actor => classic}
import akka.discovery.{Discovery, Lookup, ServiceDiscovery}
import akka.discovery.ServiceDiscovery.Resolved
import akka.actor.typed.scaladsl.adapter._
import scalafx.collections.ObservableHashSet
import scalafx.application.Platform
import akka.cluster.ClusterEvent.ReachabilityEvent
import akka.cluster.ClusterEvent.ReachableMember
import akka.cluster.ClusterEvent.UnreachableMember
import akka.cluster.ClusterEvent.MemberEvent
import akka.actor.Address
import ch.makery.whatdoyoumeme.Actor.LobbyServer
import ch.makery.whatdoyoumeme.{Client, GameServer}
import ch.makery.whatdoyoumeme.models.{Game, GameClientModel, Lobby, MemeCard}
import com.hep88.protocol.JsonSerializable
import scalafx.application.Platform
import scalafx.collections.ObservableHashSet
import ch.makery.whatdoyoumeme.controllers.LobbyController

object GameClient {
  sealed trait Command extends Serializable

  // ===== Joining / Quitting Protocols  =====
  case object QuitLobby extends Command
  case class JoinGameServer(gameServer: ActorRef[GameServer.Command]) extends Command
  case object LobbyDisbanded extends Command
  case class StartJoin(lobbyServerRef: ActorRef[LobbyServer.Command]) extends Command
  case class StartActor(gameClientModelGiven: GameClientModel) extends Command
  case class RequestToJoinBackLobby() extends Command
  case class JoinBackLobby(lobby: Lobby, lobbyPlayersReceived: List[GameClientModel]) extends Command
  // ===== Joining / Quitting Protocols  =====

  // ===== Misc Protocols  =====
  case object Ready extends Command
  case class RequestAllLobbyPlayers() extends Command
  case class ReceiveAllLobbyPlayers(lobbyPlayersReceived: List[GameClientModel]) extends Command
  case class ReceiveChatMessage(message: String) extends Command
  case class ReceiveGameChatMessage(message: String) extends Command
  case class SendChatMessage(message: String) extends Command
  case class SendGameChatMessage(message: String) extends Command
  case class ReceiveGameInstance(gameInstance: Game) extends Command
  case class DealCard(cardIndex: Int) extends Command
  case class ShuffleCard() extends Command
  case class ShowWaitingLabel() extends Command
  case class ShowVotingScene(game: Game, dealtCard:  Map[GameClientModel, MemeCard]) extends Command
  case class GetPlayerToVote(playerName: String) extends Command
  case class EndGame() extends Command
  case class showLeaderBoard(players: List[GameClientModel]) extends Command
  // ===== Misc Protocols  =====

  // ===== Properties  =====
  var currentLobbyServer : Option[ActorRef[LobbyServer.Command]] = None
  var currentGameServer : Option[ActorRef[GameServer.Command]] = None
  var gameClientModel: Option[GameClientModel] = None
  var currentLobbyMembers : List[GameClientModel] = List()
  // ===== Properties  =====

  // initial behaviour setup and only behaviour used
  def apply(): Behavior[GameClient.Command] = Behaviors.setup { context =>
    Behaviors.receiveMessage { message =>
      message match {

        // Start the actor by initializing its gameClientModel stored
        case StartActor(gameClientModelGiven: GameClientModel) =>
          gameClientModel = Some(gameClientModelGiven)
          Behaviors.same

        // Start joining lobby server
        case StartJoin(lobbyServerRef: ActorRef[LobbyServer.Command]) =>
          currentLobbyServer = Some(lobbyServerRef)
          currentLobbyServer.get ! LobbyServer.HandleJoining(gameClientModel.get)
          Behaviors.same

        // Quit joined lobby server
        case QuitLobby =>
          currentLobbyServer.foreach(_ ! LobbyServer.HandleQuitting(gameClientModel.get))
          Client.gameClientActor = None // remove pointer to this game client actor
          Behaviors.stopped

        // Request from lobby server all current lobby players
        case RequestAllLobbyPlayers() =>
          currentLobbyServer.get ! LobbyServer.SendAllPlayerInLobby(context.self)
          Behaviors.same

        // Receive response from lobby server for all current lobby players
        case ReceiveAllLobbyPlayers(lobbyPlayersReceived) =>
          currentLobbyMembers = List()
          currentLobbyMembers = lobbyPlayersReceived
          Platform.runLater {
            Client.lobbyController.updatePlayerListing(currentLobbyMembers)
          }
          Behaviors.same

        // Let lobby server know client is ready to play
        case Ready =>
          println(s"[LobbyMember] Sending ready status update for ${gameClientModel.get.getPlayerName()}")
          currentLobbyServer.foreach(_ ! LobbyServer.HandleReadyClick(gameClientModel.get))
          Behaviors.same

        // Officially join game server and setting the game server actor reference in memory
        case JoinGameServer(gameServer) =>
          currentGameServer = Some(gameServer)
          Behaviors.same

        // If lobby has been disbanded, go back to main menu and set pointer to this actor in client's app to none
        case LobbyDisbanded =>
          Platform.runLater(() => {
            Client.showMainMenuScene()
          })
          println("Lobby has been disbanded, Redirecting to main menu...")
          Client.gameClientActor = None
          Behaviors.stopped

        // Handle receiving chat message from in lobby server
        case ReceiveChatMessage(message) =>
          println(s"LobbyMember: Received chat message '$message'. Updating UI.")
          Platform.runLater(() => {
              Client.lobbyController.handleChatMessage(message)
          })
          Behaviors.same

        // Handle receiving chat message from in game server
        case ReceiveGameChatMessage(message) =>
          println(s"LobbyMember: Received chat message '$message'. Updating UI.")
          Platform.runLater(() => {
            Client.gameController.handleChatMessage(message)
          })
          Behaviors.same

        // Handle sending chat message from in lobby server
        case SendChatMessage(message) =>
          println(s"LobbyMember: Sending chat message '$message' to LobbyServer for broadcasting.")
          currentLobbyServer.foreach(_ ! LobbyServer.BroadcastChatMessage(s"${gameClientModel.get.getPlayerName}: $message"))
          Behaviors.same

        // Handle sending chat message from in game server
        case SendGameChatMessage(message) =>
          println(s"GameClient: Sending chat message '$message' to LobbyServer for broadcasting.")
          currentGameServer.foreach(_ ! GameServer.BroadcastChatMessage(s"${gameClientModel.get.getPlayerName}: $message"))
          Behaviors.same

        // Handle receiving newly updated or current state of game which is used to update client's game scene
        case ReceiveGameInstance(gameInstance) =>
          println(s"Received game instance ${gameClientModel.get.getPlayerName()}")
          Platform.runLater(() => {
            Client.showNewGameScene(gameInstance)
          })
          println("Successfully received game instance")
          Behaviors.same
          
        // Handle telling game server client has dealt a card
        case DealCard(cardIndex) =>
          currentGameServer.foreach(_ ! GameServer.DealCard(gameClientModel.get.getPlayerName(), cardIndex))
          Behaviors.same

        // Handle telling game server client wants to shuffle card, receive new set of card (after shuffling)
        case ShuffleCard() =>
          currentGameServer.foreach(_ ! GameServer.ShuffleCard(gameClientModel.get.getPlayerName()))
          Behaviors.same

        // Handles showing waiting label on client's game scene
        case ShowWaitingLabel() =>
          Platform.runLater(() => {
            Client.gameController.showWaitingLabel()
          })
          Behaviors.same

        /// Handles showing voting scene for client
        case ShowVotingScene(game, dealtCards) =>
          Platform.runLater(() => {
            Client.showVotingScene(game, dealtCards)
          })
          Behaviors.same

        // Handles voting of a player to game server
        case GetPlayerToVote(playerName) =>
          currentGameServer.foreach(_ ! GameServer.VotePlayer(playerName))
          Behaviors.same

        // Send request to end the game to game server when detected clients has no more cards
        case EndGame() =>
          currentGameServer.foreach(_ ! GameServer.EndGame())
          println("Asked server to end game")
          Behaviors.same

        // Handles showing the leaderboard scene for client
        case showLeaderBoard(players) =>
          Platform.runLater(() => {
            Client.showLeaderBoardScene(players)
          })
          Behaviors.same

        // Handles client's request to join back the lobby after game ends
        case RequestToJoinBackLobby() =>
          currentLobbyServer.get ! LobbyServer.HandleReqToJoinBackLobby(context.self)
          Behaviors.same

        // Handles message of client's successful request from game server to join back the lobby after game ends
        case JoinBackLobby(lobby, lobbyPlayersReceived) =>
          currentLobbyMembers = List()
          currentLobbyMembers = lobbyPlayersReceived
          Platform.runLater(() => {
            Client.showLobbyScene(lobby.nameS)
            Client.lobbyController.updatePlayerListing(currentLobbyMembers)
          })
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }
}

