import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.StandardSocketOptions;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SSserver {

    private class SServerInstance implements Runnable {

        final private int HEAD_BUFF_LEN = 255;

        final private int BUFF_LEN = 4096;

        final private int ADDR_TYPE_IPV4 = 0x01;
        final private int ADDR_TYPE_HOST = 0x03;

        private SocketChannel mClientChannel;
        private InetAddress mRemoteAddr;
        private int mRemotePort;

        /*
         *  |addr type: 1 byte| addr | port: 2 bytes with big endian|
         *
         *  addr type 0x1: addr = ipv4 | 4 bytes
         *  addr type 0x3: addr = host address string | 1 byte(string length) + string
         *  addr type 0x4: addr = ipv6 | 19 bytes?
         *
         */
        private void parseHead(SocketChannel client) throws IOException
        {
            Socket local = client.socket();
            //still use socket I/O here.
            DataInputStream in = new DataInputStream(local.getInputStream());

            int len = 0;
            int addrtype = in.read();
            byte buf[] = new byte[HEAD_BUFF_LEN];

            //get addr
            if (addrtype == ADDR_TYPE_IPV4) {
                byte ipv4[] = new byte[4];
                in.read(ipv4, 0, 4);
                mRemoteAddr = InetAddress.getByAddress(ipv4);
            }else if (addrtype == ADDR_TYPE_HOST) {
                len = in.read(buf, 0, in.read());
                mRemoteAddr = InetAddress.getByName(new String(buf, 0, len));
            } else {
                //do not support other addrtype now.
                throw new IOException("Unsupport addr type: " + addrtype + "!");
            }

            //get port
            ByteBuffer port = ByteBuffer.allocate(2);
            port.order(ByteOrder.BIG_ENDIAN);
            in.read(buf, 0, 2);
            port.put(buf[0]);
            port.put(buf[1]);
            mRemotePort = port.getShort(0);

            System.out.println("Addr type: " + addrtype + " addr: " + mRemoteAddr + ":" + mRemotePort
                    + " from " + local.getRemoteSocketAddress());
            //don't close this input stream
        }

        private void connectAndSendData(final SocketChannel local)
        {
            int CONNECT_TIMEOUT = 3000;

            try(final SocketChannel remote = SocketChannel.open())
            {
                remote.setOption(StandardSocketOptions.TCP_NODELAY, true);
                //Still use socket with timeout since some time, remote is unreachable, then client closed
                //but this thread is still hold. This will decrease CLOSE_wait state
                remote.socket().connect(new InetSocketAddress(mRemoteAddr, mRemotePort), CONNECT_TIMEOUT);

                // Full-Duplex need 2 threads.
                // Start local -> remote first.
                Thread t = new Thread(new Runnable() {
                    public void run() {
                        sendData(local, remote);
                    }
                });
                t.start();

                sendData(remote, local);

                t.join();
            }catch(IOException e){
                System.err.println("Target address: " + mRemoteAddr);
                e.printStackTrace();
            }catch(InterruptedException e){
                //ignore
            }

        }

        private void shutDownAll(SocketChannel in, SocketChannel out)
        {
            try{
                in.shutdownInput();
                out.shutdownInput();
                in.shutdownOutput();
                out.shutdownOutput();
            }catch(IOException e){
                e.printStackTrace();
            }
        }

        private void sendData(SocketChannel in, SocketChannel out)
        {
            ByteBuffer bbuf = ByteBuffer.allocate(BUFF_LEN);
            int size, r_size = 0, w_size = 0;
            while (true) {
                bbuf.clear();
                size = 0;
                try{
                    size = in.read(bbuf);
                    if (size <= 0)
                        break;
                    r_size += size;
                    bbuf.flip();
                    while(bbuf.hasRemaining())
                        w_size += out.write(bbuf);
                }catch(IOException e){
                    e.printStackTrace();
                    break;
                }
            }
            // check read write size
            if (r_size != w_size) {
                System.err.println("Read size: " + r_size + " != write size: " + w_size);
            }
            // no mater input/ouput reach eos shutdown all stream
            // otherwise, other IO thread may wait in read when client close the socket.
            // it could avoid CLOST_WAIT issue
            shutDownAll(in, out);
        }

        public void run()
        {
            //make sure this channel could be closed
            try(SocketChannel client = mClientChannel){
                parseHead(client);
                connectAndSendData(client);
            }catch(IOException e){
                e.printStackTrace();
            }
        }

        public SServerInstance(SocketChannel c)
        {
            mClientChannel = c;
        }

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
                local.setOption(StandardSocketOptions.SO_LINGER, 1000);
                service.execute(new SServerInstance(local));
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public static void main(String argv[])
    {
        System.out.println("Shadowsocks-Java v0.03");
        new SSserver().start();;
    }
}
