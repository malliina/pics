package com.malliina.pics.js

import com.malliina.pics.CSRFConf.{CsrfHeaderName, CsrfTokenNoCheck}
import com.malliina.pics.PicsStrings
import org.scalajs.dom.*
import scalatags.JsDom.all.*

class PicDrop extends Frontend with PicsStrings:
  val dropZone = elem[HTMLElement]("drop-zone")
  val progress = elem[HTMLProgressElement]("progress")

  dropZone.ondrop = (e: DragEvent) =>
    e.preventDefault()
    dropZone.className = "upload-drop-zone"
    startUpload(e.dataTransfer.files)

  dropZone.ondragover = (_: DragEvent) =>
    dropZone.className = "upload-drop-zone drop"
    false

  dropZone.ondragleave = (_: DragEvent) =>
    dropZone.className = "upload-drop-zone"
    false

  def startUpload(files: FileList): Unit =
    if files.length > 0 then
      val file = files(0)
      val xhr = new XMLHttpRequest
      xhr.open("POST", "/pics")
      xhr.setRequestHeader(XName.toString, file.name)
      xhr.setRequestHeader(CsrfHeaderName, CsrfTokenNoCheck)
      xhr.onload = (e: Event) =>
        val loc = xhr.getResponseHeader("Location")
        val key = xhr.getResponseHeader(XKey.toString)
        if loc != null && key != null then
          val feedback = div(`class` := "lead alert alert-success", role := "alert")(
            "Saved ",
            a(href := loc)(key)
          )
          document.getElementById("feedback").appendChild(feedback.render)
      xhr.upload.onprogress = (e: ProgressEvent) =>
        if e.lengthComputable then
          val complete = e.loaded / e.total * 100
          progress.value = complete
      xhr.send(file)
