import sbt.taskKey

object StartKeys {
  val start = taskKey[Unit]("Starts the project")
  val startInc = taskKey[Unit]("Starts the project, conditionally")
}
