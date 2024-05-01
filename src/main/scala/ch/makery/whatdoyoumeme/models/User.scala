package ch.makery.whatdoyoumeme.models

import akka.actor.typed.ActorRef
import ch.makery.whatdoyoumeme.ClientGuardian
import com.hep88.protocol.JsonSerializable

case class User(userNameS: String, ref: ActorRef[ClientGuardian.Command]) extends JsonSerializable {

  private val userName = userNameS

  def getPlayerName(): String = {
    userName
  }

}
