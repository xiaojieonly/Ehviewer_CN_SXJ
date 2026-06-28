package com.hippo.ehviewer.smb;

public class SmbConfig {
    private String host;
    private int port;
    private String share;
    private String path;
    private SmbLoginMode loginMode;
    private String username;
    private String password;

    public SmbConfig(String host, int port, String share, String path,
                     SmbLoginMode loginMode, String username, String password) {
        this.host = host;
        this.port = port;
        this.share = share;
        this.path = path;
        this.loginMode = loginMode;
        this.username = username;
        this.password = password;
    }

    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getShare() { return share; }
    public String getPath() { return path; }
    public SmbLoginMode getLoginMode() { return loginMode; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    public String toSmbUrl() {
        return "smb://" + host + ":" + port + "/" + share + "/" + path;
    }
}
