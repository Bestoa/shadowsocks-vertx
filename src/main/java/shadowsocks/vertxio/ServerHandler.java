/*
 *   Copyright 2016 Author:Bestoa bestoapache@gmail.com
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

public class ServerHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ServerHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;

    private final static int OTA_FLAG = 0x10;

    private Vertx mVertx;
    private NetSocket mClientSocket;
    private NetSocket mTargetSocket;
    private LocalConfig mConfig;
    private int mCurrentStage;
    private Buffer mBufferQueue;
    private int mChunkCount;
    private SSCrypto mCrypto;
    private SSAuth mAuthor;

    private class Stage {
        final public static int ADDRESS = 1;
        final public static int DATA = 2;
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

    public ServerHandler(Vertx vertx, NetSocket socket, LocalConfig config) {
        mVertx = vertx;
        mClientSocket = socket;
        mConfig = config;
        mCurrentStage = Stage.ADDRESS;
        mBufferQueue = Buffer.buffer();
        mChunkCount = 0;
        setFinishHandler(mClientSocket);
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

    private boolean handleStageAddress() {

        int bufferLength = mBufferQueue.length();
        String addr = null;
        int authLen = 0;
        int current = 0;

        int addrType = mBufferQueue.getByte(0);

        if (mConfig.oneTimeAuth) {
            authLen = HmacSHA1.AUTH_LEN;
            if ((addrType & OTA_FLAG) != OTA_FLAG) {
                log.error("OTA is not enabled.");
                return true;
            }
            addrType &= 0x0f;
        }

        if (addrType == ADDR_TYPE_IPV4) {
            // addrType(1) + ipv4(4) + port(2)
            if (bufferLength < 7 + authLen)
                return false;
            try{
                //remote the "/"
                addr = InetAddress.getByAddress(mBufferQueue.getBytes(1, 5)).toString().substring(1);
            }catch(UnknownHostException e){
                log.error("UnknownHostException.", e);
                return true;
            }
            current = 5;
        }else if (addrType == ADDR_TYPE_HOST) {
            short hostLength = mBufferQueue.getUnsignedByte(1);
            // addrType(1) + len(1) + host + port(2)
            if (bufferLength < hostLength + 4 + authLen)
                return false;
            addr = mBufferQueue.getString(2, hostLength + 2);
            current = hostLength + 2;
        }else {
            log.warn("Unsupport addr type " + addrType);
            return true;
        }
        int port = mBufferQueue.getUnsignedShort(current);
        current = current + 2;
        if (mConfig.oneTimeAuth) {
            byte [] expectResult = mBufferQueue.getBytes(current, current + authLen);
            byte [] authKey = SSAuth.prepareKey(mCrypto.getIV(false), mCrypto.getKey());
            byte [] authData = mBufferQueue.getBytes(0, current);
            try{
                if (!mAuthor.doAuth(authKey, authData, expectResult)) {
                    log.error("Auth header failed.");
                    return true;
                }
            }catch(AuthException e) {
                log.error("Auth header exception.", e);
                return true;
            }
            current = current + authLen;
        }
        compactBuffer(current);
        log.info("Connecting to " + addr + ":" + port);
        connectToRemote(addr, port);
        nextStage();
        return false;
    }

    private void connectToRemote(String addr, int port) {
        // 5s timeout.
        NetClientOptions options = new NetClientOptions().setConnectTimeout(5000);
        NetClient client = mVertx.createNetClient(options);
        client.connect(port, addr, res -> {  // connect handler
            if (!res.succeeded()) {
                log.error("Failed to connect " + addr + ":" + port + ". Caused by " + res.cause().getMessage());
                destory();
                return;
            }
            mTargetSocket = res.result();
            setFinishHandler(mTargetSocket);
            mTargetSocket.handler(buffer -> { // remote socket data handler
                try {
                    byte [] data = buffer.getBytes();
                    byte [] encryptData = mCrypto.encrypt(data, data.length);
                    flowControl(mClientSocket, mTargetSocket);
                    mClientSocket.write(Buffer.buffer(encryptData));
                }catch(CryptoException e){
                    log.error("Catch exception", e);
                    destory();
                }
            });
            if (mBufferQueue.length() > 0) {
                handleStageData();
            }
        });
    }

    private void flowControl(NetSocket a, NetSocket b) {
        if (a.writeQueueFull()) {
            b.pause();
            a.drainHandler(done -> {
                b.resume();
            });
        }
    }

    private void sendToRemote(Buffer buffer) {
        flowControl(mTargetSocket, mClientSocket);
        mTargetSocket.write(buffer);
    }

    private boolean handleStageData() {
        if (mTargetSocket == null) {
            //remote is not ready, just hold the buffer.
            return false;
        }
        if (mConfig.oneTimeAuth) {
            // chunk len (2) + auth len
            int chunkHeaderLen = 2 + HmacSHA1.AUTH_LEN;
            int bufferLength = mBufferQueue.length();
            if (bufferLength < chunkHeaderLen){
                return false;
            }
            int chunkLen = mBufferQueue.getUnsignedShort(0);
            if (bufferLength < chunkLen + chunkHeaderLen) {
                return false;
            }
            //handle this case, chunk len = 0
            if (chunkLen > 0) {
                byte [] expectResult = mBufferQueue.getBytes(2, chunkHeaderLen);
                byte [] authKey = SSAuth.prepareKey(mCrypto.getIV(false), mChunkCount);
                byte [] authData = mBufferQueue.getBytes(chunkHeaderLen, chunkHeaderLen + chunkLen);
                try{
                    if (!mAuthor.doAuth(authKey, authData, expectResult)){
                        log.error("Auth chunk " + mChunkCount + " failed.");
                        return true;
                    }
                }catch(AuthException e){
                    log.error("Auth chunk " + mChunkCount + " exception.", e);
                    return true;
                }
                sendToRemote(mBufferQueue.slice(chunkHeaderLen, chunkHeaderLen + chunkLen));
            }
            mChunkCount++;
            compactBuffer(chunkHeaderLen + chunkLen);
            if (mBufferQueue.length() > 0) {
                return handleStageData();
            }
        }else{
            sendToRemote(mBufferQueue);
            cleanBuffer();
        }
        return false;
    }

    private synchronized void destory() {
        if (mCurrentStage != Stage.DESTORY) {
            mCurrentStage = Stage.DESTORY;
        }
        if (mClientSocket != null)
            mClientSocket.close();
        if (mTargetSocket != null)
            mTargetSocket.close();
    }

    @Override
    public void handle(Buffer buffer) {
        boolean finish = false;
        try{
            byte [] data = buffer.getBytes();
            byte [] decryptData = mCrypto.decrypt(data, data.length);
            mBufferQueue.appendBytes(decryptData);
        }catch(CryptoException e){
            log.error("Catch exception", e);
            destory();
            return;
        }
        switch (mCurrentStage) {
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
