package shadowsocks.util;

public class LocalConfig{
    public String password;
    public String method;
    public String server;
    public int serverPort;
    public int localPort;
    public int timeout;
    public int ivLen;

    public LocalConfig(String k, String m, String s, int p, int lp, int t, int i){
        password = k;
        method = m;
        server = s;
        serverPort = p;
        localPort = lp;
        timeout = t;
        ivLen = i;
    }
}
