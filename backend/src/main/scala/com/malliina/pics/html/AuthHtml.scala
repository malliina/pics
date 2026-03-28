package com.malliina.pics.html

import com.malliina.html.UserFeedback
import com.malliina.http.CSRFConf
import com.malliina.pics.{AssetsSource, Lang, LoginLang, NavLang}
import com.malliina.pics.http4s.{Reverse, ReverseSocial, SocialRoute}
import scalatags.Text.all.*

class AuthHtml(assets: AssetsSource, csrf: CSRFConf) extends BaseHtml(csrf):
  val reverseSocial = ReverseSocial
  val reverse = Reverse
  private val formGroupClass = s"$FormGroup pb-4"

  import tags.*

  def signIn(lang: Lang, feedback: Option[UserFeedback] = None): PageConf =
    val llang = lang.login
    val heading = fullRow(h1(llang.signIn))
    val socials = rowColumn(s"${col.md.twelve} social-container")(
      socialButton("google", reverseSocial.google, llang, "Google"),
      socialButton("facebook", reverseSocial.facebook, llang, "Facebook"),
      socialButton("microsoft", reverseSocial.microsoft, llang, "Microsoft"),
      socialButton("github", reverseSocial.github, llang, "GitHub"),
      socialButton("twitter", reverseSocial.twitter, llang, "Twitter"),
      socialButton("amazon", reverseSocial.amazon, llang, "Amazon"),
      socialButton("apple", reverseSocial.apple, llang, "Apple")
    )
    val loginDivider = divClass("login-divider")(
      divClass("line"),
      divClass("text")("or")
    )
    PageConf(
      llang.title,
      bodyClass = LoginClass,
      inner = modifier(
        emptyNavbar(lang.nav),
        divClass("container")(
          divClass("auth-form ms-auto me-auto")(
            heading,
            socials,
            loginDivider,
            row(loginForm(llang, feedback)),
            row(confirmForm(llang)),
            row(mfaForm(llang)),
            row(forgotForm(llang)),
            row(resetForm(llang))
          )
        )
      ),
      extraHeader = cognitoHeaders
    )

  def signUp(lang: Lang, feedback: Option[UserFeedback] = None) =
    val llang = lang.login
    PageConf(
      llang.title,
      bodyClass = SignUpClass,
      inner = modifier(
        emptyNavbar(lang.nav),
        divClass("container")(
          divClass("auth-form ms-auto me-auto")(
            fullRow(h1(llang.signUp)),
            row(signUpForm(llang, feedback)),
            row(confirmForm(llang))
          )
        )
      ),
      extraHeader = cognitoHeaders
    )

  def profile(lang: Lang): PageConf = PageConf(
    lang.profile.title,
    bodyClass = ProfileClass,
    inner = modifier(
      emptyNavbar(lang.nav),
      divClass("container", id := ProfileContainerId),
      div(id := QrCode, cls := "qr")
    ),
//    extraHeader = modifier(cognitoHeaders, jsScript(AppAssets.js.qrcode_min_js))
    extraHeader = modifier(cognitoHeaders, jsScript(""))
  )

  private def cognitoHeaders: Modifier =
    Seq("aws-cognito-sdk.min.js", "amazon-cognito-identity.min.js").map: file =>
      jsScript(assets.at(s"js/$file"))

  private def loginForm(lang: LoginLang, feedback: Option[UserFeedback]) =
    form(id := LoginFormId, cls := col.md.twelve, method := Post, novalidate)(
      input(`type` := "hidden", name := TokenKey, id := LoginTokenId),
      divClass(formGroupClass)(
        labeledInput(lang.email, EmailId, "email", Option("me@example.com"))
      ),
      divClass(formGroupClass)(
        labeledInput(lang.password, PasswordId, "password", None)
      ),
      divClass(s"$formGroupClass d-flex")(
        a(cls := "btn btn-link me-auto ps-0", href := "#", id := ForgotPasswordLinkId)(
          lang.forgotPassword
        ),
        submitButton(cls := btn.primary, lang.signIn)
      ),
      divClass(s"$formGroupClass d-flex")(
        a(cls := "btn btn-link ms-auto me-auto", href := reverse.signUp)(lang.signUp)
      ),
      divClass(formGroupClass, id := LoginFeedbackId)(
        renderFeedback(feedback)
      )
    )

  private def forgotForm(lang: LoginLang) =
    form(id := ForgotFormId, cls := col.md.twelve, method := Post, novalidate, hidden)(
      divClass(formGroupClass)(
        labeledInput(lang.email, ForgotEmailId, "email", Option("me@example.com"))
      ),
      divClass(formGroupClass)(
        submitButton(cls := btn.primary, lang.sendCode)
      ),
      divClass(formGroupClass, id := ForgotFeedbackId)
    )

  private def resetForm(lang: LoginLang) =
    form(
      id := ResetFormId,
      cls := col.md.twelve,
      method := Post,
      action := reverse.signIn,
      novalidate,
      hidden
    )(
      input(tpe := "hidden", name := TokenKey, id := ResetTokenId),
      divClass(formGroupClass)(
        labeledInput(lang.email, ResetEmailId, "email", None, disabled)
      ),
      divClass(formGroupClass)(
        labeledInput(lang.code, ResetCodeId, "number", Option("123456"))
      ),
      divClass(formGroupClass)(
        labeledInput(lang.newPassword, ResetNewPasswordId, "password", None)
      ),
      divClass(formGroupClass)(
        submitButton(`class` := btn.primary, lang.reset)
      ),
      divClass(formGroupClass, id := ResetFeedbackId)
    )

  private def signUpForm(lang: LoginLang, feedback: Option[UserFeedback]) =
    form(
      id := SignUpFormId,
      cls := col.md.twelve,
      method := Post,
      action := reverse.signIn,
      novalidate
    )(
      input(tpe := "hidden", name := TokenKey, id := LoginTokenId),
      divClass(formGroupClass)(
        labeledInput(lang.email, EmailId, "email", Option("me@example.com"))
      ),
      divClass(formGroupClass)(
        labeledInput(lang.password, PasswordId, "password", None)
      ),
      divClass(formGroupClass)(
        submitButton(cls := btn.primary, lang.signUp)
      ),
      divClass(formGroupClass, id := SignUpFeedbackId)(
        renderFeedback(feedback)
      )
    )

  private def confirmForm(lang: LoginLang) =
    form(id := ConfirmFormId, cls := col.md.twelve, method := Post, novalidate, hidden)(
      input(tpe := "hidden", name := TokenKey, id := ConfirmTokenId),
      divClass(formGroupClass)(
        labeledInput(lang.code, CodeId, "number", Option("123456"))
      ),
      divClass(s"$formGroupClass d-flex")(
        a(id := ResendId, cls := "btn btn-link me-auto ps-0", href := "#")(lang.resendCode),
        submitButton(cls := btn.primary, lang.confirm)
      ),
      divClass(formGroupClass, id := ConfirmFeedbackId)
    )

  private def mfaForm(lang: LoginLang) =
    form(id := MfaFormId, cls := col.md.twelve, method := Post, novalidate, hidden)(
      input(`type` := "hidden", name := TokenKey, id := MfaTokenId),
      divClass(formGroupClass)(
        labeledInput(lang.code, MfaCodeId, "number", Option("123456"))
      ),
      divClass(s"$formGroupClass d-flex")(
        submitButton(cls := btn.primary, lang.submit)
      ),
      divClass(formGroupClass, id := MfaFeedbackId)
    )

  private def socialButton(
    provider: String,
    linkTo: SocialRoute,
    lang: LoginLang,
    providerName: String
  ) =
    a(cls := s"social-button $provider", href := linkTo.start)(
      span(cls := s"social-logo $provider"),
      span(cls := "social-text", s"${lang.signInWith} $providerName")
    )

  private def labeledInput(
    labelText: String,
    inId: String,
    inType: String,
    maybePlaceholder: Option[String],
    moreInput: Modifier*
  ) = modifier(
    label(`for` := inId, cls := "mb-1")(labelText),
    input(
      tpe := inType,
      cls := FormControl,
      id := inId,
      maybePlaceholder.fold(empty)(ph => placeholder := ph),
      moreInput
    )
  )

  private def emptyNavbar(lang: NavLang) =
    nav(cls := s"${navbars.Navbar} ${navbars.Light} ${navbars.BgLight}")(
      divClass(Container)(
        a(cls := navbars.Brand, href := reverse.list)(lang.title)
      )
    )
