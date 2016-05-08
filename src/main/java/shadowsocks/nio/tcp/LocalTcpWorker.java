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
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.SocketTimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;

import shadowsocks.util.Config;

import shadowsocks.auth.SSAuth;
import shadowsocks.auth.HmacSHA1;
import shadowsocks.auth.AuthException;

public class LocalTcpWorker extends TcpWorker {

    // Temp buffer for stream up(local to remote) data
    private ByteArrayOutputStream mStreamUpData;

    //For OTA
    private boolean mOneTimeAuth = false;
    private SSAuth mAuthor;
    private int mChunkCount = 0;
    /*
     *  Receive method list
     *  Reply 05 00
     *  Receive address + port
     *  Reply
     *        05 00 00 01 + ip 0.0.0.0 + port 8888(fake)
     *
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
    private void parseHeader(SocketChannel local, SocketChannel remote) throws IOException, CryptoException, AuthException
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

        mStreamUpData.reset();
        int addrtype = (int)(data[3] & 0xff);
        //add OTA flag
        if (mOneTimeAuth) {
            data[3] |= Session.OTA_FLAG;
        }
        mStreamUpData.write(data[3]);

        //get addr
        StringBuffer addr = new StringBuffer();
        if (addrtype == Session.ADDR_TYPE_IPV4) {
            //get IPV4 address
            mBufferWrap.prepare(4);
            mBufferWrap.readWithCheck(local, 4);
            data = mBuffer.array();
            byte [] ipv4 = new byte[4];
            System.arraycopy(data, 0, ipv4, 0, 4);
            addr.append(InetAddress.getByAddress(ipv4).toString());
            mStreamUpData.write(data, 0, 4);
        }else if (addrtype == Session.ADDR_TYPE_HOST) {
            //get address len
            mBufferWrap.prepare(1);
            mBufferWrap.readWithCheck(local, 1);
            data = mBuffer.array();
            int len = data[0];
            mStreamUpData.write(data[0]);
            //get address
            mBufferWrap.prepare(len);
            mBufferWrap.readWithCheck(local, len);
            data = mBuffer.array();
            addr.append(new String(data, 0, len));
            mStreamUpData.write(data, 0, len);
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        addr.append(':');

        //get port
        mBufferWrap.prepare(2);
        mBufferWrap.readWithCheck(local, 2);
        // if port > 32767 the short will < 0
        int port = (int)(mBuffer.getShort(0)&0xFFFF);

        mStreamUpData.write(mBuffer.array(), 0, 2);

        addr.append(port);
        mSession.set(addr.toString(), false);
        log.info("Target address: " + addr);

        //reply
        mBufferWrap.prepare(10);
        // 05 00 00 01 + 0.0.0.0:8888
        byte [] reply = {0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00};
        mBuffer.put(reply);
        mBuffer.putShort((short)8888);
        mBuffer.flip();
        while(mBuffer.hasRemaining())
            local.write(mBuffer);

        // Create auth head
        if (mOneTimeAuth){
            byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(true), mCryptor.getKey());
            byte [] authData = mStreamUpData.toByteArray();
            byte [] authResult = mAuthor.doAuth(authKey, authData);
            mStreamUpData.write(authResult);
        }

        //Send head to remote
        data = mStreamUpData.toByteArray();
        byte [] result = mCryptor.encrypt(data, data.length);
        ByteBuffer out = ByteBuffer.wrap(result);
        while(out.hasRemaining())
            remote.write(out);
    }

    @Override
    protected boolean send(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException
    {
        int size;
        mBufferWrap.prepare();
        try{
            size = source.read(mBuffer);
        }catch(IOException e){
            // Sometime target is unreachable, so server close the socket will cause IOException.
            log.warn(e.getMessage());
            return true;
        }
        if (size < 0)
            return true;

        mSession.record(size, direct);

        byte [] result;
        if (direct == Session.LOCAL2REMOTE) {
            mStreamUpData.reset();
            if (mOneTimeAuth) {
                ByteBuffer bb = ByteBuffer.allocate(2);
                bb.putShort((short)size);
                //chunk length 2 bytes
                mStreamUpData.write(bb.array());
                //auth result 10 bytes
                byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(true), mChunkCount);
                byte [] authData = new byte[size];
                System.arraycopy(mBuffer.array(), 0, authData, 0, size);
                byte [] authResult = mAuthor.doAuth(authKey, authData);
                mStreamUpData.write(authResult);
                mChunkCount++;
            }
            mStreamUpData.write(mBuffer.array(), 0, size);
            byte [] data = mStreamUpData.toByteArray();
            result = mCryptor.encrypt(data, data.length);
        }else{
            result = mCryptor.decrypt(mBuffer.array(), size);
        }
        ByteBuffer out = ByteBuffer.wrap(result);
        while(out.hasRemaining())
            target.write(out);
        return false;
    }

    @Override
    protected InetSocketAddress getRemoteAddress(SocketChannel local/*unused*/)
        throws IOException, CryptoException, AuthException
    {
        return new InetSocketAddress(InetAddress.getByName(Config.get().getServer()), Config.get().getPort());
    }
    @Override
    protected void preTcpRelay(SocketChannel local, SocketChannel remote)
        throws IOException, CryptoException, AuthException
    {
        parseHeader(local, remote);
    }
    @Override
    protected void postTcpTelay(SocketChannel local, SocketChannel remote)
        throws IOException, CryptoException, AuthException
    {
        //dummy
    }

    @Override
    protected void localInit() throws Exception{
        mStreamUpData = new ByteArrayOutputStream();
        // for one time auth
        mAuthor = new HmacSHA1();
        mOneTimeAuth = Config.get().isOTAEnabled();
    }

    public LocalTcpWorker(SocketChannel sc){
        super(sc);
    }
}
