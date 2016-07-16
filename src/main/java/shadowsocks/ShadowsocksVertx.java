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

import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.NetServer;

import shadowsocks.util.GlobalConfig;
import shadowsocks.util.LocalConfig;

import shadowsocks.vertxio.ClientHandler;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShadowsocksVertx {

    public static Logger log = LogManager.getLogger(ShadowsocksVertx.class.getName());

    private Vertx mVertx;
    private boolean mIsServer;

    public ShadowsocksVertx(boolean isServer) {
        mVertx = Vertx.vertx();
        mIsServer = isServer;
    }

    public void start() {
        LocalConfig config = GlobalConfig.createLocalConfig();
        int port = mIsServer ? config.serverPort : config.localPort;
        mVertx.createNetServer().connectHandler(sock -> {
            sock.handler(new ClientHandler(mVertx, sock, config));
        }).listen(port, "0.0.0.0", res->{
            if (res.succeeded()) {
                log.info("Listening at " + port);
            }else{
                log.info("Start failed!");
            }
        });
    }
}
