package com.hippo.network;

import android.util.Log;
import com.hippo.ehviewer.Settings;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class EhSSLSocketFactoryLowSDK extends SSLSocketFactory {

    private final SSLSocketFactory mSSLSocketFactory;

    public EhSSLSocketFactoryLowSDK(SSLSocketFactory sslSocketFactory) {
        this.mSSLSocketFactory = sslSocketFactory;
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return mSSLSocketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return mSSLSocketFactory.getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket() throws IOException {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket());
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        if (!Settings.getDF()) {
            return ((SSLSocketFactory) getDefault()).createSocket(s, host, port, autoClose);
        }
        InetAddress address = s.getInetAddress();
        Log.d("EhSSLSocketFactory", "Host: " + host + " Address: " + address.getHostAddress());
        if (autoClose)
            s.close();
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(address, port));
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException, UnknownHostException {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException, UnknownHostException {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port, localHost, localPort));
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(host, port));
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return enableTLSOnSocket(mSSLSocketFactory.createSocket(address, port, localAddress, localPort));
    }

    private Socket enableTLSOnSocket(Socket socket) {
        if (socket instanceof SSLSocket)
            ((SSLSocket) socket).setEnabledProtocols(new String[] { "TLSv1", "TLSv1.1", "TLSv1.2", "TLSv1.3" });
        return socket;
    }
}
