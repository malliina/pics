package com.malliina.pics.html

import com.malliina.html.UserFeedback
import com.malliina.pics.http4s.{Reverse, ReverseSocial, SocialRoute}
//import com.malliina.pics.assets.AppAssets
import scalatags.Text.all._

object AuthHtml extends BaseHtml {
  val reverseSocial = ReverseSocial
  val reverse = Reverse

  import tags._

  def signIn(feedback: Option[UserFeedback] = None): PageConf = {
    val heading = fullRow(h1("Sign in"))
    val socials = rowColumn(s"${col.md.twelve} social-container")(
      socialButton("google", reverseSocial.google, "Sign in with Google"),
      socialButton("facebook", reverseSocial.facebook, "Sign in with Facebook"),
      socialButton("microsoft", reverseSocial.microsoft, "Sign in with Microsoft"),
      socialButton("github", reverseSocial.github, "Sign in with GitHub"),
      socialButton("twitter", reverseSocial.twitter, "Sign in with Twitter"),
      socialButton("amazon", reverseSocial.amazon, "Sign in with Amazon"),
      socialButton("apple", reverseSocial.apple, "Sign in with Apple")
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
            heading,
            socials,
            loginDivider,
            row(loginForm(feedback)),
            row(confirmForm),
            row(mfaForm),
            row(forgotForm),
            row(resetForm)
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
            heading,
            row(signUpForm(feedback)),
            row(confirmForm)
          )
        )
      ),
      extraHeader = cognitoHeaders
    )
  }

  def profile: PageConf = PageConf(
    "Pics - Profile",
    bodyClass = ProfileClass,
    inner = modifier(
      emptyNavbar,
      divClass("container", id := ProfileContainerId),
      div(id := QrCode, `class` := "qr")
    ),
//    extraHeader = modifier(cognitoHeaders, jsScript(AppAssets.js.qrcode_min_js))
    extraHeader = modifier(cognitoHeaders, jsScript(""))
  )

  def cognitoHeaders = modifier(
//    jsScript(AppAssets.js.aws_cognito_sdk_min_js),
//    jsScript(AppAssets.js.amazon_cognito_identity_min_js)
  )

  def loginForm(feedback: Option[UserFeedback] = None) =
    form(id := LoginFormId, `class` := col.md.twelve, method := Post, novalidate)(
      input(`type` := "hidden", name := TokenKey, id := LoginTokenId),
      divClass(FormGroup)(
        labeledInput("Email address", EmailId, "email", Option("me@example.com"))
      ),
      divClass(FormGroup)(
        labeledInput("Password", PasswordId, "password", None)
      ),
      divClass(s"$FormGroup d-flex")(
        a(`class` := "btn btn-link mr-auto pl-0", href := "#", id := ForgotPasswordLinkId)(
          "Forgot password?"
        ),
        submitButton(`class` := btn.primary, "Sign in")
      ),
      divClass(s"$FormGroup d-flex")(
        a(`class` := "btn btn-link ml-auto mr-auto", href := reverse.signUp)("Sign up")
      ),
      divClass(FormGroup, id := LoginFeedbackId)(
        renderFeedback(feedback)
      )
    )

  def forgotForm =
    form(id := ForgotFormId, `class` := col.md.twelve, method := Post, novalidate, hidden)(
      divClass(FormGroup)(
        labeledInput("Email address", ForgotEmailId, "email", Option("me@example.com"))
      ),
      divClass(FormGroup)(
        submitButton(`class` := btn.primary, "Send code")
      ),
      divClass(FormGroup, id := ForgotFeedbackId)
    )

  def resetForm =
    form(
      id := ResetFormId,
      `class` := col.md.twelve,
      method := Post,
      action := reverse.signIn,
      novalidate,
      hidden
    )(
      input(`type` := "hidden", name := TokenKey, id := ResetTokenId),
      divClass(FormGroup)(
        labeledInput("Email address", ResetEmailId, "email", None, disabled)
      ),
      divClass(FormGroup)(
        labeledInput("Code", ResetCodeId, "number", Option("123456"))
      ),
      divClass(FormGroup)(
        labeledInput("New password", ResetNewPasswordId, "password", None)
      ),
      divClass(FormGroup)(
        submitButton(`class` := btn.primary, "Reset")
      ),
      divClass(FormGroup, id := ResetFeedbackId)
    )

  def signUpForm(feedback: Option[UserFeedback] = None) =
    form(
      id := SignUpFormId,
      `class` := col.md.twelve,
      method := Post,
      action := reverse.signIn,
      novalidate
    )(
      input(`type` := "hidden", name := TokenKey, id := LoginTokenId),
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
      input(`type` := "hidden", name := TokenKey, id := ConfirmTokenId),
      divClass(FormGroup)(
        labeledInput("Code", CodeId, "number", Option("123456"))
      ),
      divClass(s"$FormGroup d-flex")(
        a(id := ResendId, `class` := "btn btn-link mr-auto pl-0", href := "#")("Resend code"),
        submitButton(`class` := btn.primary, "Confirm")
      ),
      divClass(FormGroup, id := ConfirmFeedbackId)
    )

  def mfaForm =
    form(id := MfaFormId, `class` := col.md.twelve, method := Post, novalidate, hidden)(
      input(`type` := "hidden", name := TokenKey, id := MfaTokenId),
      divClass(FormGroup)(
        labeledInput("Code", MfaCodeId, "number", Option("123456"))
      ),
      divClass(s"$FormGroup d-flex")(
        submitButton(`class` := btn.primary, "Submit")
      ),
      divClass(FormGroup, id := MfaFeedbackId)
    )

  def socialButton(provider: String, linkTo: SocialRoute, linkText: String) =
    a(`class` := s"social-button $provider", href := linkTo.start)(
      span(`class` := s"social-logo $provider"),
      span(`class` := "social-text", linkText)
    )

  def labeledInput(
    labelText: String,
    inId: String,
    inType: String,
    maybePlaceholder: Option[String],
    moreInput: Modifier*
  ) = modifier(
    label(`for` := inId)(labelText),
    input(
      `type` := inType,
      `class` := FormControl,
      id := inId,
      maybePlaceholder.fold(empty)(ph => placeholder := ph),
      moreInput
    )
  )

  def emptyNavbar =
    nav(`class` := s"${navbars.Navbar} ${navbars.Light} ${navbars.BgLight}")(
      divClass(Container)(
        a(`class` := navbars.Brand, href := reverse.list)("Pics")
      )
    )
}
