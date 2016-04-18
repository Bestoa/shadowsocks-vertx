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

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.util.Config;
import shadowsocks.nio.tcp.SSTcpRelayLocalUnit;
import shadowsocks.crypto.CryptoFactory;

public class SSLocal {

    public static Logger log = LogManager.getLogger(SSLocal.class.getName());

    public void start()
    {
        int lport = Config.get().getLocalPort();
        Executor service = Executors.newCachedThreadPool();
        try(ServerSocketChannel server = ServerSocketChannel.open()) {
            server.socket().bind(new InetSocketAddress(lport));
            server.socket().setReuseAddress(true);
            log.info("Starting local at " + server.socket().getLocalSocketAddress());
            while (true) {
                SocketChannel local = server.accept();
                local.socket().setTcpNoDelay(true);
                service.execute(new SSTcpRelayLocalUnit(local));
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
