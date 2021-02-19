package com.hippo.network;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class EhX509TrustManager implements X509TrustManager {
//    @SuppressLint("TrustAllX509TrustManager")
//    @Override
//    public void checkClientTrusted(X509Certificate[] chain, String authType) {
//
//    }
//
//    @SuppressLint("TrustAllX509TrustManager")
//    @Override
//    public void checkServerTrusted(X509Certificate[] chain, String authType) {
//
//    }
//
//    @Override
//    public X509Certificate[] getAcceptedIssuers() {
//        return new X509Certificate[0];
//    }
    @Override
    public void checkClientTrusted(X509Certificate[] chain, String authType) throws CertificateException {

    }

    @Override
    public void checkServerTrusted(X509Certificate[] chain, String authType) throws CertificateException {
        if (chain == null) {
            throw new CertificateException("checkServerTrusted: X509Certificate array is null");
        }
        if (chain.length < 1) {
            throw new CertificateException("checkServerTrusted: X509Certificate is empty");
        }
        if (!(null != authType && authType.equals("ECDHE_RSA"))) {
            throw new CertificateException("checkServerTrusted: AuthType is not ECDHE_RSA");
        }

        //检查所有证书
        try {
            TrustManagerFactory factory = TrustManagerFactory.getInstance("X509");
            factory.init((KeyStore) null);
            for (TrustManager trustManager : factory.getTrustManagers()) {
                ((X509TrustManager) trustManager).checkServerTrusted(chain, authType);
            }
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (KeyStoreException e) {
            e.printStackTrace();
        }

        //获取本地证书中的信息
        String clientEncoded = "";
        String clientSubject = "";
        String clientIssUser = "";
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");

            String cer = "-----BEGIN CERTIFICATE-----\n" +
                    "MIIFuzCCBKOgAwIBAgIRAMILDRZOaGOKV70F36win8owDQYJKoZIhvcNAQELBQAw\n" +
                    "XzELMAkGA1UEBhMCRlIxDjAMBgNVBAgTBVBhcmlzMQ4wDAYDVQQHEwVQYXJpczEO\n" +
                    "MAwGA1UEChMFR2FuZGkxIDAeBgNVBAMTF0dhbmRpIFN0YW5kYXJkIFNTTCBDQSAy\n" +
                    "MB4XDTIxMDEyNjAwMDAwMFoXDTIyMDIyNjIzNTk1OVowGTEXMBUGA1UEAwwOKi5l\n" +
                    "LWhlbnRhaS5vcmcwggEiMA0GCSqGSIb3DQEBAQUAA4IBDwAwggEKAoIBAQDZftEy\n" +
                    "2IQKxsPs+g+2tojnhAnDTYojnPiNZWG6+ue/S8+sb9udtDr540iR0MgZe1Qn64hR\n" +
                    "YWeo/lTQ8MjlnFBCkfnwpbXiYDNtJfH8i2H30k638hLOpu/hXX77Gq8LOWqv8IQc\n" +
                    "H+YT7wJ40DeqoCZkCJDQYwcwPbLdVW9hcQsutB8VwHosaJFViC/XRSmvpFITyojt\n" +
                    "siZ0p9OzV+RwklgJRz8MMXAlrKSzve8vcAVcaKtq3jh9Dl+TLIj7ljJk5qHk2I1a\n" +
                    "pPunYBJbcMwmeX73YUFdDnQzu85+TDxWGXio41XeNMSkItaBu0rnfW6fxeEIBWkC\n" +
                    "628peCtkGZOjpcnPAgMBAAGjggK2MIICsjAfBgNVHSMEGDAWgBSzkKfYya9OzWE8\n" +
                    "n3ytXX9B/Wkw6jAdBgNVHQ4EFgQUpfqwNiX/vR6IfpcDmkfCuFjqXeYwDgYDVR0P\n" +
                    "AQH/BAQDAgWgMAwGA1UdEwEB/wQCMAAwHQYDVR0lBBYwFAYIKwYBBQUHAwEGCCsG\n" +
                    "AQUFBwMCMEsGA1UdIAREMEIwNgYLKwYBBAGyMQECAhowJzAlBggrBgEFBQcCARYZ\n" +
                    "aHR0cHM6Ly9jcHMudXNlcnRydXN0LmNvbTAIBgZngQwBAgEwQQYDVR0fBDowODA2\n" +
                    "oDSgMoYwaHR0cDovL2NybC51c2VydHJ1c3QuY29tL0dhbmRpU3RhbmRhcmRTU0xD\n" +
                    "QTIuY3JsMHMGCCsGAQUFBwEBBGcwZTA8BggrBgEFBQcwAoYwaHR0cDovL2NydC51\n" +
                    "c2VydHJ1c3QuY29tL0dhbmRpU3RhbmRhcmRTU0xDQTIuY3J0MCUGCCsGAQUFBzAB\n" +
                    "hhlodHRwOi8vb2NzcC51c2VydHJ1c3QuY29tMCcGA1UdEQQgMB6CDiouZS1oZW50\n" +
                    "YWkub3JnggxlLWhlbnRhaS5vcmcwggEDBgorBgEEAdZ5AgQCBIH0BIHxAO8AdQBG\n" +
                    "pVXrdfqRIDC1oolp9PN9ESxBdL79SbiFq/L8cP5tRwAAAXc+E5+BAAAEAwBGMEQC\n" +
                    "IB/V+c+kB+QABfXA+Un5JweR7QR0GEyMbIWgtQ4QRhRVAiBAd9aaTvbVKxBjhvsu\n" +
                    "7TeFBMly2BwmjI8/P0gsGHtoHgB2AN+lXqtogk8fbK3uuF9OPlrqzaISpGpejjsS\n" +
                    "wCBEXCpzAAABdz4ToEIAAAQDAEcwRQIhAIaHCaap+4k7In+wNcMKGYV4uPXWYXzs\n" +
                    "IVLuFOCuIbq4AiB5SuoWDvrHjrGQgGXho1izwM5qvMEOQo7T80umOHr6MzANBgkq\n" +
                    "hkiG9w0BAQsFAAOCAQEABeLA6d42dffZftbLRRaWVPZQ2mhv81/Fcq/O7+QEBXNg\n" +
                    "g6N3ZcWw3TghOa8iqhdcMLi4GUqWshagC8l0L1TqKW2gpZPuAN0U2wrQz7eCXlTw\n" +
                    "fveg5TUALO5Ct54IBBTfp4jBSqfjQcs04yfPsl1+l4QKiabnx5EuVbcQ2D0Xqen4\n" +
                    "Me5Akz8LWNGN5lt9Yslf0B5jwWMqKR34+WwNgWYkMUlUpNvzsKBb/hFw/8AUsiBE\n" +
                    "V6NF234tAsoi7A/lo3+nhhXdZD0y3n+KrTbp5t5lTuIAxoWV8VYlUKWGugS16Rh6\n" +
                    "PCei576sa9igHSPozx7ehMmVuMnkKjzDopsDfDnZCQ==\n" +
                    "-----END CERTIFICATE-----";
            InputStream inputStream = null;
            inputStream.read(cer.getBytes());
//            getAssets().open("ehentai.cer");
            X509Certificate clientCertificate = (X509Certificate) certificateFactory.generateCertificate(inputStream);
            clientEncoded = new BigInteger(1, clientCertificate.getPublicKey().getEncoded()).toString(16);
            clientSubject = clientCertificate.getSubjectDN().getName();
            clientIssUser = clientCertificate.getIssuerDN().getName();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //获取网络中的证书信息
        X509Certificate certificate = chain[0];
        PublicKey publicKey = certificate.getPublicKey();
        String serverEncoded = new BigInteger(1, publicKey.getEncoded()).toString(16);

        if (!clientEncoded.equals(serverEncoded)) {
            throw new CertificateException("server's PublicKey is not equals to client's PublicKey");
        }
        String subject = certificate.getSubjectDN().getName();
        if (!clientSubject.equals(subject)) {
            throw new CertificateException("server's subject is not equals to client's subject");
        }
        String issuser = certificate.getIssuerDN().getName();
        if (!clientIssUser.equals(issuser)) {
            throw new CertificateException("server's issuser is not equals to client's issuser");
        }
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }


}
