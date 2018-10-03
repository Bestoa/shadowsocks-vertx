package shadowsocks;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.net.ProxyOptions;
import io.vertx.core.net.ProxyType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.HttpsURLConnection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;

/**
 * socks5 客户端测试
 */
public class TEST {

    private static Logger log = LogManager.getLogger(TEST.class);

    private static boolean preferIPv4Stack = Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Stack"));
    private static String localhost = preferIPv4Stack ? "0.0.0.0" : "::";

    private static final int BYTE_MAX = 1024 * 1024;

    public static void main(String[] args) {

        String host;

        int port;

        String uri;

        int type;

        if (args == null || args.length != 4) {
            type = 1;
            host = "www.example.com";
            port = 443;
            uri = "/";
        } else {
            type = Integer.parseInt(args[0]);
            host = args[1];
            port = Integer.parseInt(args[2]);
            uri = args[3];
        }


        /*
         * 1 直接连接
         * 2 socks5 本地 DNS
         * 3 socks5 远程 DNS
         */
        if (type == 1) {
            testDirect(host, port, uri);
        } else if (type == 2) {
            testSocks5(host, port, uri);
        } else if (type == 3) {
            testSocks5_(host, port, uri);
        } else {
            log.error("type error  " + type);
        }
    }


    private static void testDirect(String host, int port, String uri) {
        String protocol = preTest(port);
        if (protocol == null) return;


        HttpURLConnection directConnection = null;
        try {
            URL url = new URL(protocol, host, port, uri);
            directConnection = (HttpURLConnection) url.openConnection();
            directConnection.setRequestMethod("GET");

            InputStream directInput = directConnection.getInputStream();
            byte[] directArr = readInfoStream(directInput);
            System.out.println("----------------------------------------------------------------\n\n " + new String(directArr, "UTF-8"));

        } catch (Exception e) {
            log.error("Failed with exception.", e);
        } finally {
            if (directConnection != null) {
                directConnection.disconnect();
            }
        }
    }



    private static void testSocks5(String host, int port, String uri) {
        String protocol = preTest(port);
        if (protocol == null) return;

        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(localhost, 1080));
        HttpURLConnection proxyConnection = null;
        try {
            URL url = new URL(protocol, host, port, uri);

            proxyConnection = (HttpURLConnection) url.openConnection(proxy);
            proxyConnection.setRequestMethod("GET");

            InputStream proxyInput = proxyConnection.getInputStream();
            byte[] proxyArr = readInfoStream(proxyInput);
            System.out.println("----------------------------------------------------------------\n\n " + new String(proxyArr, "UTF-8"));

        } catch (Exception e) {
            log.error("Failed with exception.", e);
        } finally {
            if (proxyConnection != null) {
                proxyConnection.disconnect();
            }
        }

    }


    private static String preTest(int port) {
        // 绕过 HTTPS 证书
        HttpsURLConnection.setDefaultHostnameVerifier((urlHostName, session) -> true);

        String protocol;

        if (port == 443) {
            protocol = "https";
        } else if (port == 80) {
            protocol = "http";
        } else {
            log.error("port error ! " + port);
            return null;
        }
        return protocol;
    }

    public static byte[] readInfoStream(InputStream input) throws Exception {
        byte[] bcache = new byte[1024];
        int readSize = 0;
        int totalSize = 0;
        ByteArrayOutputStream infoStream = new ByteArrayOutputStream();
        try {
            while ((readSize = input.read(bcache)) > 0) {
                totalSize += readSize;
                if (totalSize > BYTE_MAX) {
                    throw new Exception("数据量太大！");
                }
                infoStream.write(bcache,0,readSize);
            }
        } catch (IOException e1) {
            throw new Exception("输入流读取异常");
        } finally {
            try {
                //输入流关闭
                input.close();
            } catch (IOException e) {
                throw new Exception("输入流关闭异常");
            }
        }

        return infoStream.toByteArray();
    }


    /**
     * vertx 的 socks5 端
     * 远程 DNS 解析！
     */
    private static void testSocks5_(String host, int port, String uri) {

        HttpClientOptions clientOptions = new HttpClientOptions()
                .setProxyOptions(new ProxyOptions().setType(ProxyType.SOCKS5).setHost(localhost).setPort(1080));

        if (port == 443) {
            clientOptions.setSsl(true);
        } else if (port == 80) {
            clientOptions.setSsl(false);
        } else {
            log.error("port error ! " + port);
            return;
        }

        Vertx vertx = Vertx.vertx();
        HttpClient client = vertx.createHttpClient(clientOptions);

        client.getNow(port, host, uri, response -> response.bodyHandler(totalBuffer -> {
            System.out.println("----------------------------------------------------------------\n\n " + totalBuffer);

            // 关闭
            client.close();
            vertx.close();
        }));
    }

}
