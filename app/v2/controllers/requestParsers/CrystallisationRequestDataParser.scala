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

package v2.controllers.requestParsers

import javax.inject.Inject
import uk.gov.hmrc.domain.Nino
import play.api.Logger.logger
import v2.controllers.requestParsers.validators.CrystallisationValidator
import v2.models.domain.CrystallisationRequest
import v2.models.errors.{BadRequestError, ErrorWrapper}
import v2.models.requestData.{CrystallisationRawData, CrystallisationRequestData, DesTaxYear}

class CrystallisationRequestDataParser @Inject()(validator: CrystallisationValidator) {

  def parseRequest(data: CrystallisationRawData)(implicit correlationId: String): Either[ErrorWrapper, CrystallisationRequestData] = {
    validator.validate(data) match {
      case Nil =>
        logger.info(
          "[RequestParser][parseRequest] " +
            s"Validation successful for the request with CorrelationId: $correlationId")
        Right(CrystallisationRequestData(Nino(data.nino), DesTaxYear.fromMtd(data.taxYear), data.body.json.as[CrystallisationRequest]))
      case err :: Nil =>
        logger.warn(
          "[RequestParser][parseRequest] " +
            s"Validation failed with ${err.code} error for the request with CorrelationId: $correlationId")
        Left(ErrorWrapper(correlationId, err, None))
      case errs =>
        logger.warn(
          "[RequestParser][parseRequest] " +
            s"Validation failed with ${errs.map(_.code).mkString(",")} error for the request with CorrelationId: $correlationId")
        Left(ErrorWrapper(correlationId, BadRequestError, Some(errs)))
    }
  }

}
