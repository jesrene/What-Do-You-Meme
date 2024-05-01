package ch.makery.whatdoyoumeme.models

class PromptCard (promptIDS: Int, text: String) extends Serializable{

  private val promptID = promptIDS
  private val promptText = text

  def getPromptID(): Int ={
    promptID
  }

  def getPromptText(): String = {
    promptText
  }
}
