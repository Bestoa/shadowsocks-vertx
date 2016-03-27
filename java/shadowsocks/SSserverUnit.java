package shadowsocks;

import java.nio.ByteOrder;
import java.nio.ByteBuffer;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

public class SSserverUnit implements Runnable {

    final private int HEAD_BUFF_LEN = 255;

    final private int BUFF_LEN = 4096;

    final private int ADDR_TYPE_IPV4 = 0x01;
    final private int ADDR_TYPE_HOST = 0x03;

    private Socket mClient;
    private InetSocketAddress mRemoteAddress;

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

        mRemoteAddress = new InetSocketAddress(addr, port.getShort(0));
    }

    // The other thread may wait in read, interrupt it.
    // it could avoid CLOST_WAIT issue
    private synchronized void shutDownInput(Socket s)
    {
        try{
            s.shutdownInput();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void send(InputStream input, OutputStream output)
    {
        byte buf[] = new byte[BUFF_LEN];
        int size;
        try{
            while (true) {
                size = input.read(buf, 0, BUFF_LEN);
                if (size < 0)
                    break;
                output.write(buf, 0, size);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    private void doHandleTCPData(final Socket local, final Socket remote) throws IOException,InterruptedException
    {
        try(final DataInputStream lin = new DataInputStream(local.getInputStream());
                final DataInputStream rin = new DataInputStream(remote.getInputStream());
                final DataOutputStream lout = new DataOutputStream(local.getOutputStream());
                final DataOutputStream rout = new DataOutputStream(remote.getOutputStream());)
        {
            // Full-Duplex need 2 threads.
            // Start local -> remote first.
            Thread t = new Thread(new Runnable() {
                public void run() {
                    send(lin, rout);
                    shutDownInput(remote);
                }
            });
            t.start();

            send(rin, lout);
            shutDownInput(local);

            t.join();
        }catch(IOException e){
            throw e;
        }
    }

    private void handleTCPData(Socket local)
    {
        int CONNECT_TIMEOUT = 3000;

        try(Socket remote = new Socket();)
        {
            remote.setTcpNoDelay(true);
            //Still use socket with timeout since some time, remote is unreachable, then client closed
            //but this thread is still hold. This will decrease CLOSE_wait state
            System.out.println("Connecting " + mRemoteAddress + " from " + local.getRemoteSocketAddress());
            remote.connect(mRemoteAddress, CONNECT_TIMEOUT);

            doHandleTCPData(local, remote);

        }catch(SocketTimeoutException e){
            //ignore
        }catch(InterruptedException e){
            //ignore
        }catch(IOException e){
            System.err.println("Target address: " + mRemoteAddress);
            e.printStackTrace();
        }

    }


    public void run()
    {
        //make sure this channel could be closed
        try(Socket client = mClient){
            parseHead(client);
            handleTCPData(client);
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public SSserverUnit(Socket c)
    {
        mClient = c;
    }

}
