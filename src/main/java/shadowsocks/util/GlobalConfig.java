/*
 *   Copyright 2016 Author:Bestoa bestoapache@gmail.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package shadowsocks.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gnu.getopt.Getopt;
import gnu.getopt.LongOpt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import io.vertx.core.json.JsonObject;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalConfig{

    public static Logger log = LogManager.getLogger(GlobalConfig.class.getName());

    private static GlobalConfig mConfig;

    private ReentrantLock mLock = new ReentrantLock();

    private AtomicReference<String> mPassword;
    private AtomicReference<String> mMethod;
    private AtomicReference<String> mServer;
    private AtomicReference<String> mConfigFile;
    private AtomicInteger mPort;
    private AtomicInteger mLocalPort;
    private AtomicInteger mTimeout; /* UNIT second */
    private AtomicBoolean mIsServerMode;

    final private static String DEFAULT_METHOD = "aes-256-cfb";
    final private static String DEFAULT_PASSWORD = "123456";
    final private static String DEFAULT_SERVER = "127.0.0.1";
    final private static int DEFAULT_PORT = 8388;
    final private static int DEFAULT_LOCAL_PORT = 9999;
    final private static int DEFAULT_TIMEOUT = 300;

    final private static String SERVER_MODE = "server_mode";
    final private static String SERVER_ADDR = "server";
    final private static String LOCAL_PORT = "local_port";
    final private static String SERVER_PORT = "server_port";
    final private static String METHOD = "method";
    final private static String PASSWORD = "password";
    final private static String TIMEOUT = "timeout";
    final private static String HELP = "help";
    final private static String CONFIG = "config";

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
        mMethod = new AtomicReference<String>(DEFAULT_METHOD);
        mPassword = new AtomicReference<String>(DEFAULT_PASSWORD);
        mServer = new AtomicReference<String>(DEFAULT_SERVER);
        mPort = new AtomicInteger(DEFAULT_PORT);
        mLocalPort = new AtomicInteger(DEFAULT_LOCAL_PORT);
        mIsServerMode = new AtomicBoolean(false);
        mConfigFile = new AtomicReference<String>();
        mTimeout = new AtomicInteger(DEFAULT_TIMEOUT);
    }

    public void printConfig(){
        log.info("Current config is:");
        log.info("Mode [" + (isServerMode()?"Server":"Local") + "]");
        log.info("Crypto method [" + getMethod() + "]");
        log.info("Password [" + getPassword() + "]");
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
            log.error("Read config file " + name + " error:" + e.toString());
            return null;
        }
    }

    public static void getConfigFromFile() throws ClassCastException{
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
            int port = jsonobj.getInteger(SERVER_PORT).intValue();
            log.debug("CFG:Server port: " + port);
            GlobalConfig.get().setPort(port);
        }
        if (jsonobj.containsKey(LOCAL_PORT)) {
            int lport = jsonobj.getInteger(LOCAL_PORT).intValue();
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
            int timeout = jsonobj.getInteger(TIMEOUT).intValue();
            log.debug("CFG:Timeout: " + timeout);
            GlobalConfig.get().setTimeout(timeout);
        }
        if (jsonobj.containsKey(SERVER_MODE)) {
            boolean isServer = jsonobj.getBoolean(SERVER_MODE).booleanValue();
            log.debug("CFG:Running on server mode: " + isServer);
            GlobalConfig.get().setServerMode(isServer);
        }
    }
    public static boolean getConfigFromArgv(String argv[])
    {

        int c;
        String arg;

        LongOpt [] longopts = new LongOpt[9];
        longopts[0] = new LongOpt(SERVER_MODE, LongOpt.NO_ARGUMENT, null, 'S');
        longopts[1] = new LongOpt(METHOD, LongOpt.REQUIRED_ARGUMENT, null, 'm');
        longopts[2] = new LongOpt(PASSWORD, LongOpt.REQUIRED_ARGUMENT, null, 'k');
        longopts[3] = new LongOpt(SERVER_PORT, LongOpt.REQUIRED_ARGUMENT, null, 'p');
        longopts[4] = new LongOpt(SERVER_ADDR, LongOpt.REQUIRED_ARGUMENT, null, 's');
        longopts[5] = new LongOpt(LOCAL_PORT, LongOpt.REQUIRED_ARGUMENT, null, 'l');
        longopts[6] = new LongOpt(CONFIG, LongOpt.REQUIRED_ARGUMENT, null, 'c');
        longopts[7] = new LongOpt(TIMEOUT, LongOpt.REQUIRED_ARGUMENT, null, 't');
        longopts[8] = new LongOpt(HELP, LongOpt.NO_ARGUMENT, null, 'h');

        Getopt g = new Getopt("shadowsocks", argv, "Sm:k:p:s:l:c:t:h", longopts);

        while ((c = g.getopt()) != -1)
        {
            switch(c)
            {
                case 'm':
                    arg = g.getOptarg();
                    log.debug("CMD:Crypto method: " + arg);
                    GlobalConfig.get().setMethod(arg);
                    break;
                case 'k':
                    arg = g.getOptarg();
                    log.debug("CMD:Password: " + arg);
                    GlobalConfig.get().setPassowrd(arg);
                    break;
                case 'p':
                    arg = g.getOptarg();
                    int port = Integer.parseInt(arg);
                    log.debug("CMD:Server port: " + port);
                    GlobalConfig.get().setPort(port);
                    break;
                case 'S':
                    log.debug("CMD:Server mode.");
                    GlobalConfig.get().setServerMode(true);
                    break;
                case 's':
                    arg = g.getOptarg();
                    log.debug("CMD:Server address: " + arg);
                    GlobalConfig.get().setServer(arg);
                    break;
                case 'l':
                    arg = g.getOptarg();
                    int lport = Integer.parseInt(arg);
                    log.debug("CMD:Local port: " + lport);
                    GlobalConfig.get().setLocalPort(lport);
                    break;
                case 'c':
                    arg = g.getOptarg();
                    log.debug("CMD:Config file: " + arg);
                    GlobalConfig.get().setConfigFile(arg);
                    break;
                case 't':
                    arg = g.getOptarg();
                    int timeout = Integer.parseInt(arg);
                    log.debug("CMD:timeout: " + timeout);
                    GlobalConfig.get().setTimeout(timeout);
                    break;
                case 'h':
                case '?':
                default:
                    help();
                    return false;
            }
        }
        return true;
    }

    public static LocalConfig createLocalConfig() {
        LocalConfig lc = null;
        GlobalConfig.get().getLock();
        lc = new LocalConfig(GlobalConfig.get().getPassword(),
                GlobalConfig.get().getMethod(),
                GlobalConfig.get().getServer(),
                GlobalConfig.get().getPort(),
                GlobalConfig.get().getLocalPort(),
                GlobalConfig.get().getTimeout()
                );
        GlobalConfig.get().releaseLock();
        return lc;
    }

    private static void help()
    {
        System.out.println("Usage:\n" +
                "   -m crypto method\n" +
                "   -k password\n" +
                "   -p bind port(server)/remote port(client)\n" +
                "   -l local port\n" +
                "   -s server\n" +
                "   -S server mode\n" +
                "   -c config file\n" +
                "   -t timeout(unit is second)\n" +
                "   -h show help.\n");
    }
}
