/*
 *   Copyright 2016 Author:NU11 bestoapache@gmail.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
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
import java.util.Iterator;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;

import shadowsocks.util.Config;

import shadowsocks.auth.SSAuth;
import shadowsocks.auth.HmacSHA1;
import shadowsocks.auth.AuthException;

public class SSTcpRelayLocalUnit implements Runnable {

    public static Logger log = LogManager.getLogger(SSTcpRelayLocalUnit.class.getName());

    private SocketChannel mClient;

    InetSocketAddress mTargetAddress;

    public SSCrypto mCryptor;

    // Temp buffer for send data, also for OTA
    private ByteArrayOutputStream mSendData;

    private SSBufferWrap mBufferWrap;
    private ByteBuffer mBuffer;

    private boolean mOneTimeAuth = false;

    private SSAuth mAuthor;

    private int mChunkCount = 0;
    /*
     *  Reply 05 00
     *        05 00 00 01 + ip 0.0.0.0 + port 2222 (fake)
     *  Send to remote
     *  addr type: 1 byte| addr | port: 2 bytes with big endian
     *
     *  addr type 0x1: addr = ipv4 | 4 bytes
     *  addr type 0x3: addr = host address byte array | 1 byte(array length) + byte array
     *  addr type 0x4: addr = ipv6 | 19 bytes?
     *
     *  OTA will add 10 bytes HMAC-SHA1 in the end of the head.
     *
     */
    private void parseHead(SocketChannel local, SocketChannel remote) throws IOException, CryptoException, AuthException
    {
        //skip method list (max 1+1+255)
        mBufferWrap.prepare(257);
        mBufferWrap.readWithCheck(local, 3);

        //reply 0x05(Socks version) 0x00 (no password)
        mBufferWrap.prepare(2);
        mBuffer.put((byte)0x05);
        mBuffer.put((byte)0x00);
        mBuffer.flip();
        while(mBuffer.hasRemaining()){
            local.write(mBuffer);
        }

        // 4 bytes: VER MODE RSV ADDRTYPE
        mBufferWrap.prepare(4);
        mBufferWrap.readWithCheck(local, 4);

        byte [] data = mBuffer.array();
        //check mode
        // 1 connect
        // 2 bind
        // 3 udp associate
        // just support mode 1 now
        if (data[1] != 1) {
            throw new IOException("Mode = " + data[1] + ", should be 1");
        }

        mSendData.reset();
        int addrtype = (int)(data[3] & 0xff);
        //add OTA flag
        if (mOneTimeAuth) {
            data[3] |= SSTcpConstant.OTA_FLAG;
        }
        mSendData.write(data[3]);

        //get addr
        InetAddress addr;
        if (addrtype == SSTcpConstant.ADDR_TYPE_IPV4) {
            //get IPV4 address
            mBufferWrap.prepare(4);
            mBufferWrap.readWithCheck(local, 4);
            data = mBuffer.array();
            byte [] ipv4 = new byte[4];
            System.arraycopy(data, 0, ipv4, 0, 4);
            addr = InetAddress.getByAddress(ipv4);
            mSendData.write(data, 0, 4);
        }else if (addrtype == SSTcpConstant.ADDR_TYPE_HOST) {
            //get address len
            mBufferWrap.prepare(1);
            mBufferWrap.readWithCheck(local, 1);
            data = mBuffer.array();
            int len = data[0];
            mSendData.write(data[0]);
            //get address
            mBufferWrap.prepare(len);
            mBufferWrap.readWithCheck(local, len);
            data = mBuffer.array();
            addr = InetAddress.getByName(new String(data, 0, len));
            mSendData.write(data, 0, len);
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        //get port
        mBufferWrap.prepare(2);
        mBufferWrap.readWithCheck(local, 2);
        // if port > 32767 the short will < 0
        int port = (int)(mBuffer.getShort(0)&0xFFFF);

        mSendData.write(mBuffer.array(), 0, 2);

        //reply
        mBufferWrap.prepare(10);
        // 05 00 00 01 + 0.0.0.0:8888
        byte [] reply = {0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00};
        mBuffer.put(reply);
        mBuffer.putShort((short)8888);
        mBuffer.flip();
        while(mBuffer.hasRemaining())
            local.write(mBuffer);

        mTargetAddress = new InetSocketAddress(addr, port);
        log.info("Target address is " + mTargetAddress);

        // Create auth head
        if (mOneTimeAuth){
            byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(true), mCryptor.getKey());
            byte [] authData = mSendData.toByteArray();
            byte [] authResult = mAuthor.doAuth(authKey, authData);
            mSendData.write(authResult);
        }

        data = mSendData.toByteArray();
        byte [] result = mCryptor.encrypt(data, data.length);
        ByteBuffer out = ByteBuffer.wrap(result);
        while(out.hasRemaining())
            remote.write(out);
    }

    private boolean send(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException
    {
        int size;
        mBufferWrap.prepare();
        size = source.read(mBuffer);
        if (size < 0)
            return true;

        byte [] result;
        if (direct == SSTcpConstant.LOCAL2REMOTE) {
            mSendData.reset();
            if (mOneTimeAuth) {
                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.putShort((short)size);
                //chunk length 2 bytes
                mSendData.write(bb.array());
                //auth result 10 bytes
                byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(true), mChunkCount);
                byte [] authData = new byte[size];
                System.arraycopy(mBuffer.array(), 0, authData, 0, size);
                byte [] authResult = mAuthor.doAuth(authKey, authData);
                mSendData.write(authResult);
                mChunkCount++;
            }
            mSendData.write(mBuffer.array(), 0, size);
            result = mCryptor.encrypt(mSendData.toByteArray(), mSendData.toByteArray().length);
        }else{
            result = mCryptor.decrypt(mBuffer.array(), size);
        }
        ByteBuffer out = ByteBuffer.wrap(result);
        while(out.hasRemaining())
            target.write(out);
        return false;
    }

    private void doTcpRelay(Selector selector, SocketChannel local, SocketChannel remote)
        throws IOException,InterruptedException,CryptoException,AuthException
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
                        finish = send(local, remote, SSTcpConstant.LOCAL2REMOTE);
                    }else{
                        finish = send(remote, local, SSTcpConstant.REMOTE2LOCAL);
                    }
                }
                it.remove();
            }
            if (finish)
                break;
        }
    }

    private void TcpRelay(SocketChannel local) throws IOException, CryptoException, AuthException
    {
        int CONNECT_TIMEOUT = 3000;

        InetSocketAddress server = new InetSocketAddress(
                InetAddress.getByName(Config.get().getServer()),
                Config.get().getPort());

        try(SocketChannel remote = SocketChannel.open();
                Selector selector = Selector.open();)
        {
            remote.socket().setTcpNoDelay(true);
            //Still use socket with timeout since some time, remote is unreachable, then client closed
            //but this thread is still hold. This will decrease CLOSE_wait state
            remote.socket().connect(server, CONNECT_TIMEOUT);

            parseHead(local, remote);

            doTcpRelay(selector, local, remote);

        }catch(SocketTimeoutException e){
            //ignore
        }catch(InterruptedException e){
            //ignore
        }catch(IOException | CryptoException e){
            log.error("Target address is " + mTargetAddress, e);
        }

    }

    public void run()
    {
        //make sure this channel could be closed
        try(SocketChannel client = mClient){
            mCryptor = CryptoFactory.create(Config.get().getMethod(), Config.get().getPassword());
            mBufferWrap = new SSBufferWrap();
            mBuffer = mBufferWrap.get();
            mSendData = new ByteArrayOutputStream();
            // for one time auth
            mAuthor = new HmacSHA1();
            mOneTimeAuth = Config.get().isOTAEnabled();
            TcpRelay(client);
        }catch(Exception e){
            log.error("Target address is " + mTargetAddress, e);
        }
    }

    public SSTcpRelayLocalUnit (SocketChannel c)
    {
        mClient = c;
    }
}
