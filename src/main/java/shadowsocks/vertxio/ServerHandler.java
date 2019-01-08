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

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.vertx.core.Vertx;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetSocket;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;

import shadowsocks.util.LocalConfig;
import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.DecryptState;

public class ServerHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ServerHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;
    private final static int ADDR_TYPE_IPV6 = 4; //Not support yet

    private Vertx mVertx;
    private NetSocket mClientSocket;
    private NetSocket mTargetSocket;
    private int mCurrentStage;
    private Buffer mPlainTextBufferQ;
    private Buffer mEncryptTextBufferQ;
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
            log.error("Catch Exception." + e.toString());
            destory();
        });
    }

    public ServerHandler(Vertx vertx, NetSocket socket, LocalConfig config) {
        mVertx = vertx;
        mClientSocket = socket;
        mCurrentStage = Stage.HANDSHAKER;
        mPlainTextBufferQ = Buffer.buffer();
        mEncryptTextBufferQ = Buffer.buffer();
        setFinishHandler(mClientSocket);
        mCrypto = CryptoFactory.create(config.method, config.password);
    }

    private Buffer compactPlainTextBufferQ(int start) {
        mPlainTextBufferQ = Buffer.buffer().appendBuffer(mPlainTextBufferQ.slice(start, mPlainTextBufferQ.length()));
        return mPlainTextBufferQ;
    }

    private Buffer cleanPlainTextBufferQ() {
        mPlainTextBufferQ = Buffer.buffer();
        return mPlainTextBufferQ;
    }

    private Buffer cleanEncryptTextBufferQ() {
        mEncryptTextBufferQ = Buffer.buffer();
        return mEncryptTextBufferQ;
    }

    private boolean handleStageHandshaker() {

        int bufferLength = mPlainTextBufferQ.length();
        String addr = null;
        int current = 0;

        int addrType = mPlainTextBufferQ.getByte(0);

        if (addrType == ADDR_TYPE_IPV4) {
            // addrType(1) + ipv4(4) + port(2)
            if (bufferLength < 7)
                return false;
            try{
                //remote the "/"
                addr = InetAddress.getByAddress(mPlainTextBufferQ.getBytes(1, 5)).toString().substring(1);
            }catch(UnknownHostException e){
                log.error("UnknownHostException." + e.toString());
                return true;
            }
            current = 5;
        }else if (addrType == ADDR_TYPE_HOST) {
            short hostLength = mPlainTextBufferQ.getUnsignedByte(1);
            // addrType(1) + len(1) + host + port(2)
            if (bufferLength < hostLength + 4)
                return false;
            addr = mPlainTextBufferQ.getString(2, hostLength + 2);
            current = hostLength + 2;
        }else {
            log.warn("Unsupport addr type " + addrType);
            return true;
        }
        int port = mPlainTextBufferQ.getUnsignedShort(current);
        current = current + 2;
        compactPlainTextBufferQ(current);
        log.info("Connecting to " + addr + ":" + port);
        connectToRemote(addr, port);
        nextStage();
        return false;
    }

    private void sendToClient(Buffer buffer) {
        byte [] data = buffer.getBytes();
        byte [] encryptData = mCrypto.encrypt(data);
        mClientSocket.write(Buffer.buffer(encryptData));
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
            if (mPlainTextBufferQ.length() > 0) {
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
        sendToRemote(mPlainTextBufferQ);
        cleanPlainTextBufferQ();
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
        byte [] data = mEncryptTextBufferQ.appendBuffer(buffer).getBytes();
        byte [][] decryptResult = mCrypto.decrypt(data);
        int lastState = mCrypto.getLastDecryptState();
        if (lastState == DecryptState.FAILED) {
            destory();
        } else if (lastState == DecryptState.NEED_MORE) {
            return;
        }
        byte [] decryptData = decryptResult[0];
        byte [] encryptDataLeft = decryptResult[1];
        cleanEncryptTextBufferQ();
        if (encryptDataLeft != null) {
            mEncryptTextBufferQ.appendBytes(encryptDataLeft);
        }
        mPlainTextBufferQ.appendBytes(decryptData);
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
