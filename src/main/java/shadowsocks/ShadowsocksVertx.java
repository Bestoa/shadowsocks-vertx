package shadowsocks;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetServerOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shadowsocks.vertxio.ClientHandler;
import shadowsocks.vertxio.ServerHandler;

public class ShadowsocksVertx {

    public static Logger log = LogManager.getLogger(ShadowsocksVertx.class.getName());

    private Vertx mVertx;
    private boolean mIsServer;
    private NetServer mNetServer;

    private String localhost;

    public ShadowsocksVertx(boolean isServer) {
        mVertx = Vertx.vertx();
        mIsServer = isServer;
        boolean preferIPv4Stack = Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Stack"));
        localhost = preferIPv4Stack ? "0.0.0.0" : "::";
    }

    public void start() {
        int port = mIsServer ? GlobalConfig.get().getPort() : GlobalConfig.get().getLocalPort();
        mNetServer = mVertx.createNetServer(new NetServerOptions().setTcpKeepAlive(true)).connectHandler(sock -> {
            Handler<Buffer> dataHandler = mIsServer ? new ServerHandler(mVertx, sock) : new ClientHandler(mVertx, sock);
            sock.handler(dataHandler);
        }).listen(port, localhost, res -> {
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
