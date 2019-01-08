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

public class ClientHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ClientHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;
    private final static int ADDR_TYPE_IPV6 = 4; //Not support yet

    private Vertx mVertx;
    private NetSocket mLocalSocket;
    private NetSocket mServerSocket;
    private LocalConfig mConfig;
    private int mCurrentStage;
    private Buffer mPlainTextBufferQ;
    private Buffer mEncryptTextBufferQ;
    private SSCrypto mCrypto;

    private class Stage {
        final public static int HELLO = 0;
        final public static int HEADER = 1;
        final public static int ADDRESS = 2;
        final public static int STREAMING = 3;
        final public static int DESTORY = 100;
    }

    private void nextStage() {
        if (mCurrentStage != Stage.STREAMING){
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
            log.error("Catch Exception:" + e.toString());
            destory();
        });
    }

    public ClientHandler(Vertx vertx, NetSocket socket, LocalConfig config) {
        mVertx = vertx;
        mLocalSocket = socket;
        mConfig = config;
        mCurrentStage = Stage.HELLO;
        mPlainTextBufferQ = Buffer.buffer();
        mEncryptTextBufferQ = Buffer.buffer();
        setFinishHandler(mLocalSocket);
        mCrypto = CryptoFactory.create(mConfig.method, mConfig.password);
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
     */

    private boolean handleStageHello() {
        int bufferLength = mPlainTextBufferQ.length();
        // VERSION + METHOD LEN + METHOD
        if (bufferLength < 3)
            return false;
        //SOCK5
        if (mPlainTextBufferQ.getByte(0) != 5) {
            log.warn("Protocol error.");
            return true;
        }
        int methodLen = mPlainTextBufferQ.getByte(1);
        if (bufferLength < methodLen + 2)
            return false;
        byte [] msg = {0x05, 0x00};
        mLocalSocket.write(Buffer.buffer(msg));
        //Discard the method list
        cleanPlainTextBufferQ();
        nextStage();
        return false;
    }

    private boolean handleStageHeader() {
        int bufferLength = mPlainTextBufferQ.length();
        // VERSION + MODE + RSV + ADDR TYPE
        if (bufferLength < 4)
            return false;
        // 1 connect
        // 2 bind
        // 3 udp associate
        // just support mode 1 now
        if (mPlainTextBufferQ.getByte(1) != 1) {
            log.warn("Mode != 1");
            return true;
        }
        nextStage();
        //keep the addr type
        compactPlainTextBufferQ(3);
        if (mPlainTextBufferQ.length() > 0) {
            return handleStageAddress();
        }
        return false;
    }

    private boolean handleStageAddress() {
        int bufferLength = mPlainTextBufferQ.length();
        String addr = null;
        // Construct the remote header.
        Buffer remoteHeader = Buffer.buffer();
        int addrType = mPlainTextBufferQ.getByte(0);

        remoteHeader.appendByte((byte)(addrType));

        if (addrType == ADDR_TYPE_IPV4) {
            // addr type (1) + ipv4(4) + port(2)
            if (bufferLength < 7)
                return false;
            try{
                addr = InetAddress.getByAddress(mPlainTextBufferQ.getBytes(1, 5)).toString();
            }catch(UnknownHostException e){
                log.error("UnknownHostException:" + e.toString());
                return true;
            }
            remoteHeader.appendBytes(mPlainTextBufferQ.getBytes(1,5));
            compactPlainTextBufferQ(5);
        }else if (addrType == ADDR_TYPE_HOST) {
            short hostLength = mPlainTextBufferQ.getUnsignedByte(1);
            // addr type(1) + len(1) + host + port(2)
            if (bufferLength < hostLength + 4)
                return false;
            addr = mPlainTextBufferQ.getString(2, hostLength + 2);
            remoteHeader.appendByte((byte)hostLength).appendString(addr);
            compactPlainTextBufferQ(hostLength + 2);
        }else {
            log.warn("Unsupport addr type " + addrType);
            return true;
        }
        int port = mPlainTextBufferQ.getUnsignedShort(0);
        remoteHeader.appendShort((short)port);
        compactPlainTextBufferQ(2);
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
            mServerSocket = res.result();
            setFinishHandler(mServerSocket);
            mServerSocket.handler(buffer -> { // remote socket data handler
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
                flowControl(mLocalSocket, mServerSocket);
                mLocalSocket.write(Buffer.buffer(decryptData));
            });
            // reply to program.
            byte [] msg = {0x05, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
            mLocalSocket.write(Buffer.buffer(msg));
            // send remote header.
            byte [] header = remoteHeader.getBytes();
            byte [] encryptHeader = mCrypto.encrypt(header);
            mServerSocket.write(Buffer.buffer(encryptHeader));
        });
    }

    private void sendToRemote(Buffer buffer) {
        byte [] data = buffer.getBytes();
        byte [] encryptData = mCrypto.encrypt(data);
        mServerSocket.write(Buffer.buffer(encryptData));
    }

    private void flowControl(NetSocket a, NetSocket b) {
        if (a.writeQueueFull()) {
            b.pause();
            a.drainHandler(done -> {
                b.resume();
            });
        }
    }

    private boolean handleStageStreaming() {

        // Chunk max length = 0x3fff.
        int chunkMaxLen = 0x3fff;

        flowControl(mServerSocket, mLocalSocket);

        while (mPlainTextBufferQ.length() > 0) {
            int bufferLength = mPlainTextBufferQ.length();
            int end = bufferLength > chunkMaxLen ? chunkMaxLen : bufferLength;
            sendToRemote(mPlainTextBufferQ.slice(0, end));
            compactPlainTextBufferQ(end);
        }

        return false;
    }

    private synchronized void destory() {
        if (mCurrentStage != Stage.DESTORY) {
            mCurrentStage = Stage.DESTORY;
        } else {
            return;
        }
        if (mLocalSocket != null)
            mLocalSocket.close();
        if (mServerSocket != null)
            mServerSocket.close();
    }

    @Override
    public void handle(Buffer buffer) {
        boolean finish = false;
        mPlainTextBufferQ.appendBuffer(buffer);
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
            case Stage.STREAMING:
                finish = handleStageStreaming();
                break;
            default:
        }
        if (finish) {
            destory();
        }
    }
}
