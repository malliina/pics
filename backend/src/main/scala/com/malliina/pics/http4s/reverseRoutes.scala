package com.malliina.pics.http4s

import com.malliina.pics.Key
import org.http4s.Uri
import org.http4s.implicits._

case class SocialRoute(start: Uri, callback: Uri)

object SocialRoute {
  def apply(name: String): SocialRoute = {
    val base = uri"/sign-in"
    SocialRoute(base / name, base / "callbacks" / name)
  }
}

object ReverseSocial {
  val base = uri"/sign-in"
  val amazon = SocialRoute("amazon")
  val apple = SocialRoute("apple")
  val facebook = SocialRoute("facebook")
  val google = SocialRoute("google")
  val googleAmazon = SocialRoute("google-amazon")
  val github = SocialRoute("github")
  val microsoft = SocialRoute("microsoft")
  val twitter = SocialRoute("twitter")
}

object Reverse extends Reverse

trait Reverse {
  val drop = uri"/drop"
  val list = uri"/pics"

  val signIn = uri"/sign-in"
  val signUp = uri"/sign-up"
  val signOut = uri"/sign-out/leave"
  val signOutCallback = uri"/sign-out"

  val sync = uri"/sync"
  val delete = uri"/pics/delete"

  def pic(key: Key) = Uri.unsafeFromString(s"/$key")
}
