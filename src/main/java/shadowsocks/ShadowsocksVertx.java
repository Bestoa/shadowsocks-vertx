/*
 *   Copyright 2016 Author:Bestoa bestoapache@gmail.com
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

import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;

import shadowsocks.util.GlobalConfig;
import shadowsocks.util.LocalConfig;

import shadowsocks.vertxio.ClientHandler;
import shadowsocks.vertxio.ServerHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShadowsocksVertx {

    public static Logger log = LogManager.getLogger(ShadowsocksVertx.class.getName());

    private Vertx mVertx;
    private boolean mIsServer;
    private NetServer mNetServer;

    public ShadowsocksVertx(boolean isServer) {
        mVertx = Vertx.vertx();
        mIsServer = isServer;
    }

    public void start() {
        LocalConfig config = GlobalConfig.createLocalConfig();
        int port = mIsServer ? config.serverPort : config.localPort;
        mNetServer = mVertx.createNetServer().connectHandler(sock -> {
            Handler<Buffer> dataHandler = mIsServer ? new ServerHandler(mVertx, sock, config) : new ClientHandler(mVertx, sock, config);
            sock.handler(dataHandler);
        }).listen(port, "0.0.0.0", res -> {
            if (res.succeeded()) {
                log.info("Listening at " + port);
            }else{
                log.error("Start failed! " + res.cause().getMessage());
            }
        });
    }

    public void stop() {
        if (mNetServer != null) {
            mNetServer.close(ar -> {
                if (ar.succeeded()) {
                    log.info("Stoped.");
                }else{
                    log.error("Stop failed.");
                }
            });
            mNetServer = null;
        }
    }
}
