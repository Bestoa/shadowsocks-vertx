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
import shadowsocks.nio.tcp.TcpWorker;
import shadowsocks.nio.tcp.LocalTcpWorker;
import shadowsocks.nio.tcp.ServerTcpWorker;
import shadowsocks.crypto.CryptoFactory;

public class Shadowsocks{

    public static Logger log = LogManager.getLogger(Shadowsocks.class.getName());

    private String mName;

    private int mPort;

    private boolean mExit;

    private boolean mIsServer;

    public Shadowsocks(boolean server){
        mExit  = false;
        mIsServer = server;
        mName = server?"Server":"local";
        mPort = server?Config.get().getPort():Config.get().getLocalPort();
    }

    private TcpWorker createWorker(SocketChannel sc, boolean server){
        if (server)
            return new ServerTcpWorker(sc);
        else
            return  new LocalTcpWorker(sc);
    }

    public void boot()
    {
        Executor service = Executors.newCachedThreadPool();

        try(ServerSocketChannel server = ServerSocketChannel.open()) {
            server.socket().bind(new InetSocketAddress(mPort));
            server.socket().setReuseAddress(true);
            log.info("Starting " + mName + " at " + server.socket().getLocalSocketAddress());
            while (true) {
                SocketChannel local = server.accept();

                if (mExit) {
                    log.info("Exit.");
                    return;
                }

                local.socket().setTcpNoDelay(true);
                service.execute(createWorker(local, mIsServer));
            }
        }catch(IOException e){
            log.error("Start failed.", e);
        }
    }

    public void shutdown()
    {
        mExit = true;
        log.info("Prepare to exit.");
        try(SocketChannel sc = SocketChannel.open()){
            sc.connect(new InetSocketAddress("127.0.0.1", mPort));
        }catch(IOException e){
            log.error("Stop failed.", e);
        }
    }
}
