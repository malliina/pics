package com.malliina.pics.js

import scala.scalajs.js
import scala.scalajs.js.Dynamic.literal
import scala.scalajs.js.annotation.{JSGlobal, JSName}

@js.native
trait GoogleUser extends js.Object {
  @JSName("getBasicProfile")
  def basicProfile(): GoogleProfile

  @JSName("getAuthResponse")
  def authResponse(): GoogleAuthResponse
}

@js.native
trait GoogleProfile extends js.Object {
  @JSName("getId")
  def id(): String

  @JSName("getName")
  def fullName(): String

  @JSName("getEmail")
  def email(): String

  @JSName("getImageUrl")
  def imageUrl(): String
}

@js.native
trait GoogleAuthResponse extends js.Object {
  @JSName("id_token")
  def idToken: String
}

@js.native
trait IdentityCredentials extends js.Object {
  def IdentityPoolId: String = js.native

  def Logins: js.Dictionary[String] = js.native
}

object IdentityCredentials {
  import js.JSConverters._

  def apply(poolId: String, logins: Map[String, String]) =
    literal(IdentityPoolId = poolId, Logins = logins.toJSDictionary)
      .asInstanceOf[IdentityCredentials]

  def google(poolId: String, idToken: String) =
    apply(poolId, Map("accounts.google.com" -> idToken))
}

@js.native
trait AccessPayload extends js.Object {
  def username: String = js.native

  //  @JSName("cognito:groups")
  //  def cognitoGroups: Seq[String] = js.native
}

@js.native
trait AccessToken extends js.Object {
  def jwtToken: String = js.native

  def payload: js.Any = js.native
}

@js.native
trait IdToken extends js.Object {
  def jwtToken: String = js.native

  def payload: js.Any = js.native
}

@js.native
trait RefreshToken extends js.Object {
  def token: String = js.native
}

@js.native
@JSGlobal
object JSON extends js.Object {
  def parse(text: String): js.Any = js.native

  def stringify(value: js.Any): String = js.native
}
