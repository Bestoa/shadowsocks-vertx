import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.io.DataInputStream;  
import java.io.DataOutputStream;  
import java.net.ServerSocket;  
import java.net.Socket; 
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SSserver
{

    public static class handleTCP implements Runnable {
        public Socket src, dst;
        public handleTCP(Socket l, Socket r){
            src = l;
            dst = r;
        }
        public void run()
        {
            byte buf[] = new byte[4096];
            int size;
            try{
                DataInputStream input = new DataInputStream(src.getInputStream());
                DataOutputStream output = new DataOutputStream(dst.getOutputStream());
                while (true) {
                    size = input.read(buf, 0, 4096);
                    if (size <= 0) break;
                    output.write(buf, 0, size);
                }
            }catch(SocketTimeoutException e){
                // ignore
            }catch(Exception e){
                e.printStackTrace();
                System.out.println(src.toString());
                System.out.println(dst.toString());
            }
        }

    }

    public static class Server implements Runnable {

        private Socket local;
        private Socket remote;
        private String addr;
        private int port;

        public Server(Socket l)
        {
            local = l;
        }
        public void run() {
            int addrtype;
            byte buf[] = new byte[4096];
            int size;

            try {
                DataInputStream input = new DataInputStream(local.getInputStream());
                addrtype = input.read();
                size = 0;
                if (addrtype == 3) {
                    size = input.read();
                    size = input.read(buf, 0, size);
                } else {
                    //do not support other addrtype now.
                    return;
                }
                addr = new String(buf, 0, size);

                input.read(buf, 0, 2);
                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.order(ByteOrder.BIG_ENDIAN);
                bb.put(buf[0]);
                bb.put(buf[1]);

                port = bb.getShort(0);

                System.out.println("Addr type: " + addrtype + " addr: " + addr + ":" + port);


                remote = new Socket();
                remote.connect(new InetSocketAddress(addr, port), 3000);

                Thread t1 = new Thread(new handleTCP(local, remote));
                Thread t2 = new Thread(new handleTCP(remote, local));
                t1.start();
                t2.start();
                t1.join();
                t2.join();

                remote.close();
                local.close();
            }catch (Exception e) {
                System.out.println("Exception:" + e.getMessage() + "! Target address: " + addr);
            }
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Shadowsocks-Java v0.02");
        int port = 2048;
        int timeout = 30000;
        Executor service = Executors.newCachedThreadPool();
        ServerSocket server = null;

        try {
            server = new ServerSocket(port);
            server.setReuseAddress(true);
        }catch(Exception e){
            e.printStackTrace();
            return;
        }
        while (true) {
            try {
                Socket local = server.accept();
                local.setSoTimeout(timeout);
                service.execute(new Server(local));
            }catch(Exception e){
                e.printStackTrace();
            }
        }
    }
}
