package shadowsocks;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
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

    private static boolean preferIPv4Stack = Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Stack"));
    private static String localhost = preferIPv4Stack ? "0.0.0.0" : "::";

    public static void main(String[] args) {

        testSimpleHttp();

        testVertxSocks5();
    }


    /**
     * vertx 的 socks5 端
     */
    private static void testVertxSocks5 () {

        System.out.println("------------------------------------------------------------------------------------------");
        System.out.println("------------------------------------------------------------------------------------------");

        Vertx vertx = Vertx.vertx();

        HttpClientOptions clientOptions = new HttpClientOptions()
                .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost(localhost).setPort(1080))
                .setSsl(false);

        HttpClient client = vertx.createHttpClient(clientOptions);

        client.getNow(80,"www.example.com","/", response -> response.bodyHandler(totalBuffer -> {
            // Now all the body has been read
            log.info("proxy2 \n\n " + totalBuffer);
        }));
    }



    private static void testSimpleHttp() {

        // 绕过 HTTPS 证书
        HttpsURLConnection.setDefaultHostnameVerifier((urlHostName, session) -> true);

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
