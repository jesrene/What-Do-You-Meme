package ch.makery.whatdoyoumeme.controllers

import ch.makery.whatdoyoumeme.Actor.GameClient
import ch.makery.whatdoyoumeme.Client
import ch.makery.whatdoyoumeme.models.GameClientModel
import javafx.fxml.FXML
import scalafx.event.ActionEvent
import scalafx.scene.control.Label
import scalafxml.core.macros.sfxml

@sfxml
class LeaderBoardController(@FXML var firstPlaceLabel: Label,
                            @FXML var secondPlaceLabel: Label,
                            @FXML var thirdPlaceLabel: Label) {

  // update leaderboard
  def updateLeaderboard(players: List[GameClientModel]): Unit = {

    // Sort players by hand length
    val sortedPlayers = players.sortBy(_.showHand().length)

    // Show top 2 players
    firstPlaceLabel.text = s"1st Place: Player ${sortedPlayers(0).getPlayerName()}"
    secondPlaceLabel.text = s"2nd Place: Player ${sortedPlayers(1).getPlayerName()}"

    // If there is more than 3 players in game, show also third place
    if(players.size >2){

      // Set text
      thirdPlaceLabel.text = s"3rd Place: Player ${sortedPlayers(2).getPlayerName()}"

    }else{

      // Make label invisible
      thirdPlaceLabel.visible = false

    }
  }

  // Join back lobby
  def handleLobby(action: ActionEvent): Unit = {
    Client.gameClientActor.foreach(_ ! GameClient.RequestToJoinBackLobby())
  }

  // Quit lobby
  def handleQuit(action: ActionEvent): Unit = {

    Client.gameClientActor.foreach(_ ! GameClient.QuitLobby)

    // Show main menu
    println("Redirecting to main menu...")
    Client.showMainMenuScene()
  }

}