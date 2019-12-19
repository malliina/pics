package controllers

import controllers.Assets.Asset
import play.api.mvc.{Action, AnyContent}

class PicsAssets(builder: AssetsBuilder) {
  def static(file: String): Action[AnyContent] =
    builder.at("/public", s"static/$file", aggressiveCaching = true)

  def versioned(path: String, file: Asset) = builder.versioned(path, file)

  def appleDomainAssociation =
    builder.at("/public", "well-known/apple-developer-domain-association.txt")
}
