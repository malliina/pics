function launch() {
    var dropZone = document.getElementById("drop-zone");
    var progress = document.getElementById("progress");

    var startUpload2 = function (files) {
        //console.log(files);
        var formData = new FormData();
        for (var i = 0; i < files.length; i++) {
            formData.append("file", files[i]);
        }

        // now post a new XHR request
        var xhr = new XMLHttpRequest();
        xhr.open("POST", "/pics");
        xhr.onload = function () {
            var status = xhr.status;
            //console.log('got response ' + status);
            var location = xhr.getResponseHeader("Location");
            var key = xhr.getResponseHeader("X-Key");
            if (location != null && key != null) {
                document.getElementById("feedback").innerHTML =
                    "<div class='lead alert alert-success' role='alert'>Saved <a href='" + location + "'>" + key + "</a></div>";
            }
        };
        xhr.upload.onprogress = function (event) {
            if (event.lengthComputable) {
                var complete = (event.loaded / event.total * 100 | 0);
                progress.value = progress.innerHTML = complete;
            }
        };
        xhr.send(formData);
    };

    var startUpload = function (files) {
        if (files.length === 0) return;
        var file = files[0];
        //console.log(files);
        var xhr = new XMLHttpRequest();
        xhr.open("POST", "/pics");
        xhr.setRequestHeader("X-Name", files[0].name);
        xhr.onload = function () {
            // var status = xhr.status;
            //console.log('got response ' + status);
            var location = xhr.getResponseHeader("Location");
            var key = xhr.getResponseHeader("X-Key");
            if (location != null && key != null) {
                document.getElementById("feedback").innerHTML =
                    "<div class='lead alert alert-success' role='alert'>Saved <a href='" + location + "'>" + key + "</a></div>";
            }
        };
        xhr.upload.onprogress = function (event) {
            if (event.lengthComputable) {
                var complete = (event.loaded / event.total * 100 | 0);
                progress.value = progress.innerHTML = complete;
            }
        };
        xhr.send(file);
    };

    dropZone.ondrop = function (e) {
        e.preventDefault();
        this.className = 'upload-drop-zone';

        startUpload(e.dataTransfer.files)
    };

    dropZone.ondragover = function () {
        this.className = 'upload-drop-zone drop';
        return false;
    };

    dropZone.ondragleave = function () {
        this.className = 'upload-drop-zone';
        return false;
    };
}

window.onload = launch();
