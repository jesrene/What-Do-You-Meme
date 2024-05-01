package ch.makery.whatdoyoumeme

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem
import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.receptionist.{Receptionist, ServiceKey}
import akka.actor.typed.scaladsl.adapter._
import akka.management.cluster.bootstrap.ClusterBootstrap
import akka.management.scaladsl.AkkaManagement
import akka.discovery.Discovery
import akka.cluster.typed._
import com.typesafe.config.ConfigFactory
import scalafx.collections.ObservableHashSet
import ch.makery.whatdoyoumeme.AKKA.MyConfiguration
import ch.makery.whatdoyoumeme.ClientGuardian
import ch.makery.whatdoyoumeme.Actor.{LobbyServer}
import ch.makery.whatdoyoumeme.models.{User, ServerLobbyItem}

import scala.util.control.Breaks._

object Server {
  sealed trait Command

  // ===== Protocols =====
  case class JoinServer(name: String, clientRef: ActorRef[ClientGuardian.Command]) extends Command
  case class LeaveServer(name: String, clientRef: ActorRef[ClientGuardian.Command]) extends Command

  case class AddNewLobby(lobbyIDGiven: String, lobbyServerRef: ActorRef[LobbyServer.Command], lobbyMasterName: String) extends Command
  case class RemoveLobby(lobbyItem: String) extends Command
  case class updateLobbyNumber(lobbyIDGiven: String, number: Int) extends Command
  case class updateLobbyGameStatus(lobbyIDGiven: String, status: Boolean) extends Command
  case class ShowAllLobbies(clientBackendRef: ActorRef[ClientGuardian.Command]) extends Command
  // ===== Protocols =====

  //ServiceKey. Setting up server service with name "Server"
  val ServerKey: ServiceKey[Server.Command] = ServiceKey("Server")

  // ===== Properties =====
  val serverMembers = new ObservableHashSet[User]()
  val lobbies = new ObservableHashSet[ServerLobbyItem]()
  // ===== Properties =====

  // every lobby changes notify all clients of existing lobbies
  lobbies.onChange { (ns, _) =>
    serverMembers.foreach(_.ref ! ClientGuardian.ReceiveAllLobbies(lobbies.toList))
  }

  // every new or old client changes notify all current clients
  serverMembers.onChange { (ns, _) =>
    serverMembers.foreach(_.ref ! ClientGuardian.ReceiveAllClients(serverMembers.toList))
  }

  def apply(): Behavior[Server.Command] = Behaviors.setup { context =>
    // Register itself with ServerKey. Telling the receptionist this actor ref is offering this service.
    context.system.receptionist ! Receptionist.Register(ServerKey, context.self)

    // Initial behaviour setup, and only behaviour to be used
    Behaviors.receiveMessage { message =>
      message match {

        // Handle new clients joining server
        case JoinServer(name, clientBackendRef) =>
          Server.serverMembers += User(name, clientBackendRef)
          println("JOINED NEW DEVICE: " + name)

          clientBackendRef ! ClientGuardian.JoinedServer(Server.serverMembers.toList) // message sent to Actor referenced by ref. Joined
          Behaviors.same

        // Handle old clients leaving server
        case LeaveServer(name, clientRef) =>
          serverMembers -= User(name, clientRef)
          Behaviors.same

        // Handle new lobbies created
        case AddNewLobby(lobbyIDGiven, lobbyServerRef, name) =>
          Server.lobbies += ServerLobbyItem(lobbyIDGiven, lobbyServerRef, name)
          println(s"Lobby Added: ${Server.lobbies}")
          Behaviors.same

        // Handle old lobbies removed
        case RemoveLobby(lobbyIDGiven) =>
          lobbies -= lobbies.find(_.LobbyIDS == lobbyIDGiven).orNull
          println(s"Lobby removed: ${Server.lobbies}")
          Behaviors.same

        // Handle client request to show all current available lobbies
        case ShowAllLobbies(clientBackendRef) =>
          clientBackendRef ! ClientGuardian.ReceiveAllLobbies(Server.lobbies.toList)
          Behaviors.same

        // Update the current number of players of a client's lobby
        case updateLobbyNumber(lobbyIDGiven, number) =>
          breakable{
            for (lobby <- lobbies) {
              if (lobby.LobbyIDS == lobbyIDGiven) {
                lobby.changePlayerCount(number)
                break
              }
            }
          }
          Behaviors.same

        // Update the current game status of a client's lobby
        case updateLobbyGameStatus(lobbyIDGiven, status) =>
          breakable {
            for (lobby <- lobbies) {
              if (lobby.LobbyIDS == lobbyIDGiven) {
                lobby.inGameStatus = status
                break
              }
            }
          }
          Behaviors.same

        case _ =>
          Behaviors.unhandled
      }
    }
  }
}

// Object ServerApp is to be ran to start the server actor system / cluster / server actor
object ServerApp extends App {
  // Create server actor system
  val mainSystem = akka.actor.ActorSystem("MainSystem", MyConfiguration.askDevConfigServer())

  // Create server actor inside actor system
  mainSystem.spawn(Server(), "Server")

  // Create own cluster with its actor system inside
  val typedSystem: ActorSystem[Nothing] = mainSystem.toTyped
  val cluster = Cluster(typedSystem)

  cluster.manager ! Join(cluster.selfMember.address)
  
  AkkaManagement(mainSystem).start()
  ClusterBootstrap(mainSystem).start()
}