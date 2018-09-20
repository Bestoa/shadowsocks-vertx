package shadowsocks;

import io.vertx.core.json.JsonObject;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalConfig{

    private static Logger log = LogManager.getLogger(GlobalConfig.class.getName());

    private static GlobalConfig mConfig;

    private ReentrantLock mLock = new ReentrantLock();

    private AtomicReference<String> mPassword;
    private AtomicReference<String> mMethod;
    private AtomicReference<String> mServer;
    private AtomicReference<String> mConfigFile;
    private AtomicInteger mPort;
    private AtomicInteger mLocalPort;
    private AtomicInteger mTimeout; /* 单位毫秒 */
    private AtomicBoolean mIsServerMode;
    private AtomicInteger mIvLen;// IV 长度

    final private static String DEFAULT_METHOD = "rc4-md5";// 效率最高
    final private static String DEFAULT_PASSWORD = "zxcvbnm";
    final private static String DEFAULT_SERVER = "127.0.0.1";
    final private static int DEFAULT_PORT = 8388;
    final private static int DEFAULT_LOCAL_PORT = 1080;
    final private static int DEFAULT_TIMEOUT = 2000;
    final private static int DEFAULT_IV_LEN = 16;// 默认16，兼容原生ss的rc4-md5

    final static String SERVER_MODE = "server_mode";
    final static String SERVER_ADDR = "server";
    final static String LOCAL_PORT = "local_port";
    final static String SERVER_PORT = "server_port";
    final static String METHOD = "method";
    final static String PASSWORD = "password";
    final static String TIMEOUT = "timeout";
    final static String IV_LEN = "iv_len";

    //Lock
    public void getLock() {
        mLock.lock();
    }
    public void releaseLock() {
        mLock.unlock();
    }

    //Timeout
    public void setTimeout(int t) {
        mTimeout.set(t);
    }
    public int getTimeout() {
        return mTimeout.get();
    }

    //Password(Key)
    public void setPassowrd(String p) {
        mPassword.set(p);
    }
    public String getPassword() {
        return mPassword.get();
    }

    //Method
    public void setMethod(String m) {
        mMethod.set(m);
    }
    public String getMethod() {
        return mMethod.get();
    }

    //Server
    public void setServer(String s) {
        mServer.set(s);
    }
    public String getServer() {
        return mServer.get();
    }

    //Server port
    public void setPort(int p) {
        mPort.set(p);
    }
    public int getPort() {
        return mPort.get();
    }

    //Local port
    public void setLocalPort(int p) {
        mLocalPort.set(p);
    }
    public int getLocalPort() {
        return mLocalPort.get();
    }

    //Running in server/local mode
    private void setServerMode(boolean isServer){
        mIsServerMode.set(isServer);
    }
    public boolean isServerMode(){
        return mIsServerMode.get();
    }

    //Config
    public void setConfigFile(String name){
        mConfigFile.set(name);
    }
    public String getConfigFile(){
        return mConfigFile.get();
    }

    // iv len
    public void setIvLen (int i) {
        mIvLen.set(i);
    }

    public int getIvLen(){
        return mIvLen.get();
    }

    public synchronized static GlobalConfig get()
    {
        if (mConfig == null)
        {
            mConfig = new GlobalConfig();
        }
        return mConfig;
    }

    public GlobalConfig()
    {
        mMethod = new AtomicReference<>(DEFAULT_METHOD);
        mPassword = new AtomicReference<>(DEFAULT_PASSWORD);
        mServer = new AtomicReference<>(DEFAULT_SERVER);
        mPort = new AtomicInteger(DEFAULT_PORT);
        mLocalPort = new AtomicInteger(DEFAULT_LOCAL_PORT);
        mIsServerMode = new AtomicBoolean(false);
        mConfigFile = new AtomicReference<>();
        mTimeout = new AtomicInteger(DEFAULT_TIMEOUT);
        mIvLen = new AtomicInteger(DEFAULT_IV_LEN);
    }

    public void printConfig(){
        log.info("Current config is:");
        log.info("Mode [" + (isServerMode()?"Server":"Local") + "]");
        log.info("Crypto method [" + getMethod() + "]");
        log.info("Password [" + getPassword() + "]");
        log.info("Iv len [" + getIvLen() + "]");
        if (isServerMode()) {
            log.info("Bind port [" + getPort() + "]");
        }else{
            log.info("Server [" + getServer() + "]");
            log.info("Server port [" + getPort() + "]");
            log.info("Local port [" + getLocalPort() + "]");
        }
        log.info("Timeout [" + getTimeout() + "]");
    }

    public static String readConfigFile(String name){
        try{
            BufferedReader reader = new BufferedReader(new FileReader(name));
            char [] data = new char[4096]; /*4096*/
            int size = reader.read(data, 0, data.length);
            if (size < 0)
                return null;
            return new String(data);
        }catch(IOException e){
            log.error("Read config file " + name + " error.", e);
            return null;
        }
    }

    public static void getConfigFromFile() {
        String name = GlobalConfig.get().getConfigFile();
        if (name == null)
            return;
        String data = GlobalConfig.readConfigFile(name);

        JsonObject jsonobj = new JsonObject(data);

        if (jsonobj.containsKey(SERVER_ADDR)) {
            String server = jsonobj.getString(SERVER_ADDR);
            log.debug("CFG:Server address: " + server);
            GlobalConfig.get().setServer(server);
        }
        if (jsonobj.containsKey(SERVER_PORT)) {
            int port = jsonobj.getInteger(SERVER_PORT);
            log.debug("CFG:Server port: " + port);
            GlobalConfig.get().setPort(port);
        }
        if (jsonobj.containsKey(LOCAL_PORT)) {
            int lport = jsonobj.getInteger(LOCAL_PORT);
            log.debug("CFG:Local port: " + lport);
            GlobalConfig.get().setLocalPort(lport);
        }
        if (jsonobj.containsKey(PASSWORD)) {
            String password = jsonobj.getString(PASSWORD);
            log.debug("CFG:Password: " + password);
            GlobalConfig.get().setPassowrd(password);
        }
        if (jsonobj.containsKey(METHOD)) {
            String method = jsonobj.getString(METHOD);
            log.debug("CFG:Crypto method: " + method);
            GlobalConfig.get().setMethod(method);
        }
        if (jsonobj.containsKey(TIMEOUT)) {
            int timeout = jsonobj.getInteger(TIMEOUT);
            log.debug("CFG:Timeout: " + timeout);
            GlobalConfig.get().setTimeout(timeout);
        }
        if (jsonobj.containsKey(SERVER_MODE)) {
            boolean isServer = jsonobj.getBoolean(SERVER_MODE);
            log.debug("CFG:Running on server mode: " + isServer);
            GlobalConfig.get().setServerMode(isServer);
        }

        if (jsonobj.containsKey(IV_LEN)) {
            Integer ivLen = jsonobj.getInteger(IV_LEN);
            log.debug("CFG:IV len : " + ivLen);
            GlobalConfig.get().setIvLen(ivLen);
        }
    }

}
