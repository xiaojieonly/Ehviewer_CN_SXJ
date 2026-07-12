package com.hippo.ehviewer.sync.nas;

import androidx.annotation.NonNull;

import com.hierynomus.msfscc.FileAttributes;
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation;
import com.hierynomus.smbj.SMBClient;
import com.hierynomus.smbj.SmbConfig;
import com.hierynomus.smbj.auth.AuthenticationContext;
import com.hierynomus.smbj.connection.Connection;
import com.hierynomus.smbj.session.Session;
import com.hierynomus.smbj.share.DiskShare;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.net.InetAddress;
import java.util.concurrent.TimeUnit;

import jcifs.CIFSContext;
import jcifs.CloseableIterator;
import jcifs.SmbConstants;
import jcifs.SmbResource;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;

/** Network browsing used only by the guided NAS setup UI. */
public final class NasBrowserClient {
    private static final long TIMEOUT_SECONDS = 20L;

    private NasBrowserClient() {}

    @NonNull
    public static List<String> listShares(@NonNull String host, @NonNull String domain,
                                          @NonNull String username, @NonNull char[] password)
            throws Exception {
        Properties properties = new Properties();
        properties.setProperty("jcifs.smb.client.minVersion", "SMB202");
        properties.setProperty("jcifs.smb.client.maxVersion", "SMB311");
        properties.setProperty("jcifs.smb.client.connTimeout", "20000");
        properties.setProperty("jcifs.smb.client.responseTimeout", "20000");
        properties.setProperty("jcifs.smb.client.soTimeout", "20000");
        // Guest sessions have no signing key. This affects only IPC$ share discovery;
        // gallery transfer continues to use SMBJ with its normal signing behavior.
        properties.setProperty("jcifs.smb.client.ipcSigningEnforced", "false");
        CIFSContext base = new BaseContext(new PropertyConfiguration(properties));
        CIFSContext authenticated = username.isEmpty()
                ? base.withGuestCrendentials()
                : base.withCredentials(new NtlmPasswordAuthenticator(domain, username,
                new String(password)));
        List<String> result = new ArrayList<>();
        try (SmbResource server = authenticated.get("smb://" + host + "/");
             CloseableIterator<SmbResource> children = server.children()) {
            while (children.hasNext()) {
                SmbResource child = children.next();
                try {
                    if (child.getType() != SmbConstants.TYPE_SHARE) continue;
                    String name = trimTrailingSlash(child.getName());
                    if (name.isEmpty() || "IPC$".equalsIgnoreCase(name)) continue;
                    result.add(name);
                } finally {
                    child.close();
                }
            }
        } finally {
            try {
                authenticated.close();
            } finally {
                if (authenticated != base) base.close();
            }
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    @NonNull
    public static List<String> listDirectories(@NonNull NasSyncConfig config,
                                                @NonNull String directory) throws Exception {
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        List<String> result = new ArrayList<>();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                String remote = directory.replace('/', '\\');
                for (FileIdBothDirectoryInformation info : share.list(remote)) {
                    String name = info.getFileName();
                    if (".".equals(name) || "..".equals(name)) continue;
                    long attributes = info.getFileAttributes();
                    boolean isDirectory = (attributes
                            & FileAttributes.FILE_ATTRIBUTE_DIRECTORY.getValue()) != 0;
                    boolean isReparsePoint = (attributes
                            & FileAttributes.FILE_ATTRIBUTE_REPARSE_POINT.getValue()) != 0;
                    if (isDirectory && !isReparsePoint) result.add(name);
                }
            }
        } finally {
            config.clearPassword();
        }
        Collections.sort(result, String.CASE_INSENSITIVE_ORDER);
        return result;
    }

    @NonNull
    public static String probe(@NonNull NasSyncConfig config) throws Exception {
        String address = InetAddress.getByName(config.host).getHostAddress();
        SmbConfig smbConfig = SmbConfig.builder()
                .withTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .withSoTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build();
        try (SMBClient client = new SMBClient(smbConfig);
             Connection connection = client.connect(config.host)) {
            AuthenticationContext authentication = config.username.isEmpty()
                    ? AuthenticationContext.guest()
                    : new AuthenticationContext(config.username, config.password, config.domain);
            try (Session session = connection.authenticate(authentication);
                 DiskShare share = (DiskShare) session.connectShare(config.share)) {
                if (!config.remoteDirectory.isEmpty()
                        && !share.folderExists(config.remoteDirectory)) {
                    throw new java.io.IOException("Configured NAS directory is unavailable");
                }
            }
        } finally {
            config.clearPassword();
        }
        return address;
    }

    @NonNull
    private static String trimTrailingSlash(@NonNull String value) {
        String result = value.trim();
        while (result.endsWith("/") || result.endsWith("\\")) {
            result = result.substring(0, result.length() - 1);
        }
        int slash = Math.max(result.lastIndexOf('/'), result.lastIndexOf('\\'));
        return slash >= 0 ? result.substring(slash + 1) : result;
    }
}
