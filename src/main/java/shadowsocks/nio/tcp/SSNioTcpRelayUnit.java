package shadowsocks.nio.tcp;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.util.Iterator;

import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;

import shadowsocks.util.Config;

public class SSNioTcpRelayUnit implements Runnable {

    final private int BUFF_LEN = 16384; /* 16K */

    final private int ADDR_TYPE_IPV4 = 0x01;
    final private int ADDR_TYPE_HOST = 0x03;

    final private int LOCAL2REMOTE = 1;
    final private int REMOTE2LOCAL = 2;

    private SocketChannel mClient;

    private InetSocketAddress mRemoteAddress;

    public SSCrypto mCryptor;

    // For encrypt/decrypt data
    private ByteArrayOutputStream mData;

    // Read buffer
    private ByteBuffer mBuffer;

    private void prepareBuffer(){
        mBuffer.clear();
    }
    private void prepareBuffer(int size){
        prepareBuffer();
        mBuffer.limit(size);
    }
    /*
     *  IV |addr type: 1 byte| addr | port: 2 bytes with big endian|
     *
     *  addr type 0x1: addr = ipv4 | 4 bytes
     *  addr type 0x3: addr = host address byte array | 1 byte(array length) + byte array
     *  addr type 0x4: addr = ipv6 | 19 bytes?
     *
     */
    private InetSocketAddress parseHead(SocketChannel local) throws IOException, CryptoException
    {
        // Read IV + address type length.
        int len = mCryptor.getIVLength() + 1;
        prepareBuffer(len);
        local.read(mBuffer);

        mCryptor.decrypt(mBuffer.array(), len, mData);
        int addrtype = mData.toByteArray()[0];
        //get addr
        InetAddress addr;
        if (addrtype == ADDR_TYPE_IPV4) {
            prepareBuffer(4);
            local.read(mBuffer);
            mCryptor.decrypt(mBuffer.array(), 4, mData);
            addr = InetAddress.getByAddress(mData.toByteArray());
        }else if (addrtype == ADDR_TYPE_HOST) {
            prepareBuffer(1);
            local.read(mBuffer);
            mCryptor.decrypt(mBuffer.array(), 1, mData);
            len = mData.toByteArray()[0];
            prepareBuffer(len);
            local.read(mBuffer);
            mCryptor.decrypt(mBuffer.array(), len, mData);
            addr = InetAddress.getByName(new String(mData.toByteArray(), 0, len));
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        //get port
        prepareBuffer(2);
        local.read(mBuffer);
        mCryptor.decrypt(mBuffer.array(), 2, mData);
        prepareBuffer(2);
        mBuffer.put(mData.toByteArray()[0]);
        mBuffer.put(mData.toByteArray()[1]);

        // if port > 32767 the short will < 0
        return new InetSocketAddress(addr, (int)(mBuffer.getShort(0)&0xFFFF));
    }

    private boolean send(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException
    {
        int size;
        prepareBuffer();
        size = source.read(mBuffer);
        if (size < 0)
            return true;
        if (direct == LOCAL2REMOTE) {
            mCryptor.decrypt(mBuffer.array(), size, mData);
        }else{
            mCryptor.encrypt(mBuffer.array(), size, mData);
        }
        ByteBuffer out = ByteBuffer.wrap(mData.toByteArray());
        while(out.hasRemaining())
            target.write(out);
        return false;
    }

    private void doTcpRelay(Selector selector, SocketChannel local, SocketChannel remote) throws IOException,InterruptedException,CryptoException
    {
        local.configureBlocking(false);
        remote.configureBlocking(false);

        local.register(selector, SelectionKey.OP_READ);
        remote.register(selector, SelectionKey.OP_READ);

        boolean finish = false;

        while(true){
            int n = selector.select();
            if (n == 0){
                continue;
            }
            Iterator it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = (SelectionKey)it.next();
                if (key.isReadable()) {
                    SocketChannel channel = (SocketChannel)key.channel();
                    if (channel.equals(local)) {
                        finish = send(local, remote, LOCAL2REMOTE);
                    }else{
                        finish = send(remote, local, REMOTE2LOCAL);
                    }
                }
                it.remove();
            }
            if (finish)
                break;
        }
    }

    private void TcpRelay(SocketChannel local) throws IOException, CryptoException
    {
        int CONNECT_TIMEOUT = 3000;

        mRemoteAddress = parseHead(local);

        try(SocketChannel remote = SocketChannel.open();
                Selector selector = Selector.open();)
        {
            remote.setOption(StandardSocketOptions.TCP_NODELAY, true);
            System.out.println("Connecting " + mRemoteAddress + " from " + local.socket().getRemoteSocketAddress());
            //Still use socket with timeout since some time, remote is unreachable, then client closed
            //but this thread is still hold. This will decrease CLOSE_wait state
            remote.socket().connect(mRemoteAddress, CONNECT_TIMEOUT);

            doTcpRelay(selector, local, remote);

        }catch(SocketTimeoutException e){
            //ignore
        }catch(InterruptedException e){
            //ignore
        }catch(IOException | CryptoException e){
            System.err.println("Target address: " + mRemoteAddress);
            e.printStackTrace();
        }

    }

    public void run()
    {
        //make sure this channel could be closed
        try(SocketChannel client = mClient){
            mCryptor = CryptoFactory.create(Config.get().getMethod(), Config.get().getPassword());
            mData = new ByteArrayOutputStream();
            mBuffer = ByteBuffer.allocate(BUFF_LEN);
            TcpRelay(client);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public SSNioTcpRelayUnit (SocketChannel c)
    {
        mClient = c;
    }
}
