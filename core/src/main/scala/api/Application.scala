package api

import cats.effect.{ ExitCode, IO, IOApp }
import cats.implicits.catsSyntaxEitherId
import org.http4s.implicits.http4sKleisliResponseSyntaxOptionT
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import sttp.tapir.server.http4s.RichHttp4sHttpEndpoint
import sttp.tapir.{ endpoint, query, stringBody }

import scala.concurrent.ExecutionContext.global

object Application extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    val tapirEndpoint  = endpoint.get.in("hello").in(query[String]("name")).out(stringBody)
    val http4sEndpoint = tapirEndpoint.toRoutes(name => IO(s"Hello, $name!".asRight[Unit]))
    val router         = Router(
      "/" -> http4sEndpoint
    ).orNotFound

    val loggedApp = Logger.httpApp(logHeaders = true, logBody = true)(router)

    BlazeServerBuilder[IO](global)
      .bindHttp(80, "0.0.0.0")
      .withHttpApp(loggedApp)
      .serve
      .compile
      .drain
      .as(ExitCode.Success)
  }
}
