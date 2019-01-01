Develop web and mobile apps using this API for pics.

## GET /pics

Returns a list of pics:

    {
        "pics": [
            {
                "key": "key1.jpg",
                "added": 1234567890,
                "url": "https://pics.malliina.com/key1.jpg",
                "small": "https://pics.malliina.com/key1.jpg/small",
                "medium": "https://pics.malliina.com/key1.jpg/medium",
                "large": "https://pics.malliina.com/key1.jpg/large"
            }
        ]
    }

The following query parameters are supported:

| Key | Meaning | Example
|-----|---------|-------------------
| limit | Maximum number of pics | 50
| offset | Number of pics to drop | 0

## POST /pics

Adds a new pic. Provide the pic in the payload of the request.

The HTTP response includes the following headers:

| Header | Meaning | Example
|--------|---------|-------------------
| X-Key | Key of uploaded pic | abc123.jpeg
| Location | URL to pic | https://pics.malliina.com/abc123.jpeg

## POST /pics/*key/delete

Deletes the pic with the given *key*.

Returns HTTP 202 on success.

## WebSocket /sockets

Listens to pic updates over a WebSocket connection. Any updates are formatted as JSON messages. Two message types are
supported: pics added and pics removed.

### Pics added

    {
        "pics": [
            {
                "key": "key1.jpg",
                "added": 1234567890,
                "url": "https://pics.malliina.com/key1.jpg",
                "small": "https://pics.malliina.com/key1.jpg/small",
                "medium": "https://pics.malliina.com/key1.jpg/medium",
                "large": "https://pics.malliina.com/key1.jpg/large"
            }
        ]
    }

### Pics removed

Sent when pics have been removed.

    {
        "keys": [
            "key1",
            "key2"
        ]
    }

The payload contains the keys of the removed pics.