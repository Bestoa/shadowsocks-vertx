/*
 *   Copyright 2016 Author:NU11 bestoapache@gmail.com
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

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import org.json.JSONObject;
import org.json.JSONException;

public class GlobalConfig{

    public static Logger log = LogManager.getLogger(GlobalConfig.class.getName());

    private static GlobalConfig mConfig;

    private String mPassword;
    private String mMethod;
    private String mServer;
    private String mConfigFile;
    private int mPort;
    private int mLocalPort;
    private boolean mOneTimeAuth;
    private boolean mIsServerMode;
    /* UNIT second */
    private int mTimeout;

    final private static String DEFAULT_METHOD = "aes-256-cfb";
    final private static String DEFAULT_PASSWORD = "123456";
    final private static String DEFAULT_SERVER = "127.0.0.1";
    final private static int DEFAULT_PORT = 8388;
    final private static int DEFAULT_LOCAL_PORT = 9999;
    final private static int DEFAULT_TIMEOUT = 300;

    public void setTimeout(int t)
    {
        mTimeout = t;
    }
    public int getTimeout()
    {
        return mTimeout;
    }

    public void setPassowrd(String p)
    {
        mPassword = new String(p);
    }
    public String getPassword()
    {
        return mPassword;
    }

    public void setMethod(String m)
    {
        mMethod = new String(m);
    }
    public String getMethod()
    {
        return mMethod;
    }

    public void setServer(String s)
    {
        mServer = new String(s);
    }
    public String getServer()
    {
        return mServer;
    }

    public void setPort(int p)
    {
        mPort = p;
    }
    public int getPort()
    {
        return mPort;
    }

    public void setLocalPort(int p)
    {
        mLocalPort = p;
    }
    public int getLocalPort()
    {
        return mLocalPort;
    }

    public boolean isOTAEnabled()
    {
        return mOneTimeAuth;
    }
    public void setOTAEnabled(boolean enable){
        mOneTimeAuth = enable;
    }

    public boolean isServerMode(){
        return mIsServerMode;
    }
    public void setServerMode(boolean isServer){
        mIsServerMode = isServer;
    }

    public void setConfigFile(String name){
        mConfigFile = new String(name);
    }
    public String getConfigFile(){
        return mConfigFile;
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
        mMethod = DEFAULT_METHOD;
        mPassword = DEFAULT_PASSWORD;
        mServer = DEFAULT_SERVER;
        mPort = DEFAULT_PORT;
        mLocalPort = DEFAULT_LOCAL_PORT;
        mOneTimeAuth = false;
        mIsServerMode = false;
        mConfigFile = null;
        mTimeout = DEFAULT_TIMEOUT;
    }

    public void printConfig(){
        log.info("Current config is:");
        log.info("Mode [" + (isServerMode()?"Server":"Local") + "]");
        log.info("Crypto method [" + getMethod() + "]");
        log.info("Password [" + getPassword() + "]");
        log.info("Auth [" + isOTAEnabled() + "]");
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

    public static void getConfigFromFile(){
        String name = GlobalConfig.get().getConfigFile();
        if (name == null)
            return;
        String data = GlobalConfig.readConfigFile(name);

        JSONObject jsonobj = new JSONObject(data);
        try{
            String server = jsonobj.getString("server");
            log.debug("CFG:Server address: " + server);
            GlobalConfig.get().setServer(server);
        }catch(JSONException e){
            //No this config, ignore;
        }
        try{
            int port = jsonobj.getInt("server_port");
            log.debug("CFG:Port: " + port);
            GlobalConfig.get().setPort(port);
        }catch(JSONException e){
            //No this config, ignore;
        }
        try{
            int lport = jsonobj.getInt("local_port");
            log.debug("CFG:Local port: " + lport);
            GlobalConfig.get().setLocalPort(lport);
        }catch(JSONException e){
            //No this config, ignore;
        }
        try{
            String password = jsonobj.getString("password");
            log.debug("CFG:Password: " + password);
            GlobalConfig.get().setPassowrd(password);
        }catch(JSONException e){
            //No this config, ignore;
        }
        try{
            String method = jsonobj.getString("method");
            log.debug("CFG:Crypto method: " + method);
            GlobalConfig.get().setMethod(method);
        }catch(JSONException e){
            //No this config, ignore;
        }
        try{
            boolean auth = jsonobj.getBoolean("auth");
            log.debug("CFG:One time auth: " + auth);
            GlobalConfig.get().setOTAEnabled(auth);
        }catch(JSONException e){
            //No this config, ignore;
        }
        try{
            int timeout = jsonobj.getInt("timeout");
            log.debug("CFG:timeout: " + timeout);
            GlobalConfig.get().setTimeout(timeout);
        }catch(JSONException e){
            //No this config, ignore;
        }
    }
    public static void getConfigFromArgv(String argv[])
    {

        Getopt g = new Getopt("shadowsocks", argv, "SLm:k:p:as:l:c:t:");
        int c;
        String arg;
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
                    log.debug("CMD:Port: " + port);
                    GlobalConfig.get().setPort(port);
                    break;
                case 'a':
                    log.debug("CMD:OTA enforcing mode.");
                    GlobalConfig.get().setOTAEnabled(true);
                    break;
                case 'S':
                    log.debug("CMD:Server mode.");
                    GlobalConfig.get().setServerMode(true);
                    break;
                case 'L':
                    log.debug("CMD:Local mode.");
                    GlobalConfig.get().setServerMode(false);
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
                case '?':
                default:
                    help();
                    break;
            }
        }
    }

    public static LocalConfig createLocalConfig() {
        return new LocalConfig(GlobalConfig.get().getPassword(),
                GlobalConfig.get().getMethod(),
                GlobalConfig.get().getServer(),
                GlobalConfig.get().getPort(),
                GlobalConfig.get().getLocalPort(),
                GlobalConfig.get().isOTAEnabled(),
                GlobalConfig.get().getTimeout()
                );
    }

    private static void help()
    {
        //TODO
    }
}
