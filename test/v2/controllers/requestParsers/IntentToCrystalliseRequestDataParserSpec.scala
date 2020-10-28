/*
 * Copyright 2020 HM Revenue & Customs
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

package v2.controllers.requestParsers

import support.UnitSpec
import uk.gov.hmrc.domain.Nino
import v2.mocks.validators.MockIntentToCrystalliseValidator
import v2.models.errors._
import v2.models.requestData.{DesTaxYear, IntentToCrystalliseRawData, IntentToCrystalliseRequestData}

class IntentToCrystalliseRequestDataParserSpec extends UnitSpec {
  val nino = "AA123456B"
  val taxYear = "2017-18"
  val calcId = "someCalcId"
  implicit val correlationId: String = "a1e8057e-fbbc-47a8-a8b4-78d9f015c253"

  val inputData =
    IntentToCrystalliseRawData(nino, taxYear)

  trait Test extends MockIntentToCrystalliseValidator {
    lazy val parser = new IntentToCrystalliseRequestDataParser(mockValidator)
  }

  "parse" should {

    "return a retrieve intent to crystallise request object" when {
      "valid request data is supplied" in new Test {
        MockIntentToCrystalliseValidator.validate(inputData).returns(Nil)

        parser.parseRequest(inputData) shouldBe
          Right(IntentToCrystalliseRequestData(Nino(nino), DesTaxYear("2018")))
      }
    }

    "return an ErrorWrapper" when {

      "a single validation error occurs" in new Test {
        MockIntentToCrystalliseValidator.validate(inputData)
          .returns(List(NinoFormatError))

        parser.parseRequest(inputData) shouldBe
          Left(ErrorWrapper(correlationId, NinoFormatError, None))
      }

      "multiple validation errors occur" in new Test {
        MockIntentToCrystalliseValidator.validate(inputData)
          .returns(List(NinoFormatError, TaxYearFormatError))

        parser.parseRequest(inputData) shouldBe
          Left(ErrorWrapper(correlationId, BadRequestError, Some(Seq(NinoFormatError, TaxYearFormatError))))
      }
    }
  }
}
