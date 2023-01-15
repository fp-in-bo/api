package api

import api.service.DynamoEventsService
import cats.data.Kleisli
import cats.effect.{ ExitCode, IO, IOApp, Resource }
import cats.implicits._
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.server.middleware.Logger
import org.http4s.{ Request, Response }

import scala.concurrent.ExecutionContext.global

object Application extends IOApp {

  override def run(args: List[String]): IO[ExitCode] = {

    val router = for {
      service: DynamoEventsService <- DynamoEventsService.make
      endpoints                    <- Resource.eval(IO(Http4sEndpoint(service)))
    } yield Router(
      "/" -> (endpoints.events <+> endpoints.event)
    ).orNotFound

    router.use { r: Kleisli[IO, Request[IO], Response[IO]] =>
      val loggedApp = Logger.httpApp(logHeaders = true, logBody = true)(r)

      BlazeServerBuilder[IO](global)
        .bindHttp(80, "0.0.0.0")
        .withHttpApp(loggedApp)
        .serve
        .compile
        .drain
    }.as(ExitCode.Success)
  }
}
