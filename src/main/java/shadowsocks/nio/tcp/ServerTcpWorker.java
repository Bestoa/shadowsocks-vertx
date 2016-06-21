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

import shadowsocks.util.LocalConfig;

import shadowsocks.auth.SSAuth;
import shadowsocks.auth.HmacSHA1;
import shadowsocks.auth.AuthException;

public class ServerTcpWorker extends TcpWorker {


    // Store the expect auth result from client
    private byte [] mExpectAuthResult;
    // Auth header for each chunck;
    private ByteBuffer mAuthHeader;
    private int mChunkLeft = 0;

    /*
     *  IV |addr type: 1 byte| addr | port: 2 bytes with big endian|
     *
     *  addr type 0x1: addr = ipv4 | 4 bytes
     *  addr type 0x3: addr = host address byte array | 1 byte(array length) + byte array
     *  addr type 0x4: addr = ipv6 | 19 bytes?
     *
     *  OTA will add 10 bytes HMAC-SHA1 in the end of the head.
     *
     */
    private void parseHeader() throws IOException, CryptoException, AuthException
    {
        SocketChannel local = mSession.get(true);

        ByteBuffer bb = BufferHelper.create(512);

        mStreamUpData.reset();
        // Read IV + address type length.
        int len = mCryptor.getIVLength() + 1;
        BufferHelper.prepare(bb, len);
        local.read(bb);

        byte [] result = mCryptor.decrypt(bb.array(), len);
        int addrtype = (int)(result[0] & 0xff);

        if ((addrtype & Session.OTA_FLAG) == Session.OTA_FLAG) {
            mOneTimeAuth = true;
            addrtype &= 0x0f;
        }
        mStreamUpData.write(result[0]);

        if (!mOneTimeAuth && mConfig.oneTimeAuth) {
            throw new AuthException("OTA is not enabled!");
        }

        //get address
        InetAddress addr;
        if (addrtype == Session.ADDR_TYPE_IPV4) {
            //get IPV4 address
            BufferHelper.prepare(bb, 4);
            local.read(bb);
            result = mCryptor.decrypt(bb.array(), 4);
            addr = InetAddress.getByAddress(result);
            mStreamUpData.write(result, 0, 4);
        }else if (addrtype == Session.ADDR_TYPE_HOST) {
            //get address len
            BufferHelper.prepare(bb, 1);
            local.read(bb);
            result = mCryptor.decrypt(bb.array(), 1);
            len = result[0];
            mStreamUpData.write(result[0]);
            //get address
            BufferHelper.prepare(bb, len);
            local.read(bb);
            result = mCryptor.decrypt(bb.array(), len);
            addr = InetAddress.getByName(new String(result, 0, len));
            mStreamUpData.write(result, 0, len);
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        //get port
        BufferHelper.prepare(bb, 2);
        local.read(bb);
        result = mCryptor.decrypt(bb.array(), 2);
        BufferHelper.prepare(bb, 2);
        bb.put(result[0]);
        bb.put(result[1]);
        mStreamUpData.write(result, 0, 2);
        // if port > 32767 the short will < 0
        int port = (int)(bb.getShort(0)&0xFFFF);

        // Auth head
        if (mOneTimeAuth){
            BufferHelper.prepare(bb, HmacSHA1.AUTH_LEN);
            local.read(bb);
            result = mCryptor.decrypt(bb.array(), HmacSHA1.AUTH_LEN);
            byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(false), mCryptor.getKey());
            byte [] authData = mStreamUpData.toByteArray();
            if (!mAuthor.doAuth(authKey, authData, result)){
                throw new AuthException("Auth head failed");
            }
        }
        mConfig.remoteAddress = new InetSocketAddress(addr, port);
        mConfig.target = addr + ":" + port;
    }

    // For OTA the chunck will be:
    // Data len 2 bytes | HMAC-SHA1 10 bytes | Data
    // Parse the auth head
    private boolean readAuthHead(SocketChannel sc) throws IOException,CryptoException
    {
        int size = 0;

        size = sc.read(mAuthHeader);

        // Actually, we reach the end of stream.
        if (size < 0)
            return true;

        if (mAuthHeader.hasRemaining()) {
            //wait for auth header completion.
            return false;
        }

        // Data len(2) + HMAC-SHA1
        int authHeadLen = HmacSHA1.AUTH_LEN + 2;
        byte [] result = mCryptor.decrypt(mAuthHeader.array(), authHeadLen);
        // Prepare for next chunck
        BufferHelper.prepare(mAuthHeader);

        ByteBuffer bb = BufferHelper.create(2);
        BufferHelper.prepare(bb, 2);
        bb.put(result[0]);
        bb.put(result[1]);
        mChunkLeft = (int)(bb.getShort(0)&0xFFFF);

        // Windows ss may just send a empty package, handle it.
        if (mChunkLeft == 0) {
            mChunkCount++;
        }

        // store the pre-calculated auth result
        System.arraycopy(result, 2, mExpectAuthResult, 0, HmacSHA1.AUTH_LEN);

        mStreamUpData.reset();

        return false;
    }

    @Override
    protected boolean relay(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException
    {
        int size;
        ByteBuffer bb = BufferHelper.create();
        if (mOneTimeAuth && direct == Session.LOCAL2REMOTE)
        {
            if (mChunkLeft == 0)
                return readAuthHead(source);
            else
                BufferHelper.prepare(bb, mChunkLeft);
        }else{
            BufferHelper.prepare(bb);
        }
        size = source.read(bb);
        if (size < 0)
            return true;

        mSession.record(size, direct);

        byte [] result;
        if (direct == Session.LOCAL2REMOTE) {
            result = mCryptor.decrypt(bb.array(), size);
        }else{
            result = mCryptor.encrypt(bb.array(), size);
        }
        if (mOneTimeAuth && direct == Session.LOCAL2REMOTE)
        {
            mStreamUpData.write(result, 0, size);
            mChunkLeft -= size;
            if (mChunkLeft == 0) {
                byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(false), mChunkCount);
                byte [] authData = mStreamUpData.toByteArray();
                if (!mAuthor.doAuth(authKey, authData, mExpectAuthResult)){
                    throw new AuthException("Auth chunk " + mChunkCount + " failed!");
                }
                mChunkCount++;
            }
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
            case AFTER_TCP_RELAY:
            default:
                //dummy for default.
        }
    }
    private void init() throws IOException{

        mExpectAuthResult = new byte[HmacSHA1.AUTH_LEN];
        // 2 bytes for data len: data len + auth result.
        mAuthHeader = BufferHelper.create(HmacSHA1.AUTH_LEN + 2);
    }

    public ServerTcpWorker(SocketChannel sc, LocalConfig lc){
        super(sc, lc);
    }
}
