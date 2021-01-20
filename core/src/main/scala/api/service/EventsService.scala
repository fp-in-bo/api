package api.service

import api.model.Event
import api.model.response.EventError
import cats.effect.IO

trait EventsService {
  def getEvent(id: Int): IO[Either[EventError, Event]]
  def getEvents(exclusiveStartId: Option[Int] = None, limit: Int = 20): IO[(Option[String], List[Event])]
}
