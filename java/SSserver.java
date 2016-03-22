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
import java.net.StandardSocketOptions;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SSserver {

    private class SServerInstance implements Runnable {

        final private int HEAD_BUFF_LEN = 255;

        final private int ADDR_TYPE_HOST = 0x03;

        private SocketChannel mClientChannel;
        private String mRemoteAddr;
        private int mRemotePort;

        public SServerInstance(SocketChannel c)
        {
            mClientChannel = c;
        }

        /*
         *  |addr type: 1 byte| addr | port: 2 bytes with big endian|
         *
         *  addr type 0x1: addr = ipv4 | 4 bytes
         *  addr type 0x3: addr = host address string | length 1 byte + string
         *  addr type 0x4: addr = ipv6 | 19 bytes?
         *
         */
        private void parseHead() throws Exception
        {
            //still use socket I/O here.
            DataInputStream in = new DataInputStream(mClientChannel.socket().getInputStream());

            int len = 0;
            int addrtype = in.read();
            byte buf[] = new byte[HEAD_BUFF_LEN];

            //get addr
            if (addrtype == ADDR_TYPE_HOST) {
                len = in.read(buf, 0, in.read());
            } else {
                //do not support other addrtype now.
                throw new Exception("Unsupport addr type!");
            }
            mRemoteAddr = new String(buf, 0, len);

            //get port
            ByteBuffer port = ByteBuffer.allocate(2);
            port.order(ByteOrder.BIG_ENDIAN);
            in.read(buf, 0, 2);
            port.put(buf[0]);
            port.put(buf[1]);
            mRemotePort = port.getShort(0);

            System.out.println("Addr type: " + addrtype + " addr: " + mRemoteAddr + ":" + mRemotePort);
            //don't close this input stream
        }

        private void close()
        {
            try{
                mClientChannel.close();
            }catch(Exception e){
                e.printStackTrace();
            }
        }

        private void connect()
        {
            SocketChannel local = mClientChannel;
            try(SocketChannel remote = SocketChannel.open(new InetSocketAddress(mRemoteAddr, mRemotePort)))
            {
                remote.setOption(StandardSocketOptions.TCP_NODELAY, true);

                Thread t1 = new Thread(new handleStream(local, remote));
                Thread t2 = new Thread(new handleStream(remote, local));
                t1.start();
                t2.start();
                t1.join();
                t2.join();
            }catch(Exception e){
                System.err.println("Exception: " + e.getMessage() + "! Target address: " + mRemoteAddr);
            }
        }

        public void run()
        {
            try {
                parseHead();
                connect();
            }catch(Exception e){
                e.printStackTrace();
            }finally{
                close();
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

        private void send(ByteBuffer bbuf)
        {
            int size = 0;
            try{
                bbuf.clear();
                size = in.read(bbuf);
                if (size <= 0)
                    return;
                bbuf.flip();
                while(bbuf.hasRemaining()) {
                    out.write(bbuf);
                }
            }catch(SocketTimeoutException e){
                // ignore
                return;
            }catch(Exception e){
                e.printStackTrace();
                return;
            }
        }

        public void run()
        {
            ByteBuffer bbuf = ByteBuffer.allocate(BUFF_LEN);
            while (true) {
                send(bbuf);
            }
        }
    }

    public SSserver()
    {
    }

    public void start()
    {
        int port = 2048;
        Executor service = Executors.newCachedThreadPool();
        try(ServerSocketChannel server = ServerSocketChannel.open()) {
            server.bind(new InetSocketAddress(port));
            server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            while (true) {
                SocketChannel local = server.accept();
                local.setOption(StandardSocketOptions.TCP_NODELAY, true);
                service.execute(new SServerInstance(local));
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Shadowsocks-Java v0.02");
        new SSserver().start();;
    }
}
