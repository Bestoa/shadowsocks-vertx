package shadowsocks.vertxio;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shadowsocks.crypto.CryptoException;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.SSCrypto;
import shadowsocks.util.LocalConfig;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ClientHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ClientHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;

    private Vertx mVertx;
    private NetSocket mLocalSocket;
    private NetSocket mServerSocket;
    private LocalConfig mConfig;
    private int mCurrentStage;
    private Buffer mBufferQueue;
    private SSCrypto mCrypto;

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
            log.error("Client setFinishHandler Exception " + e.getMessage()
                    +" local " + socket.localAddress() + " , remote " + socket.remoteAddress());
            destory();
        });
    }

    public ClientHandler(Vertx vertx, NetSocket socket, LocalConfig config) {
        mVertx = vertx;
        mLocalSocket = socket;
        mConfig = config;
        mCurrentStage = Stage.HELLO;
        mBufferQueue = Buffer.buffer();
        setFinishHandler(mLocalSocket);
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

        remoteHeader.appendByte((byte)(addrType));

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
        NetClientOptions options = new NetClientOptions().setConnectTimeout(this.mConfig.timeout).setTcpKeepAlive(true);
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
                try {
                    byte [] data = buffer.getBytes();
                    byte [] decryptData = mCrypto.decrypt(data, data.length);
                    flowControl(mLocalSocket, mServerSocket);
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

                byte [] header = remoteHeader.getBytes();
                byte [] encryptHeader = mCrypto.encrypt(header, header.length);
                mServerSocket.write(Buffer.buffer(encryptHeader));
            }catch(CryptoException e){
                log.error("Catch exception", e);
                destory();
            }
        });
    }

    private void sendToRemote(Buffer buffer) {

        Buffer chunkBuffer = Buffer.buffer();
        try{

            chunkBuffer.appendBuffer(buffer);
            byte [] data = chunkBuffer.getBytes();
            byte [] encryptData = mCrypto.encrypt(data, data.length);
            if (mServerSocket.writeQueueFull()) {
                log.warn("-->remote write queue full");
            }
            flowControl(mServerSocket, mLocalSocket);
            mServerSocket.write(Buffer.buffer(encryptData));
        }catch(CryptoException e){
            log.error("Catch exception", e);
            destory();
        }
    }

    private void flowControl(NetSocket a, NetSocket b) {
        if (a.writeQueueFull()) {
            b.pause();
            a.drainHandler(done -> {
                b.resume();
            });
        }
    }

    private boolean handleStageData() {

        // Chunk max length = 8192.
        int chunkMaxLen = 8192;

        while (mBufferQueue.length() > 0) {
            int bufferLength = mBufferQueue.length();
            int end = bufferLength > chunkMaxLen ? chunkMaxLen : bufferLength;
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
        if (mServerSocket != null)
            mServerSocket.close();
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
