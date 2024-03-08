package com.hippo.network

import android.util.Log
import com.hippo.ehviewer.Settings
import java.io.IOException
import java.net.InetAddress
import java.net.Socket
import java.net.UnknownHostException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

class EhSSLSocketFactoryLowSDK(private val mSSLSocketFactory: SSLSocketFactory) :
    SSLSocketFactory() {
    override fun getDefaultCipherSuites(): Array<String> {
        return mSSLSocketFactory.defaultCipherSuites
    }

    override fun getSupportedCipherSuites(): Array<String> {
        return mSSLSocketFactory.supportedCipherSuites
    }

    @Throws(IOException::class)
    override fun createSocket(): Socket {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket())
    }

    @Throws(IOException::class)
    override fun createSocket(s: Socket, host: String, port: Int, autoClose: Boolean): Socket {
        if (!Settings.getDF()) {
            return (getDefault() as SSLSocketFactory).createSocket(s, host, port, autoClose)
        }
        val address = s.inetAddress
        Log.d("EhSSLSocketFactory", "Host: " + host + " Address: " + address.hostAddress)
        if (autoClose) s.close()
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(address, port))
    }

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(host: String, port: Int): Socket {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port))
    }

    @Throws(IOException::class, UnknownHostException::class)
    override fun createSocket(
        host: String,
        port: Int,
        localHost: InetAddress,
        localPort: Int
    ): Socket {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port, localHost, localPort))
    }

    @Throws(IOException::class)
    override fun createSocket(host: InetAddress, port: Int): Socket {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port))
    }

    @Throws(IOException::class)
    override fun createSocket(
        address: InetAddress,
        port: Int,
        localAddress: InetAddress,
        localPort: Int
    ): Socket {
        return enableTLSOnSocket(
            mSSLSocketFactory.createSocket(
                address,
                port,
                localAddress,
                localPort
            )
        )
    }

    private fun enableTLSOnSocket(socket: Socket): Socket {
        if (socket is SSLSocket) socket.enabledProtocols =
            arrayOf("TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3")
        return socket
    }
}