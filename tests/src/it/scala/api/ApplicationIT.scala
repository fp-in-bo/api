package api

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ IO, Resource }
import com.amazonaws.auth.EnvironmentVariableCredentialsProvider
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model._
import com.dimafeng.testcontainers.{ ForAllTestContainer, GenericContainer, MultipleContainers }
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.Wait

import scala.jdk.CollectionConverters._

class ApplicationIT extends AsyncFreeSpec with ForAllTestContainer with Matchers with AsyncIOSpec {

  lazy val dynamoContainer = GenericContainer(
    dockerImage = "amazon/dynamodb-local",
    exposedPorts = Seq(8000),
    command = Seq("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory"),
    waitStrategy = Wait.forLogMessage(".*CorsParams:.*", 1)
  ).configure { provider =>
    provider.withLogConsumer((t: OutputFrame) => println(t.getUtf8String))
    ()
  }

  private lazy val dunamoDbEndpoint = s"http://${dynamoContainer.container.getHost}:${dynamoContainer.mappedPort(8000)}"

  lazy val apiContainer = GenericContainer(
    dockerImage = "fp-in-bo/tests",
    exposedPorts = Seq(80),
    env = Map(
      "DYNAMO_HOST"           -> dunamoDbEndpoint,
      "AWS_ACCESS_KEY_ID"     -> "fake",
      "AWS_SECRET_ACCESS_KEY" -> "fake"
    ),
    waitStrategy = Wait.forLogMessage(".*started at http://0.0.0.0:80/.*", 1)
  ).configure { provider =>
    provider.withLogConsumer((t: OutputFrame) => println(t.getUtf8String))
    ()
  }

  override val container = MultipleContainers(
    dynamoContainer,
    apiContainer
  )

  override def afterStart(): Unit =
    Resource.make {
      IO(
        AmazonDynamoDBClientBuilder
          .standard()
          .withCredentials(new EnvironmentVariableCredentialsProvider())
          .withEndpointConfiguration(
            new EndpointConfiguration(dunamoDbEndpoint, null)
          )
          .build()
      )
    }(res => IO(res.shutdown)).use { client =>
      val createTable = IO(
        client
          .createTable(
            List(new AttributeDefinition("id", ScalarAttributeType.N)).asJava,
            "events",
            List(new KeySchemaElement("id", KeyType.HASH)).asJava,
            new ProvisionedThroughput(5, 5)
          )
      )

      val addItem = IO(
        client.putItem(
          "events",
          Map(
            "id"          -> new AttributeValue().withN("1"),
            "title"       -> new AttributeValue().withS("a funny title"),
            "speaker"     -> new AttributeValue().withS("a funny speaker"),
            "imageUrl"    -> new AttributeValue().withS("a funny imageUrl"),
            "videoUrl"    -> new AttributeValue().withS("a funny videoUrl"),
            "description" -> new AttributeValue().withS("a funny description")
          ).asJava
        )
      )

      for {
        _ <- createTable
        _ <- addItem
      } yield ()
    }.unsafeRunSync()

  "get list of events" in {
    Thread.sleep(20000)
    assert(dunamoDbEndpoint == System.getenv("DYNAMO_HOST"))
  }
}
