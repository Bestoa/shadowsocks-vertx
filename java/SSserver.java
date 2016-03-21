import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.io.DataInputStream;  
import java.io.DataOutputStream;  
import java.net.ServerSocket;  
import java.net.Socket; 

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
                    if (size <= 0) return;
                    output.write(buf, 0, size);
                }
            }catch(Exception e){
                e.printStackTrace();
            }
        }

    }

    public static class Server implements Runnable {

        private ServerSocket server;
        public Server(ServerSocket s)
        {
            server = s;
        }
        public void run() {
            int addrtype;
            byte buf[] = new byte[4096];
            int size;
            int target_port;
            String addr;

            try {
                while (true) 
                {
                    Socket local = server.accept();
                    DataInputStream input = new DataInputStream(local.getInputStream());
                    addrtype = input.read();
                    size = 0;
                    if (addrtype == 3) {
                        size = input.read();
                        size = input.read(buf, 0, size);
                    }
                    addr = new String(buf, 0, size);

                    input.read(buf, 0, 2);
                    ByteBuffer bb = ByteBuffer.allocate(2);
                    bb.order(ByteOrder.BIG_ENDIAN);
                    bb.put(buf[0]);
                    bb.put(buf[1]);

                    target_port = bb.getShort(0);

                    System.out.println("from client. addr type: " + addrtype + " addr: " + addr 
                            + ":" + target_port); 


                    Socket remote = new Socket(addr, target_port);
                    System.out.println("Create socket: " + remote);
                    Thread t1 = new Thread(new handleTCP(local, remote));
                    Thread t2 = new Thread(new handleTCP(remote, local));
                    t1.start();
                    t2.start();
                    t1.join();
                    t2.join();

                    input.close();
                    remote.close();
                    local.close();
                }
            }catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Test v0.2");
        int port = 2048;
        try {
            ServerSocket server = new ServerSocket(port);
            for (int i = 0; i < 32; i++)
            {
                Thread t = new Thread(new Server(server));
                t.start();
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}
