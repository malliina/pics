package com.malliina.pics.auth

import com.malliina.http.AsyncHttp
import com.malliina.pics.auth.CodeValidator._
import com.malliina.play.models.Email
import play.api.http.{HeaderNames, MimeTypes}
import play.api.mvc.Results.Redirect
import play.api.mvc.{RequestHeader, Result}

import scala.concurrent.Future

class GitHubCodeValidator(conf: AuthConf, http: AsyncHttp)
  extends CodeValidator
    with NoStateValidation {

  override def start(req: RequestHeader): Future[Result] = {
    val baseUrl = "https://github.com/login/oauth/authorize?"
    val params = Map(
      Scope -> "user:email",
      ClientId -> conf.clientId
    )
    val url = s"$baseUrl${stringify(params)}"
    fut(Redirect(url))
  }

  override def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]] = {
    val headers = Map(HeaderNames.ACCEPT -> MimeTypes.JSON)
    val params = Map(
      ClientId -> conf.clientId,
      ClientSecret -> conf.clientSecret,
      CodeKey -> code.code
    )
    readAs[GitHubTokens](http.postEmpty("https://github.com/login/oauth/access_token", headers, params)).flatMapRight { tokens =>
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
