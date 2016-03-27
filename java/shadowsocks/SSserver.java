package shadowsocks;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import shadowsocks.SSserverUnit;

public class SSserver {


    public void start()
    {
        int port = 2048;
        Executor service = Executors.newCachedThreadPool();
        try(ServerSocket server = new ServerSocket()) {
            server.bind(new InetSocketAddress(port));
            server.setReuseAddress(true);
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
        System.out.println("Shadowsocks-Java v0.04");
        new SSserver().start();;
    }
}
