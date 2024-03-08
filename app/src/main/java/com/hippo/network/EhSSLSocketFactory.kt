package com.hippo.network

import android.util.Log
import com.hippo.ehviewer.Settings
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import java.net.SocketException
import java.nio.channels.SocketChannel
import java.util.*
import javax.net.ssl.HandshakeCompletedListener
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class EhSSLSocketFactory : SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> {
        return (getDefault() as SSLSocketFactory).defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return (getDefault() as SSLSocketFactory).supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        if (!Settings.getDF()) {
            return (getDefault() as SSLSocketFactory).createSocket(s, host, port, autoClose)
        }
        val address = s.inetAddress
        if (address != null) {
            val hostAddress = address.hostAddress
            if (hostAddress != null) {
                Log.d("EhSSLSocketFactory", "Host: $host Address: $hostAddress")
            }
        }
        if (autoClose) s.close()
        return getDefault().createSocket(address, port)
    }

    @Throws(IOException::class)
    override fun createSocket(host: String, port: Int): Socket {
        return getDefault().createSocket(host, port)
    }

    @Throws(IOException::class)
    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket {
        return getDefault().createSocket(host, port, localHost, localPort)
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        return getDefault().createSocket(host, port)
    }

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket {
        return getDefault().createSocket(address, port, localAddress, localPort)
    }

    private inner class NoSSLv3SSLSocket private constructor(delegate: SSLSocket) :
        DelegateSSLSocket(delegate) {
        override fun setEnabledProtocols(protocols: Array<String>) {
            var protocols = protocols
            if (protocols != null && protocols.size == 1 && "SSLv3" == protocols[0]) {
                val enabledProtocols: MutableList<String> =
                    ArrayList(Arrays.asList(*delegate.enabledProtocols))
                if (enabledProtocols.size > 1) {
                    enabledProtocols.remove("SSLv3")
                    println("Removed SSLv3 from enabled protocols")
                } else {
                    println("SSL stuck with protocol available for $enabledProtocols")
                }
                protocols = enabledProtocols.toTypedArray()
            }
            super.setEnabledProtocols(protocols)
        }

        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener) {}
        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener) {}
    }

    open inner class DelegateSSLSocket internal constructor(protected val delegate: SSLSocket) :
        SSLSocket() {
        override fun getSupportedCipherSuites(): Array<String> {
            return delegate.supportedCipherSuites
        }

        override fun getEnabledCipherSuites(): Array<String> {
            return delegate.enabledCipherSuites
        }

        override fun setEnabledCipherSuites(suites: Array<String>) {
            delegate.enabledCipherSuites = suites
        }

        override fun getSupportedProtocols(): Array<String> {
            return delegate.supportedProtocols
        }

        override fun getEnabledProtocols(): Array<String> {
            return delegate.enabledProtocols
        }

        override fun setEnabledProtocols(protocols: Array<String>) {
            delegate.enabledProtocols = protocols
        }

        override fun getSession(): SSLSession {
            return delegate.session
        }

        override fun addHandshakeCompletedListener(listener: HandshakeCompletedListener) {
            delegate.addHandshakeCompletedListener(listener)
        }

        override fun removeHandshakeCompletedListener(listener: HandshakeCompletedListener) {
            delegate.removeHandshakeCompletedListener(listener)
        }

        @Throws(IOException::class)
        override fun startHandshake() {
            delegate.startHandshake()
        }

        override fun setUseClientMode(mode: Boolean) {
            delegate.useClientMode = mode
        }

        override fun getUseClientMode(): Boolean {
            return delegate.useClientMode
        }

        override fun setNeedClientAuth(need: Boolean) {
            delegate.needClientAuth = need
        }

        override fun setWantClientAuth(want: Boolean) {
            delegate.wantClientAuth = want
        }

        override fun getNeedClientAuth(): Boolean {
            return delegate.needClientAuth
        }

        override fun getWantClientAuth(): Boolean {
            return delegate.wantClientAuth
        }

        override fun setEnableSessionCreation(flag: Boolean) {
            delegate.enableSessionCreation = flag
        }

        override fun getEnableSessionCreation(): Boolean {
            return delegate.enableSessionCreation
        }

        @Throws(IOException::class)
        override fun bind(localAddr: SocketAddress) {
            delegate.bind(localAddr)
        }

        @Synchronized
        @Throws(IOException::class)
        override fun close() {
            delegate.close()
        }

        @Throws(IOException::class)
        override fun connect(remoteAddr: SocketAddress) {
            delegate.connect(remoteAddr)
        }

        @Throws(IOException::class)
        override fun connect(remoteAddr: SocketAddress, timeout: Int) {
            delegate.connect(remoteAddr, timeout)
        }

        override fun getChannel(): SocketChannel {
            return delegate.channel
        }

        override fun getInetAddress(): InetAddress {
            return delegate.inetAddress
        }

        @Throws(IOException::class)
        override fun getInputStream(): InputStream {
            return delegate.inputStream
        }

        @Throws(SocketException::class)
        override fun getKeepAlive(): Boolean {
            return delegate.keepAlive
        }

        override fun getLocalAddress(): InetAddress {
            return delegate.localAddress
        }

        override fun getLocalPort(): Int {
            return delegate.localPort
        }

        override fun getLocalSocketAddress(): SocketAddress {
            return delegate.localSocketAddress
        }

        @Throws(SocketException::class)
        override fun getOOBInline(): Boolean {
            return delegate.oobInline
        }

        @Throws(IOException::class)
        override fun getOutputStream(): OutputStream {
            return delegate.outputStream
        }

        override fun getPort(): Int {
            return delegate.port
        }

        @Synchronized
        @Throws(SocketException::class)
        override fun getReceiveBufferSize(): Int {
            return delegate.receiveBufferSize
        }

        override fun getRemoteSocketAddress(): SocketAddress {
            return delegate.remoteSocketAddress
        }

        @Throws(SocketException::class)
        override fun getReuseAddress(): Boolean {
            return delegate.reuseAddress
        }

        @Synchronized
        @Throws(SocketException::class)
        override fun getSendBufferSize(): Int {
            return delegate.sendBufferSize
        }

        @Throws(SocketException::class)
        override fun getSoLinger(): Int {
            return delegate.soLinger
        }

        @Synchronized
        @Throws(SocketException::class)
        override fun getSoTimeout(): Int {
            return delegate.soTimeout
        }

        @Throws(SocketException::class)
        override fun getTcpNoDelay(): Boolean {
            return delegate.tcpNoDelay
        }

        @Throws(SocketException::class)
        override fun getTrafficClass(): Int {
            return delegate.trafficClass
        }

        override fun isBound(): Boolean {
            return delegate.isBound
        }

        override fun isClosed(): Boolean {
            return delegate.isClosed
        }

        override fun isConnected(): Boolean {
            return delegate.isConnected
        }

        override fun isInputShutdown(): Boolean {
            return delegate.isInputShutdown
        }

        override fun isOutputShutdown(): Boolean {
            return delegate.isOutputShutdown
        }

        @Throws(IOException::class)
        override fun sendUrgentData(value: Int) {
            delegate.sendUrgentData(value)
        }

        @Throws(SocketException::class)
        override fun setKeepAlive(keepAlive: Boolean) {
            delegate.keepAlive = keepAlive
        }

        @Throws(SocketException::class)
        override fun setOOBInline(oobinline: Boolean) {
            delegate.oobInline = oobinline
        }

        override fun setPerformancePreferences(connectionTime: Int, latency: Int, bandwidth: Int) {
            delegate.setPerformancePreferences(connectionTime, latency, bandwidth)
        }

        @Synchronized
        @Throws(SocketException::class)
        override fun setReceiveBufferSize(size: Int) {
            delegate.receiveBufferSize = size
        }

        @Throws(SocketException::class)
        override fun setReuseAddress(reuse: Boolean) {
            delegate.reuseAddress = reuse
        }

        @Synchronized
        @Throws(SocketException::class)
        override fun setSendBufferSize(size: Int) {
            delegate.sendBufferSize = size
        }

        @Throws(SocketException::class)
        override fun setSoLinger(on: Boolean, timeout: Int) {
            delegate.setSoLinger(on, timeout)
        }

        @Synchronized
        @Throws(SocketException::class)
        override fun setSoTimeout(timeout: Int) {
            delegate.soTimeout = timeout
        }

        @Throws(SocketException::class)
        override fun setTcpNoDelay(on: Boolean) {
            delegate.tcpNoDelay = on
        }

        @Throws(SocketException::class)
        override fun setTrafficClass(value: Int) {
            delegate.trafficClass = value
        }

        @Throws(IOException::class)
        override fun shutdownInput() {
            delegate.shutdownInput()
        }

        @Throws(IOException::class)
        override fun shutdownOutput() {
            delegate.shutdownOutput()
        }

        override fun toString(): String {
            return delegate.toString()
        }

        override fun equals(o: Any?): Boolean {
            return delegate == o
        }
    }
}