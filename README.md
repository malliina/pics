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

### Events

Open a socket to */sockets* to receive events.

#### Pics added

Sent when new pics are available. Example payload:

    {
        "pics": [
            {
                "key": "key1.jpg",
                "added": 1234567890,
                "url": "https://pics.malliina.com/key1.jpg",
                "small": "https://pics.malliina.com/key1.jpg/small",
                "medium": "https://pics.malliina.com/key1.jpg/medium",
                "large": "https://pics.malliina.com/key1.jpg/large",
                "clientKey": "my_key"
            }
        ]
    }
    
All keys are non-null except the optional *clientKey* which the pic uploader can choose to
 provide when uploading the pic.

#### Pics removed

Sent when pics have been removed. Example payload:

    {
        "keys": [
            "key1",
            "key2"
        ]
    }
