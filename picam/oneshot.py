import io
import logging
import os
import time

import picamera
import requests

logging.basicConfig(level=os.environ.get("LOGLEVEL", "INFO"),
                    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")

log = logging.getLogger("campi")
log.info("Starting...")

url = "https://pics.malliina.com/pics"

# Create an in-memory stream
stream = io.BytesIO()
camera = picamera.PiCamera()
camera.resolution = (640, 480)
camera.start_preview()
# Camera warm-up time
time.sleep(2)
log.info("Capturing image...")
camera.capture(stream, 'jpeg')
# "Rewinds" the stream to the beginning so we can read its content
stream.seek(0)
log.info("Sending image...")
requests.post(url, data = stream, headers = {"Authorization": "token todo", "Accept": "application/json"})
stream.truncate()
log.info("Done.")
