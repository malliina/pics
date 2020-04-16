package tests

import com.malliina.play.auth.{Expired, LiberalValidators}
import com.malliina.values.AccessToken
import com.nimbusds.jwt.JWTClaimsSet

class JWTTests extends munit.FunSuite {
  val validator = LiberalValidators.auth0

  test("expired JWT fails") {
    val token = AccessToken(
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UYzVSamM1TVVVM00wTTVNak0yT1VKRk5VTTFNakpCUmpjelJFVXdRVFl5TWtSRk1UZEJSQSJ9.eyJpc3MiOiJodHRwczovL21hbGxpaW5hLmV1LmF1dGgwLmNvbS8iLCJzdWIiOiJnb29nbGUtb2F1dGgyfDExMjk2NjE3MjA5OTY1MDE1MjMzNyIsImF1ZCI6WyJodHRwczovL3BpY3MubWFsbGlpbmEuY29tIiwiaHR0cHM6Ly9tYWxsaWluYS5ldS5hdXRoMC5jb20vdXNlcmluZm8iXSwiaWF0IjoxNTExODE4MDc1LCJleHAiOjE1MTE5MDQ0NzUsImF6cCI6IjNiZ0hZNThrVUxxa25IOXRRS0lrM3k2aENYRUZ1aVJrIiwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSJ9.PpDKuznMDI90DTxsrvLL_ItwJuhqfJ-IolPwgOtWHzg9rj-ZRPHF5r_E7EtKfQ70MKobdVkRAILn-pH75lRCfJZUAtNHUIAfpD0yrJpum8YiBd174VP2BbfS8w0IP23wXdWP9IUKp4DPNoKx2QULnUtW1RI7g1LXh5Y5IRFbBTMj9UNmjrqBS-vYyupfaj2heSDfKy_o2BBUQYOdVvGKMjVVwzZkeuPn-2t6x6NzA4LX_--ejOKMMCL8Y63EOEGQpu0oJKpXKF6xnvXOtn_fPz5LAH1_2U9ePkntCAuk8HVnozB22W9Za5R7Tg4NX747GAqKzbAGNcEcdWzkW9z2rg"
    )
    //    val token = AccessToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UYzVSamM1TVVVM00wTTVNak0yT1VKRk5VTTFNakpCUmpjelJFVXdRVFl5TWtSRk1UZEJSQSJ9.eyJpc3MiOiJodHRwczovL21hbGxpaW5hLmV1LmF1dGgwLmNvbS8iLCJzdWIiOiJnb29nbGUtb2F1dGgyfDExMjk2NjE3MjA5OTY1MDE1MjMzNyIsImF1ZCI6WyJodHRwczovL3BpY3MubWFsbGlpbmEuY29tIiwiaHR0cHM6Ly9tYWxsaWluYS5ldS5hdXRoMC5jb20vdXNlcmluZm8iXSwiaWF0IjoxNTExODE3ODY0LCJleHAiOjE1MTE5MDQyNjQsImF6cCI6IjNiZ0hZNThrVUxxa25IOXRRS0lrM3k2aENYRUZ1aVJrIiwic2NvcGUiOiJvcGVuaWQgcHJvZmlsZSJ9.K0q9vufUQ5XZYrD6qjXTSMkwz0HvoR1LM5icuNIhx9vFbTZ9JnTPiQLr_egvF-OGBwnqv9A1Qf_9LD4lY6aYwGoyQ9Qv99z6_acRY1Gf2LRfUjADBboIQh5baLz5nZn0YsPoyJWKoVtjMFiKIhT7kpBPXnXrkVTIMYX3OGMw7jSsq9foSKAbXBkePnd-6RdQVjLRjbzyN6p1uXiJxCI2V-OFyM-MDUOJnHtk_wtZ7vBC-3r1IutkgoVpNkSsr3gBtmyM_uNKJLMVrHjU6_rHIWAdNJOskLCSamMz36yeQqNqEckq2bq3IOTVDW1cjtgEAoHq8TrM1rTZmKQcIkHB1w")
    val result = validator.validate(token)
    assert(result.left.exists(_.isInstanceOf[Expired]))
  }

  test("expired JWT fails 2".ignore) {
    val token = AccessToken(
      "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiIsImtpZCI6Ik9UYzVSamM1TVVVM00wTTVNak0yT1VKRk5VTTFNakpCUmpjelJFVXdRVFl5TWtSRk1UZEJSQSJ9.eyJpc3MiOiJodHRwczovL21hbGxpaW5hLmV1LmF1dGgwLmNvbS8iLCJzdWIiOiJnb29nbGUtb2F1dGgyfDExMjk2NjE3MjA5OTY1MDE1MjMzNyIsImF1ZCI6WyJodHRwczovL3BpY3MubWFsbGlpbmEuY29tIiwiaHR0cHM6Ly9tYWxsaWluYS5ldS5hdXRoMC5jb20vdXNlcmluZm8iXSwiaWF0IjoxNTExOTkwMzU1LCJleHAiOjE1MTIwNzY3NTUsImF6cCI6IjNiZ0hZNThrVUxxa25IOXRRS0lrM3k2aENYRUZ1aVJrIiwic2NvcGUiOiJyZWFkOnBob3RvcyB3cml0ZTpwaG90b3Mgb3BlbmlkIn0.C8Zstb5cFsW_d-t7U6RRSro-zGLl7FAX-owChN9D8kL38ES784adqZgofAV3la5M4XMtAFwQF0zHX-m-iLq2ydB4XEQDLSQQqSF8GwoRp0n9Db10e-iqwjAa5XgzIAwF727eO1uy47xbNwQek2Ed60NaCl4CK47x06q-ZMkDjz28L_Lcnt5dxkyxOtTDqico2JuFVhngyp3x9QkaFUb3VSAeM55rkB2UgMz0xtDxjjGFqvZrBedMQpL_AfK8yF9EgSf-Gm7Km6veMliDSG3Z5Zfjdn-NFQTb0FPlF50TSayKA8_J1hjQQ8FDYzFLHLFFa10DbUmsXnG5xv61i0QMcg"
    )
    val result = validator.validate(token)
    assert(result.isRight)
  }

  test("create token".ignore) {
    val asJson = new JWTClaimsSet.Builder()
      .issuer("mle")
      .audience("world")
      .claim("scopes", Array("read", "write"))
      .build()
      .toJSONObject
    assert(asJson.getAsString("aud") == "world")
  }
}
