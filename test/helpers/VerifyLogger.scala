/*
 * Copyright 2018 HM Revenue & Customs
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

package helpers

import org.mockito.Mockito.verify
import org.scalatest.mockito.MockitoSugar
import play.api.LoggerLike

trait VerifyLogger extends MockitoSugar {
  val mockLogger = mock[LoggerLike]

  def verifyWarningLogging(expectedMessage: String) = verifyLogging("warn", expectedMessage)

  def verifyErrorLogging(expectedMessage: String) = verifyLogging("error", expectedMessage)

  private def verifyLogging(methodName: String, expectedMessage: String) = {
    classOf[LoggerLike].getMethod(methodName, classOf[() => _]).invoke(
      verify(mockLogger),
      new (() => String) {
        def apply = null

        override def equals(o: Any): Boolean = {
          val actual = o.asInstanceOf[() => String].apply()
          if (expectedMessage == actual) {
            true
          } else {
            throw new RuntimeException(s"""Expected and actual messages didn't match - Expected = "$expectedMessage", Actual = "$actual""")
          }
        }
      }
    )
  }

}
