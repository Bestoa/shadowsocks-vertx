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

public class ServerTcpWorker extends TcpWorker {

    // For OTA
    // Store the data to do one time auth
    private ByteArrayOutputStream mAuthData;
    // Store the expect auth result from client
    private byte [] mExpectAuthResult;
    private StateMachine mSM;
    private boolean mOneTimeAuth = false;
    private SSAuth mAuthor;
    private int mChunkCount = 0;

    private class StateMachine{
        public final static int START_STATE = 0;
        public final static int AUTH_HEAD = 0;
        public final static int DATA = 1;
        public final static int END_STATE  = 1;

        // OTA auth head is 12 bytes
        // 2 bytes for data len, 10 bytes for HMAC-SHA1
        public int mLenToRead[] = {12, 0};

        private int mState;

        public int getState(){
            return mState;
        }
        public void nextState(){
            if (++mState > END_STATE){
                mState = START_STATE;
            }
        }
    }

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
    private InetSocketAddress parseHead(SocketChannel local) throws IOException, CryptoException, AuthException
    {
        mAuthData.reset();
        // Read IV + address type length.
        int len = mCryptor.getIVLength() + 1;
        mBufferWrap.prepare(len);
        mBufferWrap.readWithCheck(local, len);

        byte [] result = mCryptor.decrypt(mBuffer.array(), len);
        int addrtype = (int)(result[0] & 0xff);

        if ((addrtype & Session.OTA_FLAG) == Session.OTA_FLAG) {
            mOneTimeAuth = true;
            addrtype &= 0x0f;
        }
        mAuthData.write(result[0]);

        if (!mOneTimeAuth && Config.get().isOTAEnabled()) {
            throw new AuthException("OTA is not enabled!");
        }

        //get address
        InetAddress addr;
        if (addrtype == Session.ADDR_TYPE_IPV4) {
            //get IPV4 address
            mBufferWrap.prepare(4);
            mBufferWrap.readWithCheck(local, 4);
            result = mCryptor.decrypt(mBuffer.array(), 4);
            addr = InetAddress.getByAddress(result);
            mAuthData.write(result, 0, 4);
        }else if (addrtype == Session.ADDR_TYPE_HOST) {
            //get address len
            mBufferWrap.prepare(1);
            mBufferWrap.readWithCheck(local, 1);
            result = mCryptor.decrypt(mBuffer.array(), 1);
            len = result[0];
            mAuthData.write(result[0]);
            //get address
            mBufferWrap.prepare(len);
            mBufferWrap.readWithCheck(local, len);
            result = mCryptor.decrypt(mBuffer.array(), len);
            addr = InetAddress.getByName(new String(result, 0, len));
            mAuthData.write(result, 0, len);
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        //get port
        mBufferWrap.prepare(2);
        mBufferWrap.readWithCheck(local, 2);
        result = mCryptor.decrypt(mBuffer.array(), 2);
        mBufferWrap.prepare(2);
        mBuffer.put(result[0]);
        mBuffer.put(result[1]);
        mAuthData.write(result, 0, 2);
        // if port > 32767 the short will < 0
        int port = (int)(mBuffer.getShort(0)&0xFFFF);

        // Auth head
        if (mOneTimeAuth){
            mBufferWrap.prepare(HmacSHA1.AUTH_LEN);
            mBufferWrap.readWithCheck(local, HmacSHA1.AUTH_LEN);
            result = mCryptor.decrypt(mBuffer.array(), HmacSHA1.AUTH_LEN);
            byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(false), mCryptor.getKey());
            byte [] authData = mAuthData.toByteArray();
            if (!mAuthor.doAuth(authKey, authData, result)){
                throw new AuthException("Auth head failed");
            }
        }
        InetSocketAddress target = new InetSocketAddress(addr, port);
        mSession.set(target, false);
        log.info("Connecting " + target +  " from " + local.socket().getRemoteSocketAddress());
        return target;
    }

