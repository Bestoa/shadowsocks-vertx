import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.io.DataInputStream;  
import java.io.DataOutputStream;  
import java.net.ServerSocket;  
import java.net.Socket; 
import java.net.SocketTimeoutException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SSserver {

    private class SServerInstance implements Runnable {

        final private int HEAD_BUFF_LEN = 64;

        final private int ADDR_TYPE_HOST = 0x03;

        final private int TIMEOUT = 3000;

        private SocketChannel mLocalChannel;
        private String mRemoteAddr;
        private int mRemotePort;

        public SServerInstance(SocketChannel l)
        {
            mLocalChannel = l;
        }

        private void parseHead() throws Exception
        {

            Socket local = mLocalChannel.socket();
            DataInputStream in = new DataInputStream(local.getInputStream());

            try{
                int addrtype = in.read();
                int size = 0;
                byte buf[] = new byte[HEAD_BUFF_LEN];

                //get addr
                if (addrtype == ADDR_TYPE_HOST) {
                    size = in.read();
                    size = in.read(buf, 0, size);
                } else {
                    //do not support other addrtype now.
                    throw new Exception("Unsupport addr type!");
                }
                mRemoteAddr = new String(buf, 0, size);

                //get port
                in.read(buf, 0, 2);
                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.order(ByteOrder.BIG_ENDIAN);
                bb.put(buf[0]);
                bb.put(buf[1]);
                mRemotePort = bb.getShort(0);

                System.out.println("Addr type: " + addrtype + " addr: " + mRemoteAddr + ":" + mRemotePort);
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        public void run()
        {
            int addrtype;
            int size;

            try {
                parseHead();
            }catch(Exception e){
                e.printStackTrace();
            }

            try(SocketChannel remote = SocketChannel.open())
            {
                remote.socket().setTcpNoDelay(true);
                remote.socket().connect(new InetSocketAddress(mRemoteAddr, mRemotePort), TIMEOUT);

                Thread t1 = new Thread(new handleStream(mLocalChannel, remote));
                Thread t2 = new Thread(new handleStream(remote, mLocalChannel));
                t1.start();
                t2.start();
                t1.join();
                t2.join();
            }catch(Exception e){
                System.err.println("Exception: " + e.getMessage() + "! Target address: " + mRemoteAddr);
            }

            try {
                mLocalChannel.close();
            }catch(Exception e) {
                System.err.println(e.getMessage());
            }
        }
    }
    // FIXME: this is ugly...
    private class handleStream implements Runnable {

        final private int BUFF_LEN = 4096;

        private SocketChannel in;
        private SocketChannel out;

        public handleStream(SocketChannel i, SocketChannel o){
            in = i;
            out = o;
        }

        public void run()
        {
            ByteBuffer bbuf = ByteBuffer.allocate(BUFF_LEN);
            int size = 0;
            while (true) {
                try{
                    bbuf.clear();
                    size = in.read(bbuf);
                    if (size <= 0) 
                        break;
                    bbuf.flip();
                    while(bbuf.hasRemaining()) {
                        out.write(bbuf);
                    }
                    /*
                }catch(SocketTimeoutException e){
                    // ignore
                    break;
                    */
                }catch(Exception e){
                    e.printStackTrace();
                    break;
                }
            }
        }
    }

    public SSserver()
    {
    }

    public void handle()
    {
        int port = 2048;
        int timeout = 30000;
        Executor service = Executors.newCachedThreadPool();
        try(ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(port));
            server.socket().setReuseAddress(true);
            while (true) {
                SocketChannel local = server.accept();
                local.socket().setTcpNoDelay(true);
                local.socket().setSoTimeout(timeout);
                service.execute(new SServerInstance(local));
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Shadowsocks-Java v0.02");
        new SSserver().handle();;
    }
}
