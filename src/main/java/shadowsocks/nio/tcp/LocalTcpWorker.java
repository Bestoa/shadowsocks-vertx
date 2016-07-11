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
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;

import shadowsocks.util.LocalConfig;

import shadowsocks.auth.SSAuth;
import shadowsocks.auth.HmacSHA1;
import shadowsocks.auth.AuthException;

public class LocalTcpWorker extends TcpWorker {

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
    private void parseHeader() throws IOException
    {
        SocketChannel local = mSession.getSocketChannel(true);

        ByteBuffer bb = BufferHelper.create(512);
        //skip method list (max 1+1+255)
        BufferHelper.prepare(bb, 257);
        int headerSize = local.read(bb);

        //Check socks version.
        //TODO: check method list.
        bb.flip();
        if (headerSize < 3 || bb.get() != 5) {
            throw new IOException("INVALID CONNECTION");
        }

        //reply 0x05(Socks version) 0x00 (no password)
        byte [] msg = {0x05, 0x00};
        replyToProxyProgram(msg);

        BufferHelper.prepare(bb);
        headerSize = local.read(bb);
        bb.flip();

        // 4 bytes: VER MODE RSV ADDRTYPE
        if (headerSize < 4) {
            throw new IOException("Header info is too short.");
        }
        byte [] header = new byte[4];
        bb.get(header);
        headerSize -= 4;
        //check mode
        // 1 connect
        // 2 bind
        // 3 udp associate
        // just support mode 1 now
        if (header[1] != 1) {
            throw new IOException("Mode = " + header[1] + ", should be 1");
        }

        mStreamUpBuffer.reset();
        int addrtype = (int)(header[3] & 0xff);
        //add OTA flag
        if (mOneTimeAuth) {
            header[3] |= Session.OTA_FLAG;
        }
        mStreamUpBuffer.write(header[3]);

        //get addr
        StringBuffer addr = new StringBuffer();
        if (addrtype == Session.ADDR_TYPE_IPV4) {
            //get IPV4 address
            byte [] ipv4 = new byte[4];
            if (headerSize < 4) {
                throw new IOException("IPv4 address is too short.");
            }
            headerSize -= 4;
            bb.get(ipv4);
            addr.append(InetAddress.getByAddress(ipv4).toString());
            mStreamUpBuffer.write(ipv4);
        }else if (addrtype == Session.ADDR_TYPE_HOST) {
            //get address len
            if (headerSize < 2) {
                throw new IOException("Host address is too short.");
            }
            int len = (bb.get() & 0xff);
            mStreamUpBuffer.write(len);
            headerSize -= 1;
            //get address
            if (headerSize < len) {
                throw new IOException("Host address is too short.");
            }
            byte [] host = new byte[len];
            bb.get(host);
            addr.append(new String(host));
            mStreamUpBuffer.write(host);
            headerSize -= len;
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        addr.append(':');

        //get port
        if (headerSize < 2) {
            throw new IOException("Port is too short.");
        }
        // if port > 32767 the short will < 0
        bb.mark();
        int port = (int)(bb.getShort()&0xFFFF);
        headerSize -= 2;
        bb.reset();

        addr.append(port);
        mConfig.target = addr.toString();

        mStreamUpBuffer.write(bb.get());
        mStreamUpBuffer.write(bb.get());

    }

    private void replyToProxyProgram(byte [] msg) throws IOException
    {
        SocketChannel local = mSession.getSocketChannel(true);
        local.write(ByteBuffer.wrap(msg));

    }

    private void sendHeaderToRemote() throws IOException, AuthException, CryptoException
    {
        SocketChannel remote = mSession.getSocketChannel(false);
        // Create auth head
        if (mOneTimeAuth){
            byte [] authKey = SSAuth.prepareKey(mCrypto.getIV(true), mCrypto.getKey());
            byte [] authData = mStreamUpBuffer.toByteArray();
            byte [] authResult = mAuthor.doAuth(authKey, authData);
            mStreamUpBuffer.write(authResult);
        }

        //Send head to remote
        byte [] headerData = mStreamUpBuffer.toByteArray();
        byte [] result = mCrypto.encrypt(headerData, headerData.length);
        BufferHelper.send(remote, result);
    }

    @Override
    protected boolean relay(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException
    {
        int size;
        ByteBuffer bb = BufferHelper.create();
        try{
            size = source.read(bb);
        }catch(IOException e){
            // Sometime target is unreachable, so server close the socket will cause IOException.
            return true;
        }
        if (size < 0)
            return true;

        mSession.record(size, direct);

        byte [] result;
        if (direct == Session.LOCAL2REMOTE) {
            mStreamUpBuffer.reset();
            if (mOneTimeAuth) {
                ByteBuffer len = ByteBuffer.allocate(2);
                len.putShort((short)size);
                //chunk length 2 bytes
                mStreamUpBuffer.write(len.array());
                //auth result 10 bytes
                byte [] authKey = SSAuth.prepareKey(mCrypto.getIV(true), mChunkCount);
                byte [] authData = new byte[size];
                System.arraycopy(bb.array(), 0, authData, 0, size);
                byte [] authResult = mAuthor.doAuth(authKey, authData);
                mStreamUpBuffer.write(authResult);
                mChunkCount++;
            }
            mStreamUpBuffer.write(bb.array(), 0, size);
            byte [] data = mStreamUpBuffer.toByteArray();
            result = mCrypto.encrypt(data, data.length);
        }else{
            result = mCrypto.decrypt(bb.array(), size);
        }
        BufferHelper.send(target, result);
        return false;
    }

    @Override
    protected void handleStage(int stage) throws IOException, CryptoException, AuthException
    {
        switch (stage) {
            case INIT:
                init();
                break;
            case PARSE_HEADER:
                parseHeader();
                break;
            case BEFORE_TCP_RELAY:
                //Reply to program and send header info to remote.
                //reply
                // 05 00 00 01 + 0.0.0.0:4112
                byte [] msg = {0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x10, 0x10};
                replyToProxyProgram(msg);
                sendHeaderToRemote();
                break;
            case AFTER_TCP_RELAY:
            default:
                //dummy for default.
        }
    }

    private void init() throws IOException{

        mOneTimeAuth = mConfig.oneTimeAuth;

        mConfig.remoteAddress = new InetSocketAddress(InetAddress.getByName(mConfig.server), mConfig.serverPort);
    }

    public LocalTcpWorker(SocketChannel sc, LocalConfig lc){
        super(sc, lc);
    }
}