    // For OTA the chunck will be:
    // Data len 2 bytes | HMAC-SHA1 10 bytes | Data
    // Parse the auth head
    private boolean readAuthHead(SocketChannel sc) throws IOException,CryptoException
    {
        int size = 0;
        int total_size = 0;
        int authHeadLen = mSM.mLenToRead[StateMachine.AUTH_HEAD];
        mBufferWrap.prepare(authHeadLen);
        //In fact it should be send together, but we'd better to ensure we could read full head.
        while(mBuffer.hasRemaining()){
            size = sc.read(mBuffer);
            if (size < 0)
                break;
            else
                total_size += size;
        }
        if (total_size < authHeadLen){
            // Actually, we reach the end of stream.
            if (total_size == 0)
                return true;
            throw new IOException("Auth head is too short");

        }
        byte [] result = mCryptor.decrypt(mBuffer.array(), authHeadLen);
        mBufferWrap.prepare(2);
        mBuffer.put(result[0]);
        mBuffer.put(result[1]);
        mSM.mLenToRead[StateMachine.DATA] = (int)(mBuffer.getShort(0)&0xFFFF);
        mSM.nextState();

        // store the pre-calculated auth result
        System.arraycopy(result, 2, mExpectAuthResult, 0, HmacSHA1.AUTH_LEN);

        mAuthData.reset();

        return false;
    }

    @Override
    protected boolean send(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException
    {
        int size;
        boolean chunkFinish = false;
        if (mOneTimeAuth && direct == Session.LOCAL2REMOTE)
        {
            switch (mSM.getState()){
                case StateMachine.AUTH_HEAD:
                    return readAuthHead(source);
                case StateMachine.DATA:
                    mBufferWrap.prepare(mSM.mLenToRead[StateMachine.DATA]);
                    break;
            }
        }else{
            mBufferWrap.prepare();
        }
        size = source.read(mBuffer);
        if (size < 0)
            return true;

        mSession.record(size, direct);

        if (mOneTimeAuth && direct == Session.LOCAL2REMOTE)
        {
            mSM.mLenToRead[StateMachine.DATA] -= size;
            if (mSM.mLenToRead[StateMachine.DATA] == 0){
                chunkFinish = true;
                mSM.nextState();
            }
        }
        byte [] result;
        if (direct == Session.LOCAL2REMOTE) {
            result = mCryptor.decrypt(mBuffer.array(), size);
        }else{
            result = mCryptor.encrypt(mBuffer.array(), size);
        }
        if (mOneTimeAuth && direct == Session.LOCAL2REMOTE)
        {
            mAuthData.write(result, 0, size);
            if (chunkFinish) {
                byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(false), mChunkCount);
                byte [] authData = mAuthData.toByteArray();
                if (!mAuthor.doAuth(authKey, authData, mExpectAuthResult)){
                    throw new AuthException("Auth chunk " + mChunkCount + " failed!");
                }
                mChunkCount++;
            }
        }
        ByteBuffer out = ByteBuffer.wrap(result);
        while(out.hasRemaining())
            target.write(out);
        return false;
    }
    @Override
    protected InetSocketAddress getRemoteAddress(SocketChannel local)
        throws IOException, CryptoException, AuthException
    {
        return parseHead(local);
    }
    @Override
    protected void preTcpRelay(SocketChannel local, SocketChannel remote)
        throws IOException, CryptoException, AuthException
    {
        //dummy
    }
    @Override
    protected void postTcpTelay(SocketChannel local, SocketChannel remote)
        throws IOException, CryptoException, AuthException
    {
        //dummy
    }
    @Override
    protected void localInit() throws Exception{
        // for one time auth
        mSM = new StateMachine();
        mAuthor = new HmacSHA1();
        mAuthData = new ByteArrayOutputStream();
        mExpectAuthResult = new byte[HmacSHA1.AUTH_LEN];
    }

    public ServerTcpWorker(SocketChannel s){
        super(s);
    }
}
