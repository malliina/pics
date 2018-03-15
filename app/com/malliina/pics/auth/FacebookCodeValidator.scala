package com.malliina.pics.auth

import com.malliina.http.{AsyncHttp, FullUrl}
import com.malliina.pics.auth.CodeValidator._
import com.malliina.play.http.FullUrls
import com.malliina.play.models.Email
import controllers.CognitoControl
import play.api.mvc.Results.Redirect
import play.api.mvc.{Call, RequestHeader, Result}

import scala.concurrent.Future

class FacebookCodeValidator(redirCall: Call, conf: AuthConf, http: AsyncHttp)
  extends CodeValidator
    with StateValidation {

  override def start(req: RequestHeader): Future[Result] = {
    val baseUrl = "https://www.facebook.com/v2.12/dialog/oauth?"
    val state = CognitoControl.randomState()
    val params = Map(
      ClientId -> conf.clientId,
      RedirectUri -> FullUrls(redirCall, req).url,
      State -> state,
      Scope -> "public_profile email"
    )
    val url = s"$baseUrl${stringify(params)}"
    fut(Redirect(url).withSession(State -> state))
  }

  override def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]] = {
    val params = Map(
      ClientId -> conf.clientId,
      ClientSecret -> conf.clientSecret,
      RedirectUri -> FullUrls(redirCall, req).url,
      CodeKey -> code.code
    )
    val url = FullUrl("https", "graph.facebook.com", s"/v2.12/oauth/access_token?${stringify(params)}")
    readAs[FacebookTokens](http.get(url.url)).flatMapRight { tokens =>
      // https://developers.facebook.com/docs/php/howto/example_retrieve_user_profile
      val emailUrl = FullUrl("https", "graph.facebook.com", s"/v2.12/me?fields=email&access_token=${tokens.accessToken}")
      readAs[EmailResponse](http.get(emailUrl.url)).mapR(_.email)
    }
  }
}
