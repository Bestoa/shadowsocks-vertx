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
    private Object portLock = new Object();
    private int mPort;

    final private static String DEFAULT_METHOD = "aes-256-cfb";
    final private static String DEFAULT_PASSWORD = "123456";
    final private static int DEFAULT_PORT = 8388;


    public void setPassowrd(String p)
    {
        synchronized(mPassword){
            mPassword = new String(p);
        }
    }
    
    public String getPassword()
    {
        synchronized(mPassword){
            return mPassword;
        }
    }

    public void setMethod(String m)
    {
        synchronized(mMethod){
            mMethod = new String(m);
        }
    }

    public String getMethod()
    {
        synchronized(mMethod){
            return mMethod;
        }
    }

    public void setPort(int p)
    {
        synchronized(portLock){
            mPort = p;
        }
    }
    public int getPort()
    {
        synchronized(portLock){
            return mPort;
        }
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
        mPort = DEFAULT_PORT;
    }

    public static void getConfigFromArgv(String argv[])
    {

        Getopt g = new Getopt("shadowsocks", argv, "m:k:p:");
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
                case '?':
                default:
                    help();
                    break;
            }
        }
    }

    private static void help()
    {
        System.out.println("HELP");
    }
}
