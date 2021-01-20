package api

import api.model.Event
import api.model.response.{ EventError, EventNotFound, UnknownEventError }
import sttp.tapir._
import sttp.tapir.json.circe.jsonBody
import io.circe.generic.auto._
import sttp.model.StatusCode
import sttp.tapir.generic.auto.schemaForCaseClass

object Endpoints {

  private val baseEndpoint: Endpoint[Unit, Unit, Unit, Any] = endpoint
    .in("events")

  val events: Endpoint[(Option[Int], Int), EventError, (String, List[Event]), Any] =
    baseEndpoint
      .in(query[Option[Int]]("startFrom"))
      .in(query[Int]("limit"))
      .out(header[String]("Link"))
      .out(jsonBody[List[Event]])
      .errorOut(jsonBody[EventError].description("unknown"))

  val event: Endpoint[Int, EventError, Event, Any] = baseEndpoint
    .in(path[Int]("id"))
    .out(jsonBody[Event])
    .errorOut(
      oneOf[EventError](
        statusMapping(StatusCode.NotFound, jsonBody[EventNotFound]),
        statusDefaultMapping(jsonBody[UnknownEventError].description("unknown"))
      )
    )
}
