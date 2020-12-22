package com.hippo.network;

import android.util.Log;

import com.hippo.ehviewer.Settings;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import javax.net.ssl.SSLSocketFactory;

public class EhSSLSocketFactory extends SSLSocketFactory {
    @Override
    public String[] getDefaultCipherSuites() {
        return ((SSLSocketFactory) getDefault()).getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return ((SSLSocketFactory) getDefault()).getSupportedCipherSuites();
    }

    @Override
    public Socket createSocket(Socket s, String host, int port, boolean autoClose) throws IOException {
        if (!Settings.getDF()) {
            return ((SSLSocketFactory) getDefault()).createSocket(s, host, port, autoClose);
        }
        InetAddress address = s.getInetAddress();
        Log.d("EhSSLSocketFactory", "Host: " + host + " Address: " + address.getHostAddress());
        if (autoClose) s.close();
        return getDefault().createSocket(address, port);
    }

    @Override
    public Socket createSocket(String host, int port) throws IOException {
        return getDefault().createSocket(host, port);
    }

    @Override
    public Socket createSocket(String host, int port, InetAddress localHost, int localPort) throws IOException {
        return getDefault().createSocket(host, port, localHost, localPort);
    }

    @Override
    public Socket createSocket(InetAddress host, int port) throws IOException {
        return getDefault().createSocket(host, port);
    }

    @Override
    public Socket createSocket(InetAddress address, int port, InetAddress localAddress, int localPort) throws IOException {
        return getDefault().createSocket(address, port, localAddress, localPort);
    }


}
