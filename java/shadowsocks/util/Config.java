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
    final private static int DEFAULT_PORT = 2048;


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
