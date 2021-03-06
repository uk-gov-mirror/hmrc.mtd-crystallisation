/*
 * Copyright 2021 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package v2.connectors.httpparsers

import play.api.http.Status._
import play.api.libs.json.{ JsValue, Json, Reads }
import support.UnitSpec
import uk.gov.hmrc.http.{ HttpReads, HttpResponse }
import v2.connectors.DesConnectorOutcome
import v2.models.errors._
import v2.models.outcomes.DesResponse

// WLOG if Reads tested elsewhere
case class DummyModel(data: String)

object DummyModel {
  implicit val reads: Reads[DummyModel] = Json.reads
}

class StandardDesHttpParserSpec extends UnitSpec {

  val method = "POST"
  val url    = "test-url"

  val correlationId = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"

  import v2.connectors.httpparsers.StandardDesHttpParser._

  val httpReads: HttpReads[DesConnectorOutcome[Unit]] = implicitly

  val data                     = "someData"
  val desExpectedJson: JsValue = Json.obj("data" -> data)

  val desModel    = DummyModel(data)
  val desResponse = DesResponse(correlationId, desModel)

  "The generic HTTP parser" when {
    val httpReads: HttpReads[DesConnectorOutcome[DummyModel]] = implicitly

    "return a Right DES response containing the model object if the response json corresponds to a model object" in {
      val httpResponse = HttpResponse(OK, desExpectedJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

      httpReads.read(method, url, httpResponse) shouldBe Right(desResponse)
    }

    "return an outbound error if a model object cannot be read from the response json" in {
      val badFieldTypeJson: JsValue = Json.obj("incomeSourceId" -> 1234, "incomeSourceName" -> 1234)
      val httpResponse              = HttpResponse(OK, badFieldTypeJson.toString(), Map("CorrelationId" -> Seq(correlationId)))
      val expected                  = DesResponse(correlationId, OutboundError(DownstreamError))

      httpReads.read(method, url, httpResponse) shouldBe Left(expected)
    }

    handleErrorsCorrectly(httpReads)
    handleInternalErrorsCorrectly(httpReads)
    handleUnexpectedResponse(httpReads)
  }

  "The generic HTTP parser for empty response" when {
    val httpReads: HttpReads[DesConnectorOutcome[Unit]] = implicitly

    "receiving a 204 response" should {
      "return a Right DesResponse with the correct correlationId and no responseData" in {
        val httpResponse = HttpResponse(NO_CONTENT, "", headers = Map("CorrelationId" -> Seq(correlationId)))

        httpReads.read(method, url, httpResponse) shouldBe Right(DesResponse(correlationId, ()))
      }
    }

    handleErrorsCorrectly(httpReads)
    handleInternalErrorsCorrectly(httpReads)
    handleUnexpectedResponse(httpReads)
  }

  val singleErrorJson = Json.parse(
    """
      |{
      |   "code": "CODE",
      |   "reason": "MESSAGE"
      |}
    """.stripMargin
  )

  val multipleErrorsJson = Json.parse(
    """
      |{
      |   "failures": [
      |       {
      |           "code": "CODE 1",
      |           "reason": "MESSAGE 1"
      |       },
      |       {
      |           "code": "CODE 2",
      |           "reason": "MESSAGE 2"
      |       }
      |   ]
      |}
    """.stripMargin
  )

  val malformedErrorJson = Json.parse(
    """
      |{
      |   "coed": "CODE",
      |   "resaon": "MESSAGE"
      |}
    """.stripMargin
  )

  private def handleErrorsCorrectly[A](httpReads: HttpReads[DesConnectorOutcome[A]]): Unit =
    Seq(BAD_REQUEST, NOT_FOUND, FORBIDDEN, CONFLICT).foreach(
      responseCode =>
        s"receiving a $responseCode response" should {
          "be able to parse a single error" in {
            val httpResponse = HttpResponse(responseCode, singleErrorJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

            httpReads.read(method, url, httpResponse) shouldBe Left(DesResponse(correlationId, SingleError(Error("CODE", "MESSAGE"))))
          }

          "be able to parse multiple errors" in {
            val httpResponse = HttpResponse(responseCode, multipleErrorsJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

            httpReads.read(method, url, httpResponse) shouldBe {
              Left(DesResponse(correlationId, MultipleErrors(Seq(Error("CODE 1", "MESSAGE 1"), Error("CODE 2", "MESSAGE 2")))))
            }
          }

          "return an outbound error when the error returned doesn't match the Error model" in {
            val httpResponse = HttpResponse(responseCode, malformedErrorJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

            httpReads.read(method, url, httpResponse) shouldBe Left(DesResponse(correlationId, OutboundError(DownstreamError)))
          }
      }
    )

  private def handleInternalErrorsCorrectly[A](httpReads: HttpReads[DesConnectorOutcome[A]]): Unit =
    Seq(INTERNAL_SERVER_ERROR, SERVICE_UNAVAILABLE).foreach(responseCode =>
      s"receiving a $responseCode response" should {
        "return an outbound error when the error returned matches the Error model" in {
          val httpResponse = HttpResponse(responseCode, singleErrorJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

          httpReads.read(method, url, httpResponse) shouldBe Left(DesResponse(correlationId, OutboundError(DownstreamError)))
        }

        "return an outbound error when the error returned doesn't match the Error model" in {
          val httpResponse = HttpResponse(responseCode, malformedErrorJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

          httpReads.read(method, url, httpResponse) shouldBe Left(DesResponse(correlationId, OutboundError(DownstreamError)))
        }
    })

  private def handleUnexpectedResponse[A](httpReads: HttpReads[DesConnectorOutcome[A]]): Unit =
    "receiving an unexpected response" should {
      val responseCode = 499
      "return an outbound error when the error returned matches the Error model" in {
        val httpResponse = HttpResponse(responseCode, singleErrorJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

        httpReads.read(method, url, httpResponse) shouldBe Left(DesResponse(correlationId, OutboundError(DownstreamError)))
      }

      "return an outbound error when the error returned doesn't match the Error model" in {
        val httpResponse = HttpResponse(responseCode, malformedErrorJson.toString(), Map("CorrelationId" -> Seq(correlationId)))

        httpReads.read(method, url, httpResponse) shouldBe Left(DesResponse(correlationId, OutboundError(DownstreamError)))
      }
    }
}
