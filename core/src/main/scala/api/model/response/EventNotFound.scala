package api.model.response

import cats.implicits.toFunctorOps
import io.circe.{ Decoder, Encoder }
import io.circe.syntax.EncoderOps

sealed trait EventError
case class EventNotFound(message: String)     extends EventError
case class UnknownEventError(message: String) extends EventError

object EventError {

  import io.circe.generic.auto._

  implicit val encodeBusInfoResponse: Encoder[EventError] = Encoder.instance {
    case eventNotFound @ EventNotFound(_) => eventNotFound.asJson
    case failure @ UnknownEventError(_)   => failure.asJson
  }

  implicit val decodeBusInfoResponse: Decoder[EventError] =
    List[Decoder[EventError]](
      Decoder[EventNotFound].widen,
      Decoder[UnknownEventError].widen
    ).reduceLeft(_ or _)
}
