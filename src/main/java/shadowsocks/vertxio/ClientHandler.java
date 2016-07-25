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

package shadowsocks.vertxio;

import java.net.InetSocketAddress;
import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.NetServer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;

import shadowsocks.util.LocalConfig;
import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;
import shadowsocks.auth.SSAuth;
import shadowsocks.auth.HmacSHA1;
import shadowsocks.auth.AuthException;

public class ClientHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ClientHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;

    private final static int OTA_FLAG = 0x10;

    private Vertx mVertx;
    private NetSocket mLocalSocket;
    private NetSocket mRemoteSocket;
    private LocalConfig mConfig;
    private int mCurrentStage;
    private Buffer mBufferQueue;
    private int mChunkCount;
    private SSCrypto mCrypto;
    private SSAuth mAuthor;

    private class Stage {
        final public static int HELLO = 0;
        final public static int HEADER = 1;
        final public static int ADDRESS = 2;
        final public static int DATA = 3;
        final public static int DESTORY = 100;
    }

    private void nextStage() {
        if (mCurrentStage != Stage.DATA){
            mCurrentStage++;
        }
    }

    //When any sockets meet close/end/exception, destory the others.
    private void setFinishHandler(NetSocket socket) {
        socket.closeHandler(v -> {
            destory();
        });
        socket.endHandler(v -> {
            destory();
        });
        socket.exceptionHandler(e -> {
            log.error("Catch Exception.", e);
            destory();
        });
    }

    public ClientHandler(Vertx vertx, NetSocket socket, LocalConfig config) {
        mVertx = vertx;
        mLocalSocket = socket;
        mConfig = config;
        mCurrentStage = Stage.HELLO;
        mBufferQueue = Buffer.buffer();
        mChunkCount = 0;
        setFinishHandler(mLocalSocket);
        try{
            mCrypto = CryptoFactory.create(mConfig.method, mConfig.password);
        }catch(Exception e){
            //Will never happen, we check this before.
        }
        mAuthor = new HmacSHA1();
    }

    private Buffer compactBuffer(int start) {
        mBufferQueue = Buffer.buffer().appendBuffer(mBufferQueue.slice(start, mBufferQueue.length()));
        return mBufferQueue;
    }

    private Buffer cleanBuffer() {
        mBufferQueue = Buffer.buffer();
        return mBufferQueue;
    }

    /*
     *  Sock5 client side work flow.
     *
     *  Receive method list
     *  Reply 05 00
     *  Receive address + port
     *  Reply
     *        05 00 00 01 + ip 0.0.0.0 + port 0x01 (fake)
     *
     *  Send to remote
     *  addr type: 1 byte| addr | port: 2 bytes with big endian
     *
     *  addr type 0x1: addr = ipv4 | 4 bytes
     *  addr type 0x3: addr = host address byte array | 1 byte(array length) + byte array
     *  addr type 0x4: addr = ipv6 | 19 bytes
     *
     *  OTA will add 10 bytes HMAC-SHA1 in the end of the head.
     *
     */

    private boolean handleStageHello() {
        int bufferLength = mBufferQueue.length();
        // VERSION + METHOD LEN + METHOD
        if (bufferLength < 3)
            return false;
        //SOCK5
        if (mBufferQueue.getByte(0) != 5) {
            log.warn("Protocol error.");
            return true;
        }
        int methodLen = mBufferQueue.getByte(1);
        if (bufferLength < methodLen + 2)
            return false;
        byte [] msg = {0x05, 0x00};
        mLocalSocket.write(Buffer.buffer(msg));
        //Discard the method list
        cleanBuffer();
        nextStage();
        return false;
    }

    private boolean handleStageHeader() {
        int bufferLength = mBufferQueue.length();
        // VERSION + MODE + RSV + ADDR TYPE
        if (bufferLength < 4)
            return false;
        // 1 connect
        // 2 bind
        // 3 udp associate
        // just support mode 1 now
        if (mBufferQueue.getByte(1) != 1) {
            log.warn("Mode != 1");
            return true;
        }
        nextStage();
        //keep the addr type
        compactBuffer(3);
        if (mBufferQueue.length() > 0) {
            return handleStageAddress();
        }
        return false;
    }

    private boolean handleStageAddress() {
        int bufferLength = mBufferQueue.length();
        String addr = null;
        // Construct the remote header.
        Buffer remoteHeader = Buffer.buffer();
        int addrType = mBufferQueue.getByte(0);
        if (mConfig.oneTimeAuth) {
            remoteHeader.appendByte((byte)(addrType | OTA_FLAG));
        }else{
            remoteHeader.appendByte((byte)(addrType));
        }

        if (addrType == ADDR_TYPE_IPV4) {
            // addr type (1) + ipv4(4) + port(2)
            if (bufferLength < 7)
                return false;
            try{
                addr = InetAddress.getByAddress(mBufferQueue.getBytes(1, 5)).toString();
            }catch(UnknownHostException e){
                log.error("UnknownHostException.", e);
                return true;
            }
            remoteHeader.appendBytes(mBufferQueue.getBytes(1,5));
            compactBuffer(5);
        }else if (addrType == ADDR_TYPE_HOST) {
            short hostLength = mBufferQueue.getUnsignedByte(1);
            // addr type(1) + len(1) + host + port(2)
            if (bufferLength < hostLength + 4)
                return false;
            addr = mBufferQueue.getString(2, hostLength + 2);
            remoteHeader.appendByte((byte)hostLength).appendString(addr);
            compactBuffer(hostLength + 2);
        }else {
            log.warn("Unsupport addr type " + addrType);
            return true;
        }
        int port = mBufferQueue.getUnsignedShort(0);
        remoteHeader.appendShort((short)port);
        compactBuffer(2);
        log.info("Connecting to " + addr + ":" + port);
        connectToRemote(mConfig.server, mConfig.serverPort, remoteHeader);
        nextStage();
        return false;
    }

    private void connectToRemote(String addr, int port, Buffer remoteHeader) {
        // 5s timeout.
        NetClientOptions options = new NetClientOptions().setConnectTimeout(5000);
        NetClient client = mVertx.createNetClient(options);
        client.connect(port, addr, res -> {  // connect handler
            if (!res.succeeded()) {
                log.error("Failed to connect " + addr + ":" + port + ". Caused by " + res.cause().getMessage());
                destory();
                return;
            }
            mRemoteSocket = res.result();
            setFinishHandler(mRemoteSocket);
            mRemoteSocket.handler(buffer -> { // remote socket data handler
                try {
                    byte [] data = buffer.getBytes();
                    byte [] decryptData = mCrypto.decrypt(data, data.length);
                    if (mLocalSocket.writeQueueFull()) {
                        log.warn("-->local write queue full");
                    }
                    mLocalSocket.write(Buffer.buffer(decryptData));
                }catch(CryptoException e){
                    log.error("Catch exception", e);
                    destory();
                }
            });
            // reply to program.
            byte [] msg = {0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
            mLocalSocket.write(Buffer.buffer(msg));
            // send remote header.
            try{
                if (mConfig.oneTimeAuth) {
                    byte [] authKey = SSAuth.prepareKey(mCrypto.getIV(true), mCrypto.getKey());
                    byte [] authData = remoteHeader.getBytes();
                    byte [] authResult = mAuthor.doAuth(authKey, authData);
                    remoteHeader.appendBytes(authResult);
                }
                byte [] header = remoteHeader.getBytes();
                byte [] encryptHeader = mCrypto.encrypt(header, header.length);
                mRemoteSocket.write(Buffer.buffer(encryptHeader));
            }catch(CryptoException | AuthException e){
                log.error("Catch exception", e);
                destory();
            }
        });
    }

    private void sendToRemote(Buffer buffer) {

        Buffer chunkBuffer = Buffer.buffer();
        try{
            if (mConfig.oneTimeAuth) {
                //chunk length 2 bytes
                chunkBuffer.appendShort((short)buffer.length());
                //auth result 10 bytes
                byte [] authKey = SSAuth.prepareKey(mCrypto.getIV(true), mChunkCount++);
                byte [] authData = buffer.getBytes();
                byte [] authResult = mAuthor.doAuth(authKey, authData);
                chunkBuffer.appendBytes(authResult);
            }
            chunkBuffer.appendBuffer(buffer);
            byte [] data = chunkBuffer.getBytes();
            byte [] encryptData = mCrypto.encrypt(data, data.length);
            if (mRemoteSocket.writeQueueFull()) {
                log.warn("-->remote write queue full");
            }
            mRemoteSocket.write(Buffer.buffer(encryptData));
        }catch(CryptoException | AuthException e){
            log.error("Catch exception", e);
            destory();
        }
    }

    private boolean handleStageData() {

        // lens is 2 bytes short.
        int shortMax = 65535;

        while (mBufferQueue.length() > 0) {
            int bufferLength = mBufferQueue.length();
            int end = bufferLength > shortMax ? shortMax : bufferLength;
            sendToRemote(mBufferQueue.slice(0, end));
            compactBuffer(end);
        }

        return false;
    }

    private synchronized void destory() {
        if (mCurrentStage != Stage.DESTORY) {
            mCurrentStage = Stage.DESTORY;
        }
        if (mLocalSocket != null)
            mLocalSocket.close();
        if (mRemoteSocket != null)
            mRemoteSocket.close();
    }

    @Override
    public void handle(Buffer buffer) {
        boolean finish = false;
        mBufferQueue.appendBuffer(buffer);
        switch (mCurrentStage) {
            case Stage.HELLO:
                finish = handleStageHello();
                break;
            case Stage.HEADER:
                finish = handleStageHeader();
                break;
            case Stage.ADDRESS:
                finish = handleStageAddress();
                break;
            case Stage.DATA:
                finish = handleStageData();
                break;
            default:
        }
        if (finish) {
            destory();
        }
    }
}
