package shadowsocks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.crypto.CryptoFactory;

import shadowsocks.util.GlobalConfig;

public class Main{

    private static Logger log = LogManager.getLogger(Main.class.getName());

    public static final String VERSION = "0.8.4";

    public static void main(String argv[])
    {
        log.info("Shadowsocks " + VERSION);

        if (argv.length != 1) {
            throw new RuntimeException("argvError ! ");
        }

        try {
            // 加载配置文件
            String configFile = argv[0];
            GlobalConfig.get().setConfigFile(configFile);
            GlobalConfig.getConfigFromFile();
        } catch (Exception e) {
            log.fatal("get config from file error",e);
            return;
        }

        //make sure this method could work.
        try{
            CryptoFactory.create(GlobalConfig.get().getMethod(), GlobalConfig.get().getPassword());
        }catch(Exception e){
            log.fatal("Error crypto method", e);
            return;
        }
        GlobalConfig.get().printConfig();
        new ShadowsocksVertx(GlobalConfig.get().isServerMode()).start();
    }
}
