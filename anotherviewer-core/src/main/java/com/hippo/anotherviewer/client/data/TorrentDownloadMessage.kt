package com.hippo.anotherviewer.client.data

class TorrentDownloadMessage {
    @JvmField
    var path: String? = null
    @JvmField
    var dir: String? = null
    @JvmField
    var name: String? = null
    @JvmField
    var progress: Int = 0
    @JvmField
    var failed: Boolean = false

    constructor()
}
