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

import gnu.getopt.Getopt;

public class Config{

    private static Config mConfig;

    private String mPassword;
    private String mMethod;
    private String mServer;
    private int mPort;
    private int mLocalPort;
    private boolean mOneTimeAuth;
    private boolean mIsServerMode;

    final private static String DEFAULT_METHOD = "aes-256-cfb";
    final private static String DEFAULT_PASSWORD = "123456";
    final private static String DEFAULT_SERVER = "127.0.0.1";
    final private static int DEFAULT_PORT = 8388;
    final private static int DEFAULT_LOCAL_PORT = 9999;

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

    public synchronized static Config get()
    {
        if (mConfig == null)
        {
            mConfig = new Config();
        }
        return mConfig;
    }

    public Config()
    {
        mMethod = DEFAULT_METHOD;
        mPassword = DEFAULT_PASSWORD;
        mServer = DEFAULT_SERVER;
        mPort = DEFAULT_PORT;
        mLocalPort = DEFAULT_LOCAL_PORT;
        mOneTimeAuth = false;
        mIsServerMode = true;
    }

    public static void getConfigFromArgv(String argv[])
    {

        Getopt g = new Getopt("shadowsocks", argv, "SLm:k:p:as:l:");
        int c;
        String arg;
        while ((c = g.getopt()) != -1)
        {
            switch(c)
            {
                case 'm':
                    arg = g.getOptarg();
                    System.out.println("Get method: " + arg);
                    Config.get().setMethod(arg);
                    break;
                case 'k':
                    arg = g.getOptarg();
                    System.out.println("Get key: " + arg);
                    Config.get().setPassowrd(arg);
                    break;
                case 'p':
                    arg = g.getOptarg();
                    int port = Integer.parseInt(arg);
                    System.out.println("Get port: " + port);
                    Config.get().setPort(port);
                    break;
                case 'a':
                    System.out.println("OTA enforcing mode.");
                    Config.get().setOTAEnabled(true);
                    break;
                case 'S':
                    System.out.println("Server mode.");
                    Config.get().setServerMode(true);
                    break;
                case 'L':
                    System.out.println("Local mode.");
                    Config.get().setServerMode(false);
                    break;
                case 's':
                    arg = g.getOptarg();
                    System.out.println("Get server: " + arg);
                    Config.get().setServer(arg);
                    break;
                case 'l':
                    arg = g.getOptarg();
                    int lport = Integer.parseInt(arg);
                    System.out.println("Get local port: " + lport);
                    Config.get().setLocalPort(lport);
                    break;
                case '?':
                default:
                    help();
                    break;
            }
        }
    }

    private static void help()
    {
        //TODO
        System.out.println("HELP");
    }
}
