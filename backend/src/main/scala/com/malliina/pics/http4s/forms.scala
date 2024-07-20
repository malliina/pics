package com.malliina.pics.http4s

import com.malliina.http4s.FormReadableT
import com.malliina.pics.{Access, Key}
import io.circe.Codec

case class AccessLevel(access: Access) derives Codec.AsObject

object AccessLevel:
  given FormReadableT[AccessLevel] = FormReadableT.reader.emap: reader =>
    reader
      .read[Access](Access.FormKey)
      .map: access =>
        AccessLevel(access)

case class Deletion(key: Key) derives Codec.AsObject

object Deletion:
  given FormReadableT[Deletion] = FormReadableT.reader.emap: reader =>
    reader
      .read[Key](Key.Key)
      .map: key =>
        Deletion(key)
