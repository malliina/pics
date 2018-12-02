package com.malliina.pics.html

import com.malliina.pics.{HtmlBuilder, LoginStrings}

class BaseHtml extends HtmlBuilder(new com.malliina.html.Tags(scalatags.Text)) with LoginStrings