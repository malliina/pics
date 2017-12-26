package com.malliina.pics.js

import org.scalajs.dom
import org.scalajs.dom.raw._
import scalatags.JsDom.all._

class PicDrop {
  val document = dom.document
  val dropZone = document.getElementById("drop-zone").asInstanceOf[HTMLElement]
  val progress = document.getElementById("progress").asInstanceOf[HTMLProgressElement]

  dropZone.ondrop = (e: DragEvent) => {
    e.preventDefault()
    dropZone.className = "upload-drop-zone"
    startUpload(e.dataTransfer.files)
  }

  dropZone.ondragover = (_: DragEvent) => {
    dropZone.className = "upload-drop-zone drop"
    false
  }

  dropZone.ondragleave = (_: DragEvent) => {
    dropZone.className = "upload-drop-zone"
    false
  }

  def startUpload(files: FileList): Unit = {
    if (files.length > 0) {
      val file = files(0)
      val xhr = new XMLHttpRequest
      xhr.open("POST", "/pics")
      xhr.setRequestHeader("X-Name", file.name)
      xhr.onload = (e: Event) => {
        val loc = xhr.getResponseHeader("Location")
        val key = xhr.getResponseHeader("X-Key")
        if (loc != null && key != null) {
          val feedback = div(`class` := "lead alert alert-success", role := "alert")("Saved ", a(href := loc)(key))
          document.getElementById("feedback").appendChild(feedback.render)
        }
      }
      xhr.upload.onprogress = (e: ProgressEvent) => {
        if(e.lengthComputable) {
          val complete = e.loaded / e.total * 100
          progress.value = complete
        }
      }
      xhr.send(file)
    }
  }
}
