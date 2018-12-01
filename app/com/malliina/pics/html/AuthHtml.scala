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

  def signIn(feedback: Option[UserFeedback] = None) = {
    val heading = fullRow(h1("Sign in"))
    val socials = rowColumn(s"${col.md.twelve} social-container")(
      socialButton("google", routes.Social.google(), "Sign in with Google"),
      socialButton("facebook", routes.Social.facebook(), "Sign in with Facebook"),
      socialButton("microsoft", routes.Social.microsoft(), "Sign in with Microsoft"),
      socialButton("github", routes.Social.github(), "Sign in with GitHub"),
      socialButton("twitter", routes.Social.twitter(), "Sign in with Twitter"),
      socialButton("amazon", routes.Social.amazon(), "Sign in with Amazon")
    )
    val loginDivider = divClass("login-divider")(
      divClass("line"),
      divClass("text")("or")
    )

    PageConf(
      "Pics - Sign in",
      bodyClass = LoginClass,
      inner = modifier(
        emptyNavbar,
        divClass("container")(
          divClass("auth-form ml-auto mr-auto")(
            heading, socials, loginDivider, row(loginForm(feedback)), row(confirmForm)
          )
        )
      ),
      extraHeader = cognitoHeaders
    )
  }

  def signUp(feedback: Option[UserFeedback] = None) = {
    val heading = fullRow(h1("Sign up"))

    PageConf(
      "Pics - Sign up",
      bodyClass = SignUpClass,
      inner = modifier(
        emptyNavbar,
        divClass("container")(
          divClass("auth-form ml-auto mr-auto")(
            heading, row(signUpForm(feedback)), row(confirmForm)
          )
        )
      ),
      extraHeader = cognitoHeaders
    )
  }

  def cognitoHeaders = modifier(
    jsScript(AppAssets.js.aws_cognito_sdk_min_js),
    jsScript(AppAssets.js.amazon_cognito_identity_min_js)
  )

  def loginForm(feedback: Option[UserFeedback] = None) =
    form(id := LoginFormId, `class` := col.md.twelve, method := Post, novalidate)(
      input(`type` := "hidden", name := "token", id := LoginTokenId),
      input(`type` := "hidden", name := "error", id := ErrorId),
      divClass(FormGroup)(
        labeledInput("Email address", EmailId, "email", Option("me@example.com"))
      ),
      divClass(FormGroup)(
        labeledInput("Password", PasswordId, "password", None)
      ),
      divClass(s"$FormGroup d-flex")(
        a(`class` := "btn btn-link mr-auto pl-0", href := reverse.signUp())("Sign up"),
        submitButton(`class` := btn.primary, "Sign in")
      ),
      divClass(FormGroup, id := LoginFeedbackId)(
        renderFeedback(feedback)
      )
    )

  def signUpForm(feedback: Option[UserFeedback] = None) =
    form(id := SignUpFormId, `class` := col.md.twelve, method := Post, action := reverse.postSignIn(), novalidate)(
      input(`type` := "hidden", name := "token", id := LoginTokenId),
      divClass(FormGroup)(
        labeledInput("Email address", EmailId, "email", Option("me@example.com"))
      ),
      divClass(FormGroup)(
        labeledInput("Password", PasswordId, "password", None)
      ),
      divClass(FormGroup)(
        submitButton(`class` := btn.primary, "Sign up")
      ),
      divClass(FormGroup, id := SignUpFeedbackId)(
        renderFeedback(feedback)
      )
    )

  def confirmForm =
    form(id := ConfirmFormId, `class` := col.md.twelve, method := Post, novalidate, hidden)(
      input(`type` := "hidden", name := "token", id := ConfirmTokenId),
      divClass(FormGroup)(
        labeledInput("Code", CodeId, "number", Option("123456"))
      ),
      divClass(s"$FormGroup d-flex")(
        a(id := ResendId, `class` := "btn btn-link mr-auto pl-0", href := "#")("Resend code"),
        submitButton(`class` := btn.primary, "Confirm")
      ),
      divClass(FormGroup, id := ConfirmFeedbackId)
    )

  def socialButton(provider: String, linkTo: Call, linkText: String) =
    a(`class` := s"social-button $provider", href := linkTo)(
      span(`class` := s"social-logo $provider"), span(`class` := "social-text", linkText)
    )

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
