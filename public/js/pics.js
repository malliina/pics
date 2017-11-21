function launch() {
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
