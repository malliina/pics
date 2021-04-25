import logging
import os
import sys

import requests

logging.basicConfig(level=os.environ.get("LOGLEVEL", "INFO"),
                    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")

log = logging.getLogger("campi")
log.info("Opening file...")

with open("image.jpg", "rb") as file:
    log.info("Uploading file...")
    requests.post('https://pics.malliina.com/pics', data = file)
    log.info("File uploaded.")
