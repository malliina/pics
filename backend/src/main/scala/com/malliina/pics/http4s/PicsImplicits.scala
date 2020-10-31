package com.malliina.pics.http4s

import cats.effect.IO
import org.http4s.dsl.Http4sDsl
import org.http4s.play.PlayInstances
import org.http4s.scalatags.ScalatagsInstances
import org.http4s.syntax

abstract class PicsImplicits[F[_]]
  extends syntax.AllSyntaxBinCompat
  with Http4sDsl[F]
  with ScalatagsInstances
  with PlayInstances

object PicsImplicits extends PicsImplicits[IO]
