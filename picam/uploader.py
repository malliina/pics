import logging
import os
import sys

import requests

logging.basicConfig(level=os.environ.get("LOGLEVEL", "INFO"),
                    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s")

log = logging.getLogger("campi")
log.info("Opening file...")

#url = "https://pics.malliina.com/pics"
url = "http://localhost:9000/pics"
with open("image.jpg", "rb") as file:
    log.info("Uploading file...")
    requests.post(url, data = file, headers = {"Authorization": "token todo", "Accept": "application/json"})
    log.info("File uploaded.")
