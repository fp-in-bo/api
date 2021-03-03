package api.service

import api.model.Event
import api.model.response.{ EventError, EventNotFound }
import cats.effect.{ IO, Resource }
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.model.{ AttributeValue, QueryRequest }
import com.amazonaws.services.dynamodbv2.{ AmazonDynamoDB, AmazonDynamoDBClientBuilder }
import cats.implicits._

import scala.jdk.CollectionConverters._
import scala.util.Try

class DynamoEventsService(private val client: AmazonDynamoDB) extends EventsService {

  override def getEvent(id: Int): IO[Either[EventError, Event]] = {
    val values     = Map(":idValue" -> new AttributeValue().withN(String.valueOf(id)))
    val attributes = Map("#idKey" -> "id")

    val request = new QueryRequest("events")
      .withKeyConditionExpression("#idKey = :idValue")
      .withExpressionAttributeNames(attributes.asJava)
      .withExpressionAttributeValues(values.asJava)

    IO {
      val result = client.query(request)
      result.getItems.asScala.toList.headOption
        .map(v =>
          Event(
            v.get("id").getN.toInt,
            v.get("title").getS,
            v.get("speaker").getS,
            v.get("imageUrl").getS,
            v.get("videoUrl").getS,
            v.get("description").getS
          )
        )
        .fold(EventNotFound("").asLeft[Event])(e => e.asRight[EventNotFound])
    }
  }

  override def getEvents(exclusiveStartId: Option[Int], limit: Int): IO[(Option[String], List[Event])] = {
    val values     = Map(":communityValue" -> new AttributeValue().withS("fpinbo"))
    val attributes = Map("#communityKey" -> "community")

    val request = exclusiveStartId.fold {

      new QueryRequest("events")
        .withKeyConditionExpression("#communityKey = :communityValue")
        .withExpressionAttributeNames(attributes.asJava)
        .withExpressionAttributeValues(values.asJava)
    }(key =>
      new QueryRequest("events")
        .withKeyConditionExpression("#communityKey = :communityValue")
        .withExpressionAttributeNames(attributes.asJava)
        .withExpressionAttributeValues(values.asJava)
        .withExclusiveStartKey(
          Map(
            "community" -> new AttributeValue().withS("fpinbo"),
            "id"        -> new AttributeValue().withN(key.toString)
          ).asJava
        )
    ).withLimit(limit)

    IO {
      val result = client.query(request)
      (
        Option(result.getLastEvaluatedKey).map(_.get("id")).map(_.getN),
        result.getItems.asScala.toList.map(v =>
          Event(
            v.get("id").getN.toInt,
            v.get("title").getS,
            v.get("speaker").getS,
            v.get("imageUrl").getS,
            v.get("videoUrl").getS,
            v.get("description").getS
          )
        )
      )
    }
  }
}

object DynamoEventsService {

  def make = Resource.make(
    IO(
      new DynamoEventsService(
        Try(sys.env("DYNAMO_HOST"))
          .fold(
            _ => AmazonDynamoDBClientBuilder.defaultClient(),
            host =>
              AmazonDynamoDBClientBuilder
                .standard()
                .withCredentials(new EnvironmentVariableCredentialsProvider())
                .withEndpointConfiguration(
                  new EndpointConfiguration(host, null)
                )
                .build()
          )
      )
    )
  )((db: DynamoEventsService) => IO(db.client.shutdown()))
}
