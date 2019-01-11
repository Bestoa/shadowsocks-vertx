package shadowsocks;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.VertxOptions;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.dns.AddressResolverOptions;
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
        mIsServer = isServer;
        boolean preferIPv4Stack = Boolean.parseBoolean(System.getProperty("java.net.preferIPv4Stack"));
        if (mIsServer) {// server 使用自定义 DNS
            VertxOptions vertxOptions;
            if (preferIPv4Stack) {// ipv4
                vertxOptions = new VertxOptions().setAddressResolverOptions(
                        new AddressResolverOptions().
                                addServer("8.8.8.8").
                                addServer("8.8.4.4"));
            } else {// ipv6
                vertxOptions = new VertxOptions().setAddressResolverOptions(
                        new AddressResolverOptions().
                                addServer("2001:4860:4860::8888").
                                addServer("2001:4860:4860::8844"));
            }

            mVertx = Vertx.vertx(vertxOptions);
        } else {// client 使用默认 DNS
            mVertx = Vertx.vertx();
        }

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
