package ch.makery.whatdoyoumeme

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
import Server._
import ch.makery.whatdoyoumeme.Actor.{GameClient, LobbyServer}
import ch.makery.whatdoyoumeme.models.{Lobby, User, ServerLobbyItem}

object ClientGuardian {
  sealed trait Command

  // === Joining Protocols ===
  case class Start(name: String) extends Command
  case class StartJoin() extends Command
  final case object FindTheServer extends Command
  private case class ListingResponse(listing: Receptionist.Listing) extends Command
  case class JoinedServer(list: Iterable[User]) extends Command
  // === Mandatory Protocols ===

  // === Custom Protocols ===
  case class CreateLobby(lobbyIDGiven: String, lobbyMasterRef: ActorRef[LobbyServer.Command], lobbyMasterName: String) extends Command
  case class ReceiveAllLobbies(list: Iterable[ServerLobbyItem]) extends Command
  case class ReceiveAllClients(list: Iterable[User]) extends Command
  case class GetAllLobbies() extends Command
  case class UpdateClientLobbyNumber(lobbyID: String, numb: Int) extends Command
  case class UpdateClientLobbyStatus(lobbyID: String, stauts: Boolean) extends Command
  case class RemoveLobby(lobbyID: String) extends Command
  // === Custom Protocols ===

  // === Properties ===
  val clientMembers = new ObservableHashSet[User]()
  var lobbies = new ObservableHashSet[ServerLobbyItem]()
  var defaultBehavior: Option[Behavior[ClientGuardian.Command]] = None
  var serverOpt: Option[ActorRef[Server.Command]] = None
  var clientName: Option[String] = None
  // === Properties ===

  // When server AS detects changes to lobbies, will tell every ClientGuardian new lobbies and update
  // their mainMenuController to update new changes to lobby available UI
  lobbies.onChange { (ns, _) =>
    Platform.runLater {
      if(lobbies != null){
        Client.mainMenuController.addLobbyToUI(lobbies)
      }
    }
  }

  def apply(): Behavior[ClientGuardian.Command] = Behaviors.setup { context =>

      // The translator ActorRef between Client and Receptionist, returns a ClientBackend.ListingResponse message
      val listingAdapter: ActorRef[Receptionist.Listing] =
        context.messageAdapter { listing =>
          println(s"listingAdapter:listing: ${listing.toString}")
          ClientGuardian.ListingResponse(listing)
        }

      // Ask receptionist to find subscriptions available for actors with ServerKey
      context.system.receptionist ! Receptionist.Subscribe(Server.ServerKey, listingAdapter)

      // Initial behaviour setup, and only behaviour to be used stored in defaultBehaviour
      defaultBehavior = Some(Behaviors.receiveMessage { message =>
        message match {
          // Initialize Actor
          case Start(name) =>
            clientName = Option(name)
            context.self ! FindTheServer
            Behaviors.same

          // Get Client Actor System receptionist to find specific server via given ServerKey. is given listingAdapter Actor
          // to reply message back to ActorRef which returns a ClientBackend.ListingResponse message type to Client Ref
          case FindTheServer =>
            context.system.receptionist !
              Receptionist.Find(Server.ServerKey, listingAdapter)
            Behaviors.same

          // Get the actor systems listed as ServerKey, get the last one out of all the listing found by receptionist
          case ListingResponse(Server.ServerKey.Listing(listings)) =>
            val xs: Set[ActorRef[Server.Command]] = listings
            for (x <- xs) {
              println("Found Server Provider AS")
              serverOpt = Some(x)
            }
            // Once Server Actor System is found, join server actor
            context.self ! StartJoin()
            Behaviors.same

          // Get the server found from listing response and actually join it
          case StartJoin() =>
            println("Joining Server Now")
            serverOpt.map(_ ! Server.JoinServer(clientName.get, context.self))
            Behaviors.same

          // Receive back message from server that confirms joining
          case JoinedServer(x) =>
            println("Successfully joined server")
            clientMembers.clear()
            clientMembers ++= x
            Behaviors.same

          // tell server to recognise new lobby created
          case CreateLobby(lobbyIDGiven, lobbyMasterRef, lobbyMasterName) =>
            serverOpt.map(_ ! Server.AddNewLobby(lobbyIDGiven, lobbyMasterRef, lobbyMasterName))
            Behaviors.same

          // Request all current existing lobbies from server
          case GetAllLobbies() =>
            serverOpt.map(_ ! Server.ShowAllLobbies(context.self))
            Behaviors.same

          // Get all current existing lobbies from server
          case ReceiveAllLobbies(lobbiesReceived) =>
            lobbies.clear()
            lobbies ++= lobbiesReceived
            Behaviors.same

          // Get all current existing clients from server
          case ReceiveAllClients(clientsReceived) =>
            clientMembers.clear()
            clientMembers ++= clientsReceived
            Behaviors.same

          // Update created LobbyServer number of players to let server keep track of
          case UpdateClientLobbyNumber(lobbyID, numb) =>
            serverOpt.map(_ ! Server.updateLobbyNumber(lobbyID, numb))
            Behaviors.same

          // Update created LobbyServer game status to let server keep track of
          case UpdateClientLobbyStatus(lobbyID, status) =>
            serverOpt.map(_ ! Server.updateLobbyGameStatus(lobbyID, status))
            Behaviors.same

          // Remove lobby server created by client itself
          case RemoveLobby(lobbyID) =>
            println(s"Removing created lobby with ID: $lobbyID")
            serverOpt.map(_ ! Server.RemoveLobby(lobbyID))
            Client.lobbyServerActor = None // set pointer to null
            Behaviors.same

          case _ =>
            Behaviors.unhandled
        }
      })

      defaultBehavior.get
    }
}
