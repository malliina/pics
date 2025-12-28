package com.malliina.pics.js

import com.malliina.http.FullUrl
import com.malliina.pics.BuildInfo
import com.malliina.values.IdToken
import io.circe.{Codec, Encoder}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.scalajs.js.Date

case class TokenRequest(app: String) derives Codec.AsObject

case class TokenResponse(token: IdToken) derives Codec.AsObject

enum Level(val name: String):
  case Info extends Level("info")
  case Warn extends Level("warn")
  case Error extends Level("warn")

object Level:
  given Encoder[Level] = Encoder.encodeString.contramap(_.name)

case class LogEvent(
  timestamp: Long,
  message: String,
  loggerName: String,
  threadName: String,
  level: Level,
  stackTrace: Option[String] = None
) derives Encoder.AsObject

object LogEvent:
  def info(message: String) =
    build(message, Level.Info)
  def error(t: Throwable) =
    build(Option(t.getMessage).getOrElse("Error."), Level.Error, stackTraceString(t))

  def build(message: String, level: Level, stackTrace: Option[String] = None) =
    LogEvent(new Date().getTime().toLong, message, "web", "js", level, stackTrace)

  private def stackTraceString(t: Throwable): Option[String] =
    val msg = Option(t.getMessage).getOrElse("Error.")
    if t.getStackTrace.isEmpty then None
    else
      val header = s"${t.getClass.getName}: $msg"
      val trace = t.getStackTrace.map(_.toString).toList
      Option((Seq(header) ++ trace).mkString("\n"))

case class LogEvents(events: Seq[LogEvent]) derives Encoder.AsObject

object LogSocket:
  val queryKey = "token"

  private def prod = LogSocket(
    FullUrl.https("logs.malliina.com", "/sources/token"),
    FullUrl.wss("logs.malliina.com", "/ws/sources/clients")
  )
  private def dev = LogSocket(
    FullUrl("http", "localhost:9001", "/sources/token"),
    FullUrl.ws("localhost:9001", "/ws/sources/clients")
  )

  def impl = if BuildInfo.isProdLog then prod else dev
  lazy val instance: LogSocket =
    val log = impl
    log.start()
    log

class LogSocket(tokenUrl: FullUrl, socketUrl: FullUrl, fallback: BaseLogger = BaseLogger.console)
  extends BaseLogger:
  private var current: BaseLogger = fallback
  def log = current

  def start() = fetchToken(tokenUrl).map: res =>
    val simple = new SimpleSocket(socketUrl.withQuery(LogSocket.queryKey -> res.token.token))
    current = SocketLogger(simple, fallback)

  private def fetchToken(from: FullUrl): Future[TokenResponse] =
    Http.post[TokenRequest, TokenResponse](from, TokenRequest("pics-web"))

  override def info(message: String): Unit = current.info(message)
  override def error(t: Throwable): Unit = current.error(t)

class SocketLogger(socket: SimpleSocket, fallback: BaseLogger) extends BaseLogger:
  override def info(message: String): Unit =
    if socket.isOpen then sendSingle(LogEvent.info(message))
    else fallback.info(message)

  override def error(t: Throwable): Unit =
    if socket.isOpen then sendSingle(LogEvent.error(t))
    else fallback.error(t)

  private def sendSingle(event: LogEvent): Unit =
    socket.send(LogEvents(Seq(event)))
