package shadowsocks;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import shadowsocks.SSserverUnit;
import shadowsocks.util.Config;

public class SSserver {


    public void start()
    {
        int port = Config.get().getPort();
        Executor service = Executors.newCachedThreadPool();
        try(ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(port));
            server.setReuseAddress(true);
            System.out.println("starting server at " + server.getLocalSocketAddress());
            while (true) {
                Socket local = server.accept();
                local.setTcpNoDelay(true);
                local.setSoLinger(true, 1);
                service.execute(new SSserverUnit(local));
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Shadowsocks-Java v0.2");
        Config.getConfigFromArgv(argv);
        new SSserver().start();;
    }
}
