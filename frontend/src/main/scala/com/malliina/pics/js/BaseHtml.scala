package com.malliina.pics.js

import com.malliina.html.Tags
import com.malliina.pics.HtmlBuilder

object BaseHtml extends BaseHtml

abstract class BaseHtml extends HtmlBuilder(new Tags(scalatags.JsDom))
