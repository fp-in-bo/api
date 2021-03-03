package api

import api.model.response.{ EventError, UnknownEventError }
import api.service.EventsService
import cats.effect.{ ContextShift, IO, Timer }
import cats.implicits._
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.Http4sServerInterpreter

class Http4sEndpoint(eventsService: EventsService)(implicit
  cs: ContextShift[IO],
  timer: Timer[IO]
) {

  val events: HttpRoutes[IO] =
    Http4sServerInterpreter.toRoutes(Endpoints.events) { input: (Option[Int], Int) =>
      eventsService
        .getEvents(input._1, input._2)
        .map { case (token, events) =>
          val headerLinkValue = token.fold(
            s"""<events?limit=${input._2}; rel="last">"""
          )(t => s"""<events?startFrom=$t&limit=${input._2}; rel="next">""")
          (headerLinkValue, events).asRight[EventError]
        }
        .handleErrorWith(t => IO(UnknownEventError(t.getMessage).asLeft))
    }

  val event: HttpRoutes[IO] =
    Http4sServerInterpreter.toRoutes(Endpoints.event) { id: Int => eventsService.getEvent(id) }
}

object Http4sEndpoint {

  def apply(eventsService: EventsService)(implicit cs: ContextShift[IO], timer: Timer[IO]): Http4sEndpoint =
    new Http4sEndpoint(eventsService)
}
