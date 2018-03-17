package com.malliina.pics.html

import com.malliina.html.UserFeedback
import com.malliina.pics.assets.AppAssets
import com.malliina.pics.html.PicsHtml.{callAttr, reverse}
import com.malliina.pics.{HtmlBuilder, LoginStrings}
import controllers.routes
import play.api.mvc.Call
import scalatags.Text.all._

object AuthHtml extends HtmlBuilder(new com.malliina.html.Tags(scalatags.Text)) with LoginStrings {

  import tags._

  def socialButton(provider: String, linkTo: Call, linkText: String) =
    a(`class` := s"social-button $provider", href := linkTo)(
      span(`class` := s"social-logo $provider"), span(`class` := "social-text", linkText)
    )

  def signIn(feedback: Option[UserFeedback] = None) = {
    val heading = fullRow(h1("Sign In"))
    val socials = rowColumn(s"${col.md.twelve} social-container")(
      socialButton("google", routes.Social.google(), "Sign in with Google"),
      socialButton("facebook", routes.Social.facebook(), "Sign in with Facebook"),
      socialButton("microsoft", routes.Social.microsoft(), "Sign in with Microsoft"),
      socialButton("github", routes.Social.github(), "Sign in with GitHub"),
      socialButton("twitter", routes.Social.twitter(), "Sign in with Twitter"),
      socialButton("amazon", routes.CognitoControl.amazon(), "Sign in with Amazon")
    )
    val loginDivider = divClass("login-divider")(
      divClass("line"),
      divClass("text")("or")
    )
    val loginForm = form(id := LoginFormId, `class` := col.md.twelve, method := Post, novalidate)(
      input(`type` := "hidden", name := "token", id := "token"),
      input(`type` := "hidden", name := "error", id := "error"),
      divClass(FormGroup)(
        labeledInput("Email address", EmailId, "email", Option("me@example.com"))
      ),
      divClass(FormGroup)(
        labeledInput("Password", PasswordId, "password", None)
      ),
      divClass(FormGroup)(
        submitButton(`class` := btn.primary, "Sign in")
      ),
      divClass(FormGroup)(
        renderFeedback(feedback)
      )
    )

    PageConf(
      "Pics - Login",
      bodyClass = "login",
      inner = modifier(
        emptyNavbar,
        divClass("container")(
          divClass("auth-form ml-auto mr-auto")(
            heading, socials, loginDivider, row(loginForm)
          )
        )
      ),
      extraHeader = modifier(
        jsScript(AppAssets.js.aws_cognito_sdk_min_js),
        jsScript(AppAssets.js.amazon_cognito_identity_min_js)
      )
    )
  }

  def labeledInput(labelText: String, inId: String, inType: String, maybePlaceholder: Option[String]) = modifier(
    label(`for` := inId)(labelText),
    input(`type` := inType, `class` := FormControl, id := inId, maybePlaceholder.fold(empty)(ph => placeholder := ph))
  )

  def emptyNavbar =
    nav(`class` := s"${navbar.Navbar} ${navbar.Light} ${navbar.BgLight}")(
      divClass(Container)(
        a(`class` := navbar.Brand, href := reverse.list())("Pics")
      )
    )
}
