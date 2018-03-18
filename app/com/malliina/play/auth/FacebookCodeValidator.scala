package com.malliina.play.auth

import com.malliina.http.{AsyncHttp, FullUrl}
import com.malliina.play.auth.StaticCodeValidator.StaticConf
import com.malliina.play.models.Email
import play.api.mvc.{Call, RequestHeader}

import scala.concurrent.Future

class FacebookCodeValidator(val redirCall: Call, val conf: AuthConf, http: AsyncHttp)
  extends StaticCodeValidator(StaticConf.facebook(conf)) {
  
  override def validate(code: Code, req: RequestHeader): Future[Either[AuthError, Email]] = {
    val params = validationParams(code, req).mapValues(urlEncode)
    val url = staticConf.tokenEndpoint.append(s"?${stringify(params)}")
    readAs[FacebookTokens](http.get(url.url)).flatMapRight { tokens =>
      // https://developers.facebook.com/docs/php/howto/example_retrieve_user_profile
      val emailUrl = FullUrl("https", "graph.facebook.com", s"/v2.12/me?fields=email&access_token=${tokens.accessToken}")
      readAs[EmailResponse](http.get(emailUrl.url)).mapR(_.email)
    }
  }
}
