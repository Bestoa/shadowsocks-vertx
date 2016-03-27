package shadowsocks;

import java.io.ByteArrayOutputStream;
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

import javax.crypto.Cipher;
import javax.crypto.NullCipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import shadowsocks.crypto.ICrypt;
import shadowsocks.crypto.AesCrypt;

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
    private InetSocketAddress parseHead(Socket local) throws IOException
    {

        DataInputStream in = new DataInputStream(local.getInputStream());
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        int len = 0;
        int addrtype;
        byte buf[] = new byte[HEAD_BUFF_LEN];

        in.read(buf, 0, 17);

        mCrypt.decrypt(buf, 17, baos);
        addrtype = baos.toByteArray()[0];
        //get addr
        InetAddress addr;
        if (addrtype == ADDR_TYPE_IPV4) {
            byte ipv4[] = new byte[4];
            in.read(ipv4, 0, 4);
            mCrypt.decrypt(ipv4, baos);
            addr = InetAddress.getByAddress(baos.toByteArray());
        }else if (addrtype == ADDR_TYPE_HOST) {
            in.read(buf, 0, 1);
            mCrypt.decrypt(buf, 1, baos);
            len = baos.toByteArray()[0];
            in.read(buf, 0, len);
            mCrypt.decrypt(buf, len, baos);
            addr = InetAddress.getByName(new String(baos.toByteArray(), 0, len));
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        //get port
        in.read(buf, 0, 2);
        mCrypt.decrypt(buf, 2, baos);
        ByteBuffer port = ByteBuffer.allocate(2);
        port.order(ByteOrder.BIG_ENDIAN);
        port.put(baos.toByteArray()[0]);
        port.put(baos.toByteArray()[1]);

        return new InetSocketAddress(addr, (int)(port.getShort(0)&0xFFFF));
    }

    // The other thread may wait in read, interrupt it.
    // it could avoid CLOST_WAIT issue
    private synchronized void shutdownInput(Socket s)
    {
        try{
            s.shutdownInput();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    final private int LOCAL2REMOTE = 1;
    final private int REMOTE2LOCAL = 2;

    private void send(Socket source, Socket target, int direct)
    {
        byte rbuf[] = new byte[BUFF_LEN];
        byte wbuf[];
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int size;
        try{
            DataInputStream in = new DataInputStream(source.getInputStream());
            DataOutputStream out = new DataOutputStream(target.getOutputStream());
            while (true) {
                size = in.read(rbuf, 0, BUFF_LEN);
                if (size < 0)
                    break;
                if (direct == LOCAL2REMOTE) {
                    mCrypt.decrypt(rbuf, size, baos);
                }else{
                    mCrypt.encrypt(rbuf, size, baos);
                }
                wbuf = baos.toByteArray();
                out.write(wbuf, 0, wbuf.length);
            }
        }catch(IOException e){
            e.printStackTrace();
        }
        shutdownInput(target);
    }

    private void doHandleTCPData(final Socket local, final Socket remote) throws IOException,InterruptedException
    {
        // Full-Duplex need 2 threads.
        // Start local -> remote first.
        Thread t = new Thread(new Runnable() {
            public void run() {
                send(local, remote, LOCAL2REMOTE);
            }
        });
        t.start();

        send(remote, local, REMOTE2LOCAL);

        t.join();
    }

    private void handleTCPData(Socket local) throws IOException
    {
        int CONNECT_TIMEOUT = 3000;

        mRemoteAddress = parseHead(local);

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

    public ICrypt mCrypt;

    public void run()
    {
        //make sure this channel could be closed
        try(Socket client = mClient){
            mCrypt = new AesCrypt("aes-128-cfb", "881123");
            handleTCPData(client);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public SSserverUnit(Socket c)
    {
        mClient = c;
    }

}
