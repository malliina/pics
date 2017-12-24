function launch() {
    initCopyToClipboard();
    initSocket();
}

function setStatus(status) {
    console.log(status);
}

var onconnect = function (payload) {
    setStatus("Connected.");
};

var onmessage = function (payload) {
    var data = JSON.parse(payload.data);
    if (data.event === "ping") {

    } else {
        console.log('got data ' + JSON.stringify(data));
    }
};

var onclose = function (payload) {
    setStatus("Closed.");
};
var onerror = function (payload) {
    setStatus("Error.");
};

var openSocket = function (wsUrl) {
    var WS = window['MozWebSocket'] ? MozWebSocket : WebSocket;
    var webSocket = new WS(wsUrl);
    webSocket.onopen = onconnect;
    webSocket.onmessage = function (evt) {
        onmessage(evt)
    };
    webSocket.onclose = onclose;
    webSocket.onerror = onerror;
};

function initSocket() {
    // http://stackoverflow.com/a/6941653
    var scheme = location.protocol === "https:" ? "wss:" : "ws:";
    var full = scheme + '//' + location.hostname + (location.port ? ':' + location.port : '');
    openSocket(full + "/sockets");
}

function initCopyToClipboard() {
    $(".copy-button").click(function (e) {
        var url = e.delegateTarget.getAttribute("data-id");
        copyToClipboard(url);
    });
    $('[data-toggle="popover"]').popover();
}

// http://stackoverflow.com/a/30905277
function copyToClipboard(text) {
    // Create a "hidden" input
    var aux = document.createElement("input");
    // Assign it the value of the supplied parameter
    aux.setAttribute("value", text);
    // Append it to the body
    document.body.appendChild(aux);
    // Highlight its content
    aux.select();
    // Copy the highlighted text
    document.execCommand("copy");
    // Remove it from the body
    document.body.removeChild(aux);
}

window.onload = launch();

