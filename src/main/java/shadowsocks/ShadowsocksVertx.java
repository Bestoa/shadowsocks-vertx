package shadowsocks;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shadowsocks.util.GlobalConfig;
import shadowsocks.util.LocalConfig;
import shadowsocks.vertxio.ClientHandler;
import shadowsocks.vertxio.ServerHandler;

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
        mNetServer = mVertx.createNetServer(new NetServerOptions().setTcpKeepAlive(true)).connectHandler(sock -> {
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
