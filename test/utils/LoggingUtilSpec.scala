/*
 * Copyright 2022 HM Revenue & Customs
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

package utils

import org.scalatest.matchers.must.Matchers
import org.scalatestplus.play.PlaySpec
import play.api.test.FakeRequest
import uk.gov.hmrc.http.{HeaderNames, SessionKeys}

class LoggingUtilSpec extends PlaySpec with Matchers with LoggingUtil with LogCapturing {

  val testTrueClientIp = "test-true-client-ip"
  val testSessionId = "test-session-id"

  implicit val request: FakeRequest[_] = FakeRequest()
    .withHeaders((HeaderNames.trueClientIp, testTrueClientIp))
    .withSession((SessionKeys.sessionId, testSessionId))

  val emptyRequest: FakeRequest[_] = FakeRequest()

  "trueClientIp" when {

    "trueClientIp is in request" must {

      "return message including trueClientIp" in {

        val request: FakeRequest[_] = FakeRequest().withHeaders((HeaderNames.trueClientIp, testTrueClientIp))

        trueClientIp(request) mustBe Some(s"trueClientIp: $testTrueClientIp ")

      }
    }

    "trueClientIp is not in request" must {

      "return None" in {

        trueClientIp(emptyRequest) mustBe None
      }
    }
  }

  "sessionId" when {

    "sessionId is in request" must {

      "return message including sessionId" in {

        val request: FakeRequest[_] = FakeRequest().withSession((SessionKeys.sessionId, testSessionId))

        sessionId(request) mustBe Some(s"sessionId: $testSessionId ")

      }
    }

    "sessionId is not in request" must {

      "return None" in {

        sessionId(emptyRequest) mustBe None
      }
    }
  }

  "identifiers" when {

    "trueClientIp is in request" must {

      "return message including trueClientIp" in {

        val request: FakeRequest[_] = FakeRequest().withHeaders((HeaderNames.trueClientIp, testTrueClientIp))

        identifiers(request) mustBe s"trueClientIp: $testTrueClientIp "

      }
    }

    "sessionId is in request" must {

      "return message including sessionId" in {

        val request: FakeRequest[_] = FakeRequest().withSession((SessionKeys.sessionId, testSessionId))

        identifiers(request) mustBe s"sessionId: $testSessionId "

      }
    }

    "all identifiers are in request" must {

      "return message including sessionId" in {

        identifiers(request) mustBe s"trueClientIp: $testTrueClientIp sessionId: $testSessionId "

      }
    }

    "no identifiers are in request" must {

      "return None" in {

        identifiers(emptyRequest) mustBe ""
      }
    }
  }

  "identifiers are added to logs" when {

    "log level is INFO" in {
      withCaptureOfLoggingFrom(logger) { capturedLogs =>

        infoLog("test INFO log message")

        capturedLogs.head.getMessage mustBe s"test INFO log message (trueClientIp: $testTrueClientIp sessionId: $testSessionId )"
      }
    }

    "log level is WARN" in {
      withCaptureOfLoggingFrom(logger) { capturedLogs =>

        warnLog("test WARN log message")

        capturedLogs.head.getMessage mustBe s"test WARN log message (trueClientIp: $testTrueClientIp sessionId: $testSessionId )"
      }
    }

    "log level is WARN and throwable is provided" in {
      withCaptureOfLoggingFrom(logger) { capturedLogs =>

        val exception = new Exception("ERROR")

        warnLog("test WARN log message", exception)

        capturedLogs.head.getMessage mustBe s"test WARN log message (trueClientIp: $testTrueClientIp sessionId: $testSessionId )"
      }
    }

    "log level is ERROR" in {
      withCaptureOfLoggingFrom(logger) { capturedLogs =>

        errorLog("test ERROR log message")

        capturedLogs.head.getMessage mustBe s"test ERROR log message (trueClientIp: $testTrueClientIp sessionId: $testSessionId )"
      }
    }

    "log level is ERROR and throwable is provided" in {
      withCaptureOfLoggingFrom(logger) { capturedLogs =>

        val exception = new Exception("ERROR")

        errorLog("test ERROR log message", exception)

        capturedLogs.head.getMessage mustBe s"test ERROR log message (trueClientIp: $testTrueClientIp sessionId: $testSessionId )"
      }
    }
  }
}
