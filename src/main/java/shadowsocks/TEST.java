package shadowsocks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;

/**
 * 本地测试
 */
public class TEST {

    private static Logger log = LogManager.getLogger(TEST.class);

    public static void main(String[] args) {
        testSimpleHttp();
    }



    private static void testSimpleHttp() {

        ShadowsocksVertx server = new ShadowsocksVertx(true);
        ShadowsocksVertx client = new ShadowsocksVertx(false);

        server.start();
        client.start();

        //Wait 2s for server/client start.
        try{
            Thread.sleep(2000);
        }catch(Exception e){
            //ignore
        }

        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", GlobalConfig.get().getLocalPort()));
        HttpURLConnection proxyConnection = null;// 代理连接
        HttpURLConnection directConnection = null;// 直接连接
        try{
            URL url = new URL("https://www.baidu.com");

            proxyConnection = (HttpURLConnection)url.openConnection(proxy);
            proxyConnection.setRequestMethod("GET");

            directConnection = (HttpURLConnection) url.openConnection();
            directConnection.setRequestMethod("GET");

            DataInputStream proxyData = new DataInputStream(proxyConnection.getInputStream());
            byte [] result = new byte[10000];
            proxyData.read(result);// 代理数据放入 result
            log.info("proxy \n\n " + new String(result,"UTF-8"));

            System.out.println("------------------------------------------------------------------------------------------");
            System.out.println("------------------------------------------------------------------------------------------");

            DataInputStream directData = new DataInputStream(directConnection.getInputStream());
            byte [] expect = new byte[10000];
            directData.read(expect);// 直连数据放入 expect
            log.info("direct \n\n " + new String(expect,"UTF-8"));

        }catch(IOException e){
            log.error("Failed with exception.", e);
        }finally{
            if (proxyConnection != null) {
                proxyConnection.disconnect();
            }
            if (directConnection != null) {
                directConnection.disconnect();
            }
            server.stop();
            client.stop();
            //Wait 1s for server/client stop.
            try{
                Thread.sleep(1000);
            }catch(Exception e){
                //ignore
            }
        }
    }
}
