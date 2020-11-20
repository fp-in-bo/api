package api

import api.model.Event
import api.model.response.EventError
import cats.effect.IO

trait EventsService {

  def getEvent(id: Int): IO[Either[EventError, Event]]
  def getEvents: IO[Either[EventError, List[Event]]]
}
