package ch.makery.whatdoyoumeme.Actor

import ch.makery.whatdoyoumeme.models.{GameClientModel, Lobby}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors
import ch.makery.whatdoyoumeme.{Client, ClientGuardian, GameServer, Server}
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
import scalafx.application.Platform
import scalafx.collections.ObservableHashSet

object LobbyServer {
  sealed trait Command extends Serializable

  // ===== Protocols =====
  case class HandleJoining(gameClient: GameClientModel) extends Command
  case class SendAllPlayerInLobby(gameClientRef: ActorRef[GameClient.Command]) extends Command
  case class HandleReadyClick(gameClient: GameClientModel) extends Command
  case class HandleQuitting(gameClient: GameClientModel) extends Command
  case class BroadcastChatMessage(message: String) extends Command
  case class StartGame() extends Command
  case class HandleReqToJoinBackLobby(gameClientRef: ActorRef[GameClient.Command]) extends Command
  case class GameEnded() extends Command
  // ===== Protocols =====

  // ===== Properties =====
  private var numberOfPlayer: Int = 1
  private var lobbyMembers : List[GameClientModel] = Nil
  private val readyMembers = new ObservableHashSet[GameClientModel]() // track ready members
  private var lobbyID = ""
  private var lobbyObj: Option[Lobby] = None
  private var lobbyOwnerClientGuardianRef: Option[ActorRef[ClientGuardian.Command]] = None
  private var lobbyOwnerMemberActor: Option[ActorRef[GameClient.Command]] = None
  // ===== Properties =====


  def apply(lobbyIDGiven: String, lobbyOwnerClientGuardian: ActorRef[ClientGuardian.Command],
            lobbyOwnerMemberActorGiven: ActorRef[GameClient.Command], lobbyOwnerName: String): Behavior[LobbyServer.Command] = Behaviors.setup { context =>

    // Set up initial values of properties
    lobbyID = lobbyIDGiven
    lobbyObj = Some(Lobby(lobbyID, lobbyOwnerName))
    lobbyOwnerClientGuardianRef = Some(lobbyOwnerClientGuardian)
    lobbyOwnerMemberActor = Some(lobbyOwnerMemberActorGiven)

    Behaviors.receiveMessage { message =>
      message match {

        // Handle joining of new game clients
        case HandleJoining(gameClientRef) =>
          lobbyMembers = lobbyMembers :+ gameClientRef
          lobbyMembers.foreach(member => println(member.getPlayerName() + " in server list"))

          // Update Number of players
          lobbyOwnerClientGuardianRef.map(_ ! ClientGuardian.UpdateClientLobbyNumber(lobbyID, 1)) // in server list
          numberOfPlayer += 1 // in property

          // Update the joining of new member to all the current lobby members (already exist and just joined)
          for(clientModel <- lobbyMembers){
            context.self ! SendAllPlayerInLobby(clientModel.gameClientRef)
          }
          Behaviors.same

        // Handle quitting of existing game clients
        case HandleQuitting(gameClient) =>
          if (gameClient.getPlayerName() == lobbyObj.map(_.getLobbyMasterName).getOrElse("")) {
            println(s"Lobby owner ${gameClient.getPlayerName()} is quitting. Disbanding lobby.")

            // Disband the lobby
            lobbyOwnerClientGuardianRef.foreach(_ ! ClientGuardian.RemoveLobby(lobbyID))
            lobbyMembers.foreach(_.gameClientRef ! GameClient.LobbyDisbanded)
            lobbyMembers = List()

            Behaviors.stopped
          } else {
            // normal member quitting

            // Let everyone know a member has quit
            println(s"Member ${gameClient.getPlayerName()} is quitting.")
            context.self ! BroadcastChatMessage(s"${gameClient.getPlayerName()} has left the lobby.")

            // Just remove this member
            lobbyMembers = lobbyMembers.filterNot(_.getPlayerName() == gameClient.getPlayerName())

            // Remove members from ready members list
            readyMembers -= gameClient

            // Update Number of platers
            lobbyOwnerClientGuardianRef.map(_ ! ClientGuardian.UpdateClientLobbyNumber(lobbyID, -1)) // in Server List
            numberOfPlayer += 1 // in property

            // Notify remaining members of the update
            lobbyMembers.foreach(member => context.self ! SendAllPlayerInLobby(member.gameClientRef))

            // Check if game can be started
            context.self ! StartGame()

            Behaviors.same
          }

        // Handle request of sending every player info in lobby to game client ref
        case SendAllPlayerInLobby(gameClientRef) =>
          gameClientRef ! GameClient.ReceiveAllLobbyPlayers(lobbyMembers)
          Behaviors.same

        // Handle clients clicking ready to start game
        case HandleReadyClick(gameClientModel) =>
          println(s"[LobbyServer] Received ready status update from ${gameClientModel.getPlayerName()}")
          context.self ! BroadcastChatMessage(s"${gameClientModel.getPlayerName()} is ready!")

          // Find the GameClientModel in the lobbyMembers set and update it
          lobbyMembers.find(_.getPlayerName() == gameClientModel.getPlayerName()).foreach { member =>
            // Update the ready state of the member
            member.setReadyState(true)

            // Update the readyMembers set
            readyMembers += member
          }

          // Log the status of each member
          lobbyMembers.foreach(member => println(s"Status of ${member.getPlayerName()}: ${member.getReadyState()}"))
          println(s"Ready Members: ${readyMembers.size}, Total Members: ${lobbyMembers.size}")

          // Check if game can be started
          context.self ! StartGame()
          Behaviors.same

        // Broadcast lobby server message
        case BroadcastChatMessage(message) =>
          println(s"LobbyServer: Broadcasting chat message '$message' to all members.")
          lobbyMembers.foreach(_.gameClientRef ! GameClient.ReceiveChatMessage(message))
          Behaviors.same

        // Handle starting and initialization of Game Server and game when all game clients are ready
        case StartGame() =>
          if (readyMembers.size == lobbyMembers.size){
            val gameStartMessage = "Game is starting"
            println(gameStartMessage) // Placeholder message for starting the game

            //broadcast onto Lobby Chat box
            context.self ! BroadcastChatMessage(gameStartMessage)

            // Spawn GameServerActor here
            Client.gameServerActor = Some(Client.mainSystem.spawn(GameServer(context.self), "GameServer"))

            // Reset lobby member's game state
            lobbyMembers.foreach(player => {
              player.clearHand()
              player.setHasShuffled(false)
              player.clearDealtCard()
            })

            // Initialize Game Resources
            Client.gameServerActor.get ! GameServer.StartActor(lobbyMembers.toList)

            // Update lobby status in ServerApp reference
            lobbyOwnerClientGuardianRef.map(_ ! ClientGuardian.UpdateClientLobbyStatus(lobbyID, true))
          }
          Behaviors.same

        // Handle when ongoing game has ended. GameServerActor removed previously by GameServerActor already
        case GameEnded() =>
          readyMembers.clear()

          // Update lobby status in ServerApp reference
          lobbyOwnerClientGuardianRef.map(_ ! ClientGuardian.UpdateClientLobbyStatus(lobbyID, false))
          Behaviors.same

        // Handle clients request to join back lobby once game has finished
        case HandleReqToJoinBackLobby(gameClientRef)=>
          gameClientRef ! GameClient.JoinBackLobby(lobbyObj.get, lobbyMembers.toList)
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }
}

