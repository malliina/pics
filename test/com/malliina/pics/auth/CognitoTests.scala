package com.malliina.pics.auth

import org.scalatest.FunSuite

class CognitoTests extends FunSuite {
  val validator = CognitoValidator.default

  ignore("valid token passes validation") {
    val token = AccessToken("eyJraWQiOiJPcmQxNGhoaHpTZHN0N3dmbUlLNTlvQk1FZHhJRUVlckRsUDNNNXNqWUNZPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiI1Mzg5Y2NlYy1mNTcwLTQyYmQtODg5YS05OTMwZjcwN2I0YzEiLCJkZXZpY2Vfa2V5IjoiZXUtd2VzdC0xXzY2ODJkMGExLTlmMjktNDkyNC1iYmFiLTU2YTJjM2I2M2UxNSIsImNvZ25pdG86Z3JvdXBzIjpbInBpY3MtZ3JvdXAiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImlzcyI6Imh0dHBzOlwvXC9jb2duaXRvLWlkcC5ldS13ZXN0LTEuYW1hem9uYXdzLmNvbVwvZXUtd2VzdC0xX2VnaTJQRWU2NSIsImV4cCI6MTUxMjIzMzgzOSwiaWF0IjoxNTEyMjMwMjM5LCJqdGkiOiJjM2YzNjZjNC01YWQyLTQ0MDktOWZhMy01OTJjMzA1ZmNiZDciLCJjbGllbnRfaWQiOiIyc2tqYmVva2U4MnJsNzZqdmgybTMxYXJ2aCIsInVzZXJuYW1lIjoibWxlIn0.ABxoR7XkUJSymwna1G2veW2NFqGNOwEsdrVVdJ9o05OMle7lcj6EkTxG-IkkA1sfCXyXtjR5-W1_YvSkOL4hNb5nEMCDw1teIj0FIOxCz71D1vUFvt6RSZfBDDxpb96XL-TzrsD52760r6EDJOQIkxiqcPVEyA46nSyCGz3jkM4q9i0F72ygLGHjqzFQP-L7uu-5gXq-Lyi0LqbFblwXxypnh2OviWXDxGt4RXnvfX-vjsX3E_uqqWpZWGPb6mhEItlg3IIu02LKdAMse0-y7GbgUeP6kCA2J8Y9qWgpSe3uduRTjE0Cccj31sf-cjFceVKFxt-tronWTYbAhnqz_Q")
    val result = validator.validate(token)
    assert(result.isRight)
  }

  test("expired token fails validation") {
    val token = AccessToken("eyJraWQiOiJPcmQxNGhoaHpTZHN0N3dmbUlLNTlvQk1FZHhJRUVlckRsUDNNNXNqWUNZPSIsImFsZyI6IlJTMjU2In0.eyJzdWIiOiI1Mzg5Y2NlYy1mNTcwLTQyYmQtODg5YS05OTMwZjcwN2I0YzEiLCJkZXZpY2Vfa2V5IjoiZXUtd2VzdC0xXzY2ODJkMGExLTlmMjktNDkyNC1iYmFiLTU2YTJjM2I2M2UxNSIsImNvZ25pdG86Z3JvdXBzIjpbInBpY3MtZ3JvdXAiXSwidG9rZW5fdXNlIjoiYWNjZXNzIiwic2NvcGUiOiJhd3MuY29nbml0by5zaWduaW4udXNlci5hZG1pbiIsImlzcyI6Imh0dHBzOlwvXC9jb2duaXRvLWlkcC5ldS13ZXN0LTEuYW1hem9uYXdzLmNvbVwvZXUtd2VzdC0xX2VnaTJQRWU2NSIsImV4cCI6MTUxMjIyODQ1MywiaWF0IjoxNTEyMjI0ODU0LCJqdGkiOiI1NjgzODM0MS03YTkzLTRiMWMtOWM2ZS0yMWFiNTRjNmQxNjkiLCJjbGllbnRfaWQiOiIyc2tqYmVva2U4MnJsNzZqdmgybTMxYXJ2aCIsInVzZXJuYW1lIjoibWxlIn0.kVLlsYh6PzHvl57u-urf2zin2nSk0Ssh-e1AMXmTxArZEkdwTFwKRYI6iSCmtadA_7QWMgJ978sN1kvCm2Bu4yW83zHqg4mEhfCBTgjQkBjn8Iyie3YdX78eEuY3m_H6gGB-CBcfJ8y4gmLbLQBCRv-8G5QZKkg4Todqj2SrpnFXRIV40sKVmh3wI6JMaCyskFPqdNjTKMe709Ku3817D17Fq3i-JBrLPAfUw19_zBeARiZljFsBXyIa8xXpRgC7LlOAo4T93MmHcQNKVUFxhD_Bz_8PAsMSOB1IfuOBE4C8iVCiglnh-JPUUia7hlghhDcN-LY_Z4wnRcszjbMY8Q")
    val result = validator.validate(token)
    assert(result.isLeft)
    val err = result.left.get
    assert(err.isInstanceOf[Expired])
  }
}