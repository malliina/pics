[![Build Status](https://travis-ci.org/malliina/pics.svg?branch=master)](https://travis-ci.org/malliina/pics)

# pics

This is a pic app.

## API

Use the HTTP API to list, download, save and remove images.

### GET /pics

Returns the images. Latest first.

### GET /pics/*key/thumb

Returns the thumbnail of the image with the given *key*.

### GET /pics/*key

Returns the image with the given *key*.

### POST /pics

Saves the uploaded image in your image gallery. Include the raw image contents in the request body. 
(Not as form data.) Optionally set the image filename in the *X-Name* request header.
