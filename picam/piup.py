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

try:
    camera = picamera.PiCamera()
    camera.resolution = (640, 480)
    # Start a preview and let the camera warm up for 2 seconds
    camera.start_preview()
    time.sleep(2)

    # Note the start time and construct a stream to hold image data
    # temporarily (we could write it directly to connection but in this
    # case we want to find out the size of each capture first to keep
    # our protocol simple)
    start = time.time()
    stream = io.BytesIO()
    log.info("Capturing images...")
    for foo in camera.capture_continuous(stream, "jpeg"):
        log.info("Sending image...")
        # Rewind the stream and send the image data over the wire
        stream.seek(0)
        requests.post(url, data = stream, headers = {"Authorization": "token todo", "Accept": "application/json"})
        # If we've been capturing for more than 3600*24*7 seconds, quit
        if time.time() - start > 3600*24*7:
            break
        # Reset the stream for the next capture
        stream.seek(0)
        stream.truncate()
        # Sleep five minutes
        time.sleep(60*5)
finally:
    log.info("Done.")
