package shadowsocks;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.net.StandardSocketOptions;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import shadowsocks.util.Config;
import shadowsocks.nio.tcp.SSNioTcpRelayUnit;
import shadowsocks.crypto.CryptoFactory;

public class SSServer {


    public void start()
    {
        int port = Config.get().getPort();
        Executor service = Executors.newCachedThreadPool();
        try(ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(port));
            server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            System.out.println("starting server at " + server.socket().getLocalSocketAddress());
            while (true) {
                SocketChannel local = server.accept();
                local.setOption(StandardSocketOptions.TCP_NODELAY, true);
                service.execute(new SSNioTcpRelayUnit(local));
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Shadowsocks v0.3");
        Config.getConfigFromArgv(argv);
        //make sure this method could work.
        try{
            CryptoFactory.create(Config.get().getMethod(), Config.get().getPassword());
        }catch(Exception e){
            e.printStackTrace();
            return;
        }
        new SSServer().start();;
    }
}
