package api

import cats.effect.{ ContextShift, IO, Timer }
import org.http4s.HttpRoutes
import sttp.tapir.server.http4s.RichHttp4sHttpEndpoint

class Http4sEndpoint(eventsService: EventsService)(implicit
  cs: ContextShift[IO],
  timer: Timer[IO]
) {
  val eventsHttp4s: HttpRoutes[IO] = Endpoints.events.toRoutes(_ => eventsService.getEvents)
}
