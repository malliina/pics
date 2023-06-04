package com.malliina.pics.auth

import com.malliina.values.{AccessToken, IdToken}
import com.malliina.web.Expired

class CognitoTests extends munit.FunSuite:
  val validator = Validators.picsAccess

  test("valid token passes validation".ignore) {
    val token = AccessToken(
      "eyJraWQiOiJPcmQxNGhoaHpTZHN0N3dmbUlLNTlvQk1FZHhJRUVlckRsUDNNNXNqWUNZPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiI1Mzg5Y2NlYy1mNTcwLTQyYmQtODg5YS05OTMwZjcwN2I0YzEiLCJkZXZpY2Vfa2V5IjoiZXUtd2VzdC0xXzY2ODJkMGExLTlmMjktNDkyNC1iYmFiLTU2YTJjM2I2M2UxNSIsImNvZ25pdG86Z3JvdXBzIjpbInBpY3MtZ3JvdXAiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImlzcyI6Imh0dHBzOlwvXC9jb2duaXRvLWlkcC5ldS13ZXN0LTEuYW1hem9uYXdzLmNvbVwvZXUtd2VzdC0xX2VnaTJQRWU2NSIsImV4cCI6MTUxMjIzMzgzOSwiaWF0IjoxNTEyMjMwMjM5LCJqdGkiOiJjM2YzNjZjNC01YWQyLTQ0MDktOWZhMy01OTJjMzA1ZmNiZDciLCJjbGllbnRfaWQiOiIyc2tqYmVva2U4MnJsNzZqdmgybTMxYXJ2aCIsInVzZXJuYW1lIjoibWxlIn0.ABxoR7XkUJSymwna1G2veW2NFqGNOwEsdrVVdJ9o05OMle7lcj6EkTxG-IkkA1sfCXyXtjR5-W1_YvSkOL4hNb5nEMCDw1teIj0FIOxCz71D1vUFvt6RSZfBDDxpb96XL-TzrsD52760r6EDJOQIkxiqcPVEyA46nSyCGz3jkM4q9i0F72ygLGHjqzFQP-L7uu-5gXq-Lyi0LqbFblwXxypnh2OviWXDxGt4RXnvfX-vjsX3E_uqqWpZWGPb6mhEItlg3IIu02LKdAMse0-y7GbgUeP6kCA2J8Y9qWgpSe3uduRTjE0Cccj31sf-cjFceVKFxt-tronWTYbAhnqz_Q"
    )
    val result = validator.validate(token)
    assert(result.isRight)
  }

  test("expired token fails validation") {
    val token = AccessToken(
      "eyJraWQiOiJPcmQxNGhoaHpTZHN0N3dmbUlLNTlvQk1FZHhJRUVlckRsUDNNNXNqWUNZPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiI1Mzg5Y2NlYy1mNTcwLTQyYmQtODg5YS05OTMwZjcwN2I0YzEiLCJkZXZpY2Vfa2V5IjoiZXUtd2VzdC0xXzY2ODJkMGExLTlmMjktNDkyNC1iYmFiLTU2YTJjM2I2M2UxNSIsImNvZ25pdG86Z3JvdXBzIjpbInBpY3MtZ3JvdXAiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImlzcyI6Imh0dHBzOlwvXC9jb2duaXRvLWlkcC5ldS13ZXN0LTEuYW1hem9uYXdzLmNvbVwvZXUtd2VzdC0xX2VnaTJQRWU2NSIsImV4cCI6MTUxMjIyODQ1MywiaWF0IjoxNTEyMjI0ODU0LCJqdGkiOiI1NjgzODM0MS03YTkzLTRiMWMtOWM2ZS0yMWFiNTRjNmQxNjkiLCJjbGllbnRfaWQiOiIyc2tqYmVva2U4MnJsNzZqdmgybTMxYXJ2aCIsInVzZXJuYW1lIjoibWxlIn0.kVLlsYh6PzHvl57u-urf2zin2nSk0Ssh-e1AMXmTxArZEkdwTFwKRYI6iSCmtadA_7QWMgJ978sN1kvCm2Bu4yW83zHqg4mEhfCBTgjQkBjn8Iyie3YdX78eEuY3m_H6gGB-CBcfJ8y4gmLbLQBCRv-8G5QZKkg4Todqj2SrpnFXRIV40sKVmh3wI6JMaCyskFPqdNjTKMe709Ku3817D17Fq3i-JBrLPAfUw19_zBeARiZljFsBXyIa8xXpRgC7LlOAo4T93MmHcQNKVUFxhD_Bz_8PAsMSOB1IfuOBE4C8iVCiglnh-JPUUia7hlghhDcN-LY_Z4wnRcszjbMY8Q"
    )
    val result = validator.validate(token)
    assert(result.isLeft)
    val err = result.left.toOption.get
    assert(err.isInstanceOf[Expired])
  }

  test("some JWT test") {
    val token = AccessToken(
      "eyJraWQiOiJPcmQxNGhoaHpTZHN0N3dmbUlLNTlvQk1FZHhJRUVlckRsUDNNNXNqWUNZPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiI0YjYxNDhhZC1jNTBhLTRjN2QtOWY2OC1kMmM1M2U0ZjAzMmYiLCJjb2duaXRvOmdyb3VwcyI6WyJldS13ZXN0LTFfZWdpMlBFZTY1X0dvb2dsZSJdLCJ0b2tlbl91c2UiOiJhY2Nlc3MiLCJzY29wZSI6ImF3cy5jb2duaXRvLnNpZ25pbi51c2VyLmFkbWluIHBob25lIG9wZW5pZCBwcm9maWxlIGVtYWlsIiwiYXV0aF90aW1lIjoxNTIwMTg3MzQwLCJpc3MiOiJodHRwczpcL1wvY29nbml0by1pZHAuZXUtd2VzdC0xLmFtYXpvbmF3cy5jb21cL2V1LXdlc3QtMV9lZ2kyUEVlNjUiLCJleHAiOjE1MjAxOTA5NDAsImlhdCI6MTUyMDE4NzM0MCwidmVyc2lvbiI6MiwianRpIjoiZDU0NzYzZDItODRhNS00MzdiLTkyZTEtMmRiNDdiMzVmMzQzIiwiY2xpZW50X2lkIjoiMnJucWVwdjQ0ZXBhcmdkb3NiYTZubGcydDkiLCJ1c2VybmFtZSI6Ikdvb2dsZV8xMTU3MTIwMzk1MDM0MDAyOTI5MDEifQ.ZjALfr8-XJxBf6aa1LqkwyMvNd1Q4L8EmsHkk3CKMOeulGJbNamizMvaT6N0IbWovND6HCyPVtCPbXCksc8nBDxurXX2gvvRPYCr3t_FfdbZrqXOupN-yLxNVgrpKvvLg08way-xW-CANkd1LSDAo9gKdBzSUuMvSClgvVblPsBdUlR0KrT6oQFt93_HEJNC8LPkEglpwaRXp1Icg1irmuUw2q5Th2q9_u1ZFJzMuSQMFzepaNhX4qEEGIESVfwlvlUOz6qNlOGt48m38-KjOUPZuvzN7bizT0fiYQuwdtlvkSwNhz5XyLfK65VYjmrSX6kGTPwHw4d68T9vLIoRSQ"
    )
    //    val token = AccessToken("eyJraWQiOiJPcmQxNGhoaHpTZHN0N3dmbUlLNTlvQk1FZHhJRUVlckRsUDNNNXNqWUNZPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiI0YjYxNDhhZC1jNTBhLTRjN2QtOWY2OC1kMmM1M2U0ZjAzMmYiLCJjb2duaXRvOmdyb3VwcyI6WyJldS13ZXN0LTFfZWdpMlBFZTY1X0dvb2dsZSJdLCJ0b2tlbl91c2UiOiJhY2Nlc3MiLCJzY29wZSI6ImF3cy5jb2duaXRvLnNpZ25pbi51c2VyLmFkbWluIHBob25lIG9wZW5pZCBwcm9maWxlIGVtYWlsIiwiYXV0aF90aW1lIjoxNTIwMTc4OTEzLCJpc3MiOiJodHRwczpcL1wvY29nbml0by1pZHAuZXUtd2VzdC0xLmFtYXpvbmF3cy5jb21cL2V1LXdlc3QtMV9lZ2kyUEVlNjUiLCJleHAiOjE1MjAxODI1MTMsImlhdCI6MTUyMDE3ODkxMywidmVyc2lvbiI6MiwianRpIjoiYTk4MWM5MWQtOTJkMC00Yzk5LTk4YmYtNGM0OTM4NWU1NDE5IiwiY2xpZW50X2lkIjoiMnJucWVwdjQ0ZXBhcmdkb3NiYTZubGcydDkiLCJ1c2VybmFtZSI6Ikdvb2dsZV8xMTU3MTIwMzk1MDM0MDAyOTI5MDEifQ.c7Q-FUpiTuo2Uj99PQEwQEihN0s73Hsf02jUDMBW0tTvtCH53eJFDm6RdhGQMdM2roocnxFY1vvYpNX1fxKnt1mdFKEdPENRhGqqzNWIaJ-G-zrrQxVQrqJe3N15v1Q-pPpLvD9l3e2zdqVaBxxoz0kglG0cKB47yh-SivPgVWqVxaecddiTCoWTMwwXPKo6z4jdh_B6ErhGtcFJ1I3DGlBKgIzivNcr24cX6M9q2Dm5wSRPvag_tu6Dn9y1FuKk5rSYwcXl-Or1yxSHSjv4cr3T9S_ThFaDIBYbeHs4kAqhj0zuJ86fqdXaO-G0uUPK3u9jSlIshbPXu0BZVplQJA")
    val _ = Validators.picsAccess.validate(token)
    // println(result)
  }

  test("id token") {
    val token = IdToken(
      "eyJraWQiOiJxVHE4WUdySVwvTnRsOGVldjgyNlhCMm1ES29YZkFlUzVuRkhSYzc4UU14Zz0iLCJhbGciOiJSUzI1NiJ9.eyJhdF9oYXNoIjoiMC13bUVEZlpBTDlTRG9KRnEyVUtYQSIsInN1YiI6IjY4MzdiOTA1LTU3YWMtNDE4OS1iNTEyLTVlYTNmMjM3YThiMiIsImF1ZCI6IjJybnFlcHY0NGVwYXJnZG9zYmE2bmxnMnQ5IiwiY29nbml0bzpncm91cHMiOlsiZXUtd2VzdC0xX2VnaTJQRWU2NV9Hb29nbGUiXSwiaWRlbnRpdGllcyI6W3sidXNlcklkIjoiMTE1NzEyMDM5NTAzNDAwMjkyOTAxIiwicHJvdmlkZXJOYW1lIjoiR29vZ2xlIiwicHJvdmlkZXJUeXBlIjoiR29vZ2xlIiwiaXNzdWVyIjpudWxsLCJwcmltYXJ5IjoidHJ1ZSIsImRhdGVDcmVhdGVkIjoiMTUyMDQ0NzY0MjczNiJ9XSwidG9rZW5fdXNlIjoiaWQiLCJhdXRoX3RpbWUiOjE1MjA0NTk5MDUsImlzcyI6Imh0dHBzOlwvXC9jb2duaXRvLWlkcC5ldS13ZXN0LTEuYW1hem9uYXdzLmNvbVwvZXUtd2VzdC0xX2VnaTJQRWU2NSIsImNvZ25pdG86dXNlcm5hbWUiOiJHb29nbGVfMTE1NzEyMDM5NTAzNDAwMjkyOTAxIiwiZXhwIjoxNTIwNDYzNTA1LCJpYXQiOjE1MjA0NTk5MDUsImVtYWlsIjoibWxlc2tpMTIzQGdtYWlsLmNvbSJ9.QYf4qDpt1leZwkYsJYaRD9nAGKUBkror9kL6Cw9d_gN9ZAHGxOqPB5HBHh56ibOUaFY2UDlKDhZAlkAJcIN-YoXx377LjD3oe5pC4bAEeb9UUgokOcigNy6ndzOMhie6VLU7-2-gKaorV269NKfrD2vdg7WTGDw1LvNdpgdIhi3P73yl6sUfhm678__v8homwhfT0LCrmsEv09zWp_OSmiXvI99ZEt06ygwmdm9PieKLmnw1oUsAcB5SFtNfWptaAHumIyv30yQ-xRdEm7BVjjPmhxSTSmGLcXt5z8zGnBZE2epsAkb5b-xHLTA-p09yJ_5SnfYW8kilhi3X5iVjbA"
    )
    val _ = Validators.picsId.validate(token)
    // println(result)
  }
