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

public class ServerHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ServerHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;
    private final static int ADDR_TYPE_IPV6 = 4; //Not support yet

    private Vertx mVertx;
    private NetSocket mClientSocket;
    private NetSocket mTargetSocket;
    private LocalConfig mConfig;
    private int mCurrentStage;
    private Buffer mBufferQueue;
    private SSCrypto mCrypto;

    private class Stage {
        final public static int HANDSHAKER = 1;
        final public static int STREAMIMG = 2;
        final public static int DESTORY = 100;
    }

    private void nextStage() {
        if (mCurrentStage != Stage.STREAMIMG){
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
        mCurrentStage = Stage.HANDSHAKER;
        mBufferQueue = Buffer.buffer();
        setFinishHandler(mClientSocket);
        try{
            mCrypto = CryptoFactory.create(mConfig.method, mConfig.password);
        }catch(Exception e){
            //Will never happen, we check this before.
        }
    }

    private Buffer compactBuffer(int start) {
        mBufferQueue = Buffer.buffer().appendBuffer(mBufferQueue.slice(start, mBufferQueue.length()));
        return mBufferQueue;
    }

    private Buffer cleanBuffer() {
        mBufferQueue = Buffer.buffer();
        return mBufferQueue;
    }

    private boolean handleStageHandshaker() {

        int bufferLength = mBufferQueue.length();
        String addr = null;
        int current = 0;

        int addrType = mBufferQueue.getByte(0);

        if (addrType == ADDR_TYPE_IPV4) {
            // addrType(1) + ipv4(4) + port(2)
            if (bufferLength < 7)
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
            if (bufferLength < hostLength + 4)
                return false;
            addr = mBufferQueue.getString(2, hostLength + 2);
            current = hostLength + 2;
        }else {
            log.warn("Unsupport addr type " + addrType);
            return true;
        }
        int port = mBufferQueue.getUnsignedShort(current);
        current = current + 2;
        compactBuffer(current);
        log.info("Connecting to " + addr + ":" + port);
        connectToRemote(addr, port);
        nextStage();
        return false;
    }

    private void sendToClient(Buffer buffer) {
        try{
            byte [] data = buffer.getBytes();
            byte [] encryptData = mCrypto.encrypt(data, data.length);
            mClientSocket.write(Buffer.buffer(encryptData));
        }catch(CryptoException e){
            log.error("Catch exception", e);
            destory();
        }
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
                // Chunk max length = 0x3fff.
                int chunkMaxLen = 0x3fff;

                flowControl(mClientSocket, mTargetSocket);

                while (buffer.length() > 0) {
                    int bufferLength = buffer.length();
                    int end = bufferLength > chunkMaxLen ? chunkMaxLen : bufferLength;
                    sendToClient(buffer.slice(0, end));
                    buffer = buffer.slice(end, buffer.length());
                }
            });
            if (mBufferQueue.length() > 0) {
                handleStageStreaming();
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

    private boolean handleStageStreaming() {
        if (mTargetSocket == null) {
            //remote is not ready, just hold the buffer.
            return false;
        }
        sendToRemote(mBufferQueue);
        cleanBuffer();
        return false;
    }

    private synchronized void destory() {
        if (mCurrentStage != Stage.DESTORY) {
            mCurrentStage = Stage.DESTORY;
        } else {
            return;
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
            case Stage.HANDSHAKER:
                finish = handleStageHandshaker();
                break;
            case Stage.STREAMIMG:
                finish = handleStageStreaming();
                break;
            default:
        }
        if (finish) {
            destory();
        }
    }
}
