package support

import org.joda.time.DateTime
import play.api.Application
import play.api.libs.crypto.CookieSigner
import uk.gov.hmrc.crypto.{CompositeSymmetricCrypto, PlainText}

import java.net.URLEncoder

object SessionCookieBaker {
  private val cookieKey = "gvBoGdgzqG1AarzF1LY0zQ=="

  def cookieValue(mdtpSessionData: Map[String, String],
                  optConfiguredTime: Option[DateTime])(implicit application: Application): String = {
    val cookieSigner = application.injector.instanceOf[CookieSigner]

    def encode(data: Map[String, String]): PlainText = {
      val encoded = data
        .map {
          case (k, v) => URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8")
        }
        .mkString("&")
      val key = "yNhI04vHs9<_HWbC`]20u`37=NGLGYY5:0Tg5?y`W<NoJnXWqmjcgZBec@rOxb^G".getBytes
      PlainText(cookieSigner.sign(encoded, key) + "-" + encoded)
    }

    val encodedCookie = encode(mdtpSessionData)
    val encrypted = CompositeSymmetricCrypto.aesGCM(cookieKey, Seq()).encrypt(encodedCookie).value

    val cookieString: String = s"""mdtp="$encrypted"; Path=/; HTTPOnly"; Path=/; HTTPOnly"""

    cookieString
  }
}
