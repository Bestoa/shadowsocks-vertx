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
package shadowsocks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import shadowsocks.util.GlobalConfig;

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.util.Arrays;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SystemTest{

    public static Logger log = LogManager.getLogger(SystemTest.class.getName());

    @Before
    public void setUp(){
        log.info("Set up");
        GlobalConfig.get().setPassowrd("");
        GlobalConfig.get().setMethod("me");
        GlobalConfig.get().setServer("127.0.0.1");
        GlobalConfig.get().setPort(1024);
        GlobalConfig.get().setLocalPort(2048);
        GlobalConfig.get().setOTAEnabled(false);
        GlobalConfig.get().setTimeout(100);
    }
    @After
    public void tearDown(){
        log.info("Tear down");
    }

    private void testSimpleHttp(boolean ota) {

        GlobalConfig.get().setOTAEnabled(ota);

        ShadowsocksVertx server = new ShadowsocksVertx(true);
        ShadowsocksVertx client = new ShadowsocksVertx(false);

        server.start();
        client.start();

        //Wait 1s for server/client start.
        try{
            Thread.sleep(1000);
        }catch(Exception e){
            //ignore
        }

        Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress("127.0.0.1", 2048));
        HttpURLConnection proxyConnection = null;// 代理连接
        HttpURLConnection directConnection = null;// 直接连接
        try{
            URL url = new URL("https://example.com");

            proxyConnection = (HttpURLConnection)url.openConnection(proxy);
            proxyConnection.setRequestMethod("GET");

            directConnection = (HttpURLConnection) url.openConnection();
            directConnection.setRequestMethod("GET");

            DataInputStream directData = new DataInputStream(directConnection.getInputStream());
            byte [] expect = new byte[1000];
            directData.read(expect);

            DataInputStream proxyData = new DataInputStream(proxyConnection.getInputStream());
            byte [] result = new byte[1000];
            proxyData.read(result);

            System.out.println(new String(expect,"UTF-8"));
            System.out.println(new String(result,"UTF-8"));

            assertTrue(Arrays.equals(result, expect));
        }catch(IOException e){
            log.error("Failed with exception.", e);
            fail();
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
    @Test
    public void testHttp() {
        String [] methodList = {
            "aes-128-cfb",
            "aes-128-ofb",
            "aes-192-cfb",
            "aes-192-ofb",
            "aes-256-cfb",
            "aes-256-ofb",
            "chacha20",
            "chacha20-ietf",
        };
        for (String method: methodList) {
            GlobalConfig.get().setMethod(method);
            log.debug("Test method: " + method);
            testSimpleHttp(true);
        }
    }

    @Test
    public void testHttpWithoutOTA() {
        testSimpleHttp(false);
    }
}
