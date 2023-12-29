XMLHttpRequest.prototype.origOpen = XMLHttpRequest.prototype.open;
XMLHttpRequest.prototype.open = function(method, url, async, user, password) {
    // these will be the key to retrieve the payload
    this.recordedMethod = method;
    this.recordedUrl = url;
    this.origOpen(method, url, async, user, password);
};
XMLHttpRequest.prototype.origSend = XMLHttpRequest.prototype.send;
XMLHttpRequest.prototype.send = function(body) {
    // interceptor is a Kotlin interface added in WebView
    if(body) recorder.recordPayload(this.recordedMethod, this.recordedUrl, body);
    this.origSend(body);
};

XMLHttpRequest.prototype.orisubmit = XMLHttpRequest.prototype.submit;
XMLHttpRequest.prototype.submit = function(body) {
    // interceptor is a Kotlin interface added in WebView
    if(body) recorder.recordPayload(this.recordedMethod, this.recordedUrl, body);
    this.orisubmit(body);
};