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
import java.net.StandardSocketOptions;
import java.util.Iterator;

import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;

import shadowsocks.util.Config;

import shadowsocks.auth.SSAuth;
import shadowsocks.auth.HmacSHA1;
import shadowsocks.auth.AuthException;

public class SSNioTcpRelayUnit implements Runnable {

    final private int BUFF_LEN = 16384; /* 16K */

    final private int ADDR_TYPE_IPV4 = 0x01;
    final private int ADDR_TYPE_HOST = 0x03;

    final private int LOCAL2REMOTE = 1;
    final private int REMOTE2LOCAL = 2;

    final private int OTA_FLAG = 0x10;

    private SocketChannel mClient;

    private InetSocketAddress mRemoteAddress;

    public SSCrypto mCryptor;


    // Read buffer
    private ByteBuffer mBuffer;

    private boolean mOneTimeAuth = false;

    private StateMachine mSM;

    private SSAuth mAuthor;

    // Store the data to do one time auth
    private ByteArrayOutputStream mAuthData;
    // Store the expect auth result from client
    private byte [] mExpectAuthResult;

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

    private void prepareBuffer(){
        mBuffer.clear();
    }
    private void prepareBuffer(int size){
        prepareBuffer();
        // if data len is longer than buffer size, just read buffer size one time.
        if (size < BUFF_LEN)
            mBuffer.limit(size);
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
        prepareBuffer(len);
        local.read(mBuffer);

        byte [] result = mCryptor.decrypt(mBuffer.array(), len);
        int addrtype = (int)(result[0] & 0xff);

        if ((addrtype & OTA_FLAG) == OTA_FLAG) {
            mOneTimeAuth = true;
            addrtype &= 0x0f;
        }
        mAuthData.write(result[0]);

        //get addr
        InetAddress addr;
        if (addrtype == ADDR_TYPE_IPV4) {
            //get IPV4 address
            prepareBuffer(4);
            local.read(mBuffer);
            result = mCryptor.decrypt(mBuffer.array(), 4);
            addr = InetAddress.getByAddress(result);
            mAuthData.write(result, 0, 4);
        }else if (addrtype == ADDR_TYPE_HOST) {
            //get address len
            prepareBuffer(1);
            local.read(mBuffer);
            result = mCryptor.decrypt(mBuffer.array(), 1);
            len = result[0];
            mAuthData.write(result[0]);
            //get address
            prepareBuffer(len);
            local.read(mBuffer);
            result = mCryptor.decrypt(mBuffer.array(), len);
            addr = InetAddress.getByName(new String(result, 0, len));
            mAuthData.write(result, 0, len);
        } else {
            //do not support other addrtype now.
            throw new IOException("Unsupport addr type: " + addrtype + "!");
        }

        //get port
        prepareBuffer(2);
        local.read(mBuffer);
        result = mCryptor.decrypt(mBuffer.array(), 2);
        prepareBuffer(2);
        mBuffer.put(result[0]);
        mBuffer.put(result[1]);
        mAuthData.write(result, 0, 2);

        // if port > 32767 the short will < 0
        int port = (int)(mBuffer.getShort(0)&0xFFFF);
        // Auth head
        if (mOneTimeAuth){
            prepareBuffer(HmacSHA1.AUTH_LEN);
            local.read(mBuffer);
            //Even we don't need this sha1, we need decrypt it
            //otherwise, the follow-up data can't be decrypted
            result = mCryptor.decrypt(mBuffer.array(), HmacSHA1.AUTH_LEN);
            byte [] authKey = SSAuth.prepareKey(mCryptor.getIV(false), mCryptor.getKey());
            byte [] authData = mAuthData.toByteArray();
            if (!mAuthor.doAuth(authKey, authData, result)){
                throw new AuthException("Auth head failed");
            }
        }
        return new InetSocketAddress(addr, port);
    }

    // For OTA the chunck will be:
    // Data len 2 bytes | HMAC-SHA1 10 bytes | Data
    // Parse the auth head
    private boolean readAuthHead(SocketChannel sc) throws IOException,CryptoException
    {
        int size = 0;
        int total_size = 0;
        int authHeadLen = mSM.mLenToRead[StateMachine.AUTH_HEAD];
        prepareBuffer(authHeadLen);
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
        prepareBuffer(2);
        mBuffer.put(result[0]);
        mBuffer.put(result[1]);
        mSM.mLenToRead[StateMachine.DATA] = (int)(mBuffer.getShort(0)&0xFFFF);
        mSM.nextState();

        // store the pre-calculated auth result
        System.arraycopy(result, 2, mExpectAuthResult, 0, HmacSHA1.AUTH_LEN);

        mAuthData.reset();

        return false;
    }

    private boolean send(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException
    {
        int size;
        boolean chunkFinish = false;
        if (mOneTimeAuth && direct == LOCAL2REMOTE)
        {
            switch (mSM.getState()){
                case StateMachine.AUTH_HEAD:
                    return readAuthHead(source);
                case StateMachine.DATA:
                    prepareBuffer(mSM.mLenToRead[StateMachine.DATA]);
                    break;
            }
        }else{
            prepareBuffer();
        }
        size = source.read(mBuffer);
        if (size < 0)
            return true;
        if (mOneTimeAuth && direct == LOCAL2REMOTE)
        {
            mSM.mLenToRead[StateMachine.DATA] -= size;
            if (mSM.mLenToRead[StateMachine.DATA] == 0){
                chunkFinish = true;
                mSM.nextState();
            }
        }
        byte [] result;
        if (direct == LOCAL2REMOTE) {
            result = mCryptor.decrypt(mBuffer.array(), size);
        }else{
            result = mCryptor.encrypt(mBuffer.array(), size);
        }
        if (mOneTimeAuth && direct == LOCAL2REMOTE)
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

    private void TcpRelay(SocketChannel local) throws IOException, CryptoException, AuthException
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
            mBuffer = ByteBuffer.allocate(BUFF_LEN);
            // for one time auth
            mSM = new StateMachine();
            mAuthor = new HmacSHA1();
            mAuthData = new ByteArrayOutputStream();
            mExpectAuthResult = new byte[HmacSHA1.AUTH_LEN];
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
