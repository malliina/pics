package com.malliina.pics

import com.malliina.html.UserFeedback
import io.circe.Codec

object FeedbackJson extends FeedbackJson

trait FeedbackJson:
  given feedbackJson: Codec[UserFeedback] = Codec.derived[UserFeedback]
