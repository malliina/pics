package com.malliina.play.auth

import com.malliina.http.AsyncHttp
import com.malliina.play.auth.StaticCodeValidator.StaticConf
import com.malliina.play.models.Email
import play.api.http.{HeaderNames, MimeTypes}
import play.api.mvc.{Call, RequestHeader}

import scala.concurrent.Future

class GitHubCodeValidator(val redirCall: Call, val conf: AuthConf, http: AsyncHttp)
  extends StaticCodeValidator(StaticConf.github(conf)) {

  override def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]] = {
    val headers = Map(HeaderNames.ACCEPT -> MimeTypes.JSON)
    val params = validationParams(code, req)
    readAs[GitHubTokens](http.postEmpty(staticConf.tokenEndpoint.url, headers, params)).flatMapRight { tokens =>
      readAs[Seq[GitHubEmail]](http.get(s"https://api.github.com/user/emails?access_token=${tokens.accessToken}")).mapRight { emails =>
        emails.find(email => email.primary && email.verified).map { primaryEmail =>
          Right(primaryEmail.email)
        }.getOrElse {
          Left(JsonError("No primary and verified email found."))
        }
      }
    }
  }
}
