package shadowsocks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/**
 * 本地测试
 */
public class TEST {

    private static Logger log = LogManager.getLogger(TEST.class);

    public static void main(String[] args) {

        // 绕过 HTTPS 证书
        HttpsURLConnection.setDefaultHostnameVerifier((urlHostName, session) -> true);

        testSimpleHttp();
    }



    private static void testSimpleHttp() {

        boolean preferIPv4Stack = Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Stack"));
        String localhost = preferIPv4Stack ? "0.0.0.0" : "::";

        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(localhost, GlobalConfig.get().getLocalPort()));
        HttpURLConnection proxyConnection = null;// 代理连接
        HttpURLConnection directConnection = null;// 直接连接
        try{
            URL url = new URL("http://www.example.com/");

            proxyConnection = (HttpURLConnection)url.openConnection(proxy);
            proxyConnection.setRequestMethod("GET");

            directConnection = (HttpURLConnection) url.openConnection();
            directConnection.setRequestMethod("GET");

            DataInputStream proxyData = new DataInputStream(proxyConnection.getInputStream());
            byte [] proxyArr = new byte[1024];
            proxyData.read(proxyArr);// 代理数据放入 proxyArr
            log.info("proxy \n\n " + new String(proxyArr,"UTF-8"));

            System.out.println("------------------------------------------------------------------------------------------");
            System.out.println("------------------------------------------------------------------------------------------");

            DataInputStream directData = new DataInputStream(directConnection.getInputStream());
            byte [] directArr = new byte[1024];
            directData.read(directArr);// 直连数据放入 directArr
            log.info("direct \n\n " + new String(directArr,"UTF-8"));

        }catch(IOException e){
            log.error("Failed with exception.", e);
        }finally{
            if (proxyConnection != null) {
                proxyConnection.disconnect();
            }
            if (directConnection != null) {
                directConnection.disconnect();
            }

        }
    }
}
