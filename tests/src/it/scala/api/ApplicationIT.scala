package api

import cats.effect.testing.scalatest.AsyncIOSpec
import cats.effect.{ Blocker, IO, Resource }
import cats.implicits._
import com.amazonaws.auth.{ AWSStaticCredentialsProvider, BasicAWSCredentials }
import com.amazonaws.client.builder.AwsClientBuilder.EndpointConfiguration
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder
import com.amazonaws.services.dynamodbv2.model._
import com.dimafeng.testcontainers.{ ForAllTestContainer, GenericContainer, MultipleContainers }
import io.circe.Json
import org.http4s.circe.jsonDecoder
import org.http4s.client.JavaNetClientBuilder
import org.http4s.headers.Accept
import org.http4s.util.CaseInsensitiveString
import org.http4s.{ DecodeFailure, Headers, MediaType, Request, Uri, _ }
import org.scalatest.freespec.AsyncFreeSpec
import org.scalatest.matchers.should.Matchers
import org.testcontainers.containers.Network
import org.testcontainers.containers.output.OutputFrame
import org.testcontainers.containers.wait.strategy.Wait

import scala.jdk.CollectionConverters._

class ApplicationIT extends AsyncFreeSpec with ForAllTestContainer with Matchers with AsyncIOSpec {

  val blocker: Blocker = Blocker.liftExecutionContext(executionContext)

  val network: Network  = Network.newNetwork
  val networkAlias      = "dynamo"
  val exposedDynamoPort = 8000

  lazy val dynamoContainer = GenericContainer(
    dockerImage = "amazon/dynamodb-local",
    exposedPorts = Seq(exposedDynamoPort),
    command = Seq("-jar", "DynamoDBLocal.jar", "-sharedDb", "-inMemory"),
    waitStrategy = Wait.forLogMessage(".*CorsParams:.*", 1)
  ).configure { provider =>
    provider.withLogConsumer((t: OutputFrame) => println(t.getUtf8String))
    provider.withNetwork(network)
    provider.withNetworkAliases(networkAlias)
    ()
  }

  private lazy val dunamoDbEndpoint =
    s"http://${dynamoContainer.container.getHost}:${dynamoContainer.mappedPort(exposedDynamoPort)}"

  lazy val apiContainer = GenericContainer(
    dockerImage = "fp-in-bo/tests",
    exposedPorts = Seq(80),
    env = Map(
      "DYNAMO_HOST"           -> s"http://${networkAlias}:${exposedDynamoPort}",
      "AWS_ACCESS_KEY_ID"     -> "fake",
      "AWS_SECRET_ACCESS_KEY" -> "fake"
    ),
    waitStrategy = Wait.forLogMessage(".*started at http://0.0.0.0:80/.*", 1)
  ).configure { provider =>
    provider.withLogConsumer((t: OutputFrame) => println(t.getUtf8String))
    provider.withNetwork(network)
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
          .withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials("dummy", "dummy")))
          .withEndpointConfiguration(new EndpointConfiguration(dunamoDbEndpoint, null))
          .build()
      )
    }(res => IO(res.shutdown())).use { client =>
      val createTable      = IO(
        client
          .createTable(
            List(new AttributeDefinition("id", ScalarAttributeType.N)).asJava,
            "events",
            List(new KeySchemaElement("id", KeyType.HASH)).asJava,
            new ProvisionedThroughput(5, 5)
          )
      )
      def addItem(id: Int) = IO(
        client.putItem(
          "events",
          Map(
            "id"          -> new AttributeValue().withN(id.toString),
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
        _ <- (1 to 100).map(addItem).toList.sequence
      } yield ()
    }.unsafeRunSync()

  "read all the events in a paginated fashion" in {

    case class Res(headers: Headers, body: Either[DecodeFailure, Json])

    val hhtpClient = JavaNetClientBuilder[IO](blocker).create

    def mkRequest(eventsPath: String): Request[IO] =
      Request(
        uri = Uri.unsafeFromString(s"http://${apiContainer.host}:${apiContainer.mappedPort(80)}/$eventsPath"),
        headers = Headers.of(Accept(MediaType.application.json))
      )

    def parseLink(h: Header) = {
      val strings  = h.value.split(";")
      val nextLink = strings(0)
      val rel      = strings(1)

      if (rel.contains("next")) Some(nextLink.substring(1))
      else None
    }

    def responseFor(req: Request[IO]): IO[(Res, Option[Request[IO]])] = {
      val response: IO[(Headers, Either[DecodeFailure, Json])] =
        hhtpClient.run(req).use(r => r.attemptAs[Json].value.map(j => (r.headers, j)))

      val res = response.map(t => Res(t._1, t._2))

      val link: IO[Option[String]] =
        res.map(r =>
          r.headers
            .get(CaseInsensitiveString("Link"))
            .flatMap(h => parseLink(h))
        )

      val nextRequest = link.map(_.fold[Option[Request[IO]]](None)(s => Some(mkRequest(s))))
      res.product(nextRequest)
    }

    def callsStartingFrom(
      request: Request[IO],
      start: List[(Res, Option[Request[IO]])] = List()
    ): IO[List[(Res, Option[Request[IO]])]] =
      responseFor(request).flatMap {
        case (res, Some(req)) => callsStartingFrom(req, start :+ ((res, Some(req))))
        case (_, None)        => IO(start)
      }

    val result = callsStartingFrom(mkRequest("events?limit=10")).unsafeRunSync()

    assert(result.size == 10)
  }

}
