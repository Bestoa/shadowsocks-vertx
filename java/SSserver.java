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

        private Socket mClient;
        private InetSocketAddress mRemoteAddr;

        /*
         *  |addr type: 1 byte| addr | port: 2 bytes with big endian|
         *
         *  addr type 0x1: addr = ipv4 | 4 bytes
         *  addr type 0x3: addr = host address string | 1 byte(string length) + string
         *  addr type 0x4: addr = ipv6 | 19 bytes?
         *
         */
        private void parseHead(Socket local) throws IOException
        {
            DataInputStream in = new DataInputStream(local.getInputStream());

            int len = 0;
            int addrtype = in.read();
            byte buf[] = new byte[HEAD_BUFF_LEN];

            //get addr
            InetAddress addr;
            if (addrtype == ADDR_TYPE_IPV4) {
                byte ipv4[] = new byte[4];
                in.read(ipv4, 0, 4);
                addr = InetAddress.getByAddress(ipv4);
            }else if (addrtype == ADDR_TYPE_HOST) {
                len = in.read(buf, 0, in.read());
                addr = InetAddress.getByName(new String(buf, 0, len));
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

            mRemoteAddr = new InetSocketAddress(addr, port.getShort(0));
            //don't close this input stream, it will close the socket too.
        }

        private void connectAndSendData(final Socket local)
        {
            int CONNECT_TIMEOUT = 3000;

            try(final Socket remote = new Socket())
            {
                remote.setTcpNoDelay(true);
                //Still use socket with timeout since some time, remote is unreachable, then client closed
                //but this thread is still hold. This will decrease CLOSE_wait state
                System.out.println("Connecting " + mRemoteAddr + " from " + local.getRemoteSocketAddress());
                remote.connect(mRemoteAddr, CONNECT_TIMEOUT);

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

        synchronized private void shutDownAll(Socket s)
        {
            if(s.isInputShutdown()) return;
            try{
                s.shutdownInput();
                s.shutdownOutput();
            }catch(IOException e){
                e.printStackTrace();
            }
        }

        private void sendData(Socket in, Socket out)
        {
            byte buf[] = new byte[BUFF_LEN];
            int size, r_size = 0, w_size = 0;
            try{
                DataInputStream input = new DataInputStream(in.getInputStream());
                DataOutputStream output = new DataOutputStream(out.getOutputStream());
                while (true) {
                    size = 0;
                    size = input.read(buf, 0, BUFF_LEN);
                    if (size <= 0)
                        break;
                    r_size += size;
                    output.write(buf, 0, size);
                }
                w_size = output.size();
            }catch(IOException e){
                e.printStackTrace();
            }
            // check read write size
            if (r_size != w_size) {
                System.err.println("Read size: " + r_size + " != write size: " + w_size);
            }
            // no mater input/ouput reach eos shutdown all stream
            // otherwise, other IO thread may wait in read when client close the socket.
            // it could avoid CLOST_WAIT issue
            shutDownAll(in);
            shutDownAll(out);
        }

        public void run()
        {
            //make sure this channel could be closed
            try(Socket client = mClient){
                parseHead(client);
                connectAndSendData(client);
            }catch(IOException e){
                e.printStackTrace();
            }
        }

        public SServerInstance(Socket c)
        {
            mClient = c;
        }

    }

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
                service.execute(new SServerInstance(local));
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
