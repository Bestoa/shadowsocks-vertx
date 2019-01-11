package shadowsocks.vertxio;

import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.net.NetClient;
import io.vertx.core.net.NetClientOptions;
import io.vertx.core.net.NetSocket;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import shadowsocks.GlobalConfig;
import shadowsocks.crypto.CryptoException;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.Utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class ServerHandler implements Handler<Buffer> {

    public static Logger log = LogManager.getLogger(ServerHandler.class.getName());

    private final static int ADDR_TYPE_IPV4 = 1;
    private final static int ADDR_TYPE_HOST = 3;
    private final static int ADDR_TYPE_IPV6 = 4;


    private Vertx mVertx;
    private NetSocket mClientSocket;
    private NetSocket mTargetSocket;
    private int mCurrentStage;
    private Buffer mBufferQueue;
    private SSCrypto mCrypto;

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
            log.error("Server setFinishHandler Exception " + e.getMessage()
                    +" local " + socket.localAddress() + " , remote " + socket.remoteAddress());
            destory();
        });
    }

    public ServerHandler(Vertx vertx, NetSocket socket) {
        mVertx = vertx;
        mClientSocket = socket;
        mCurrentStage = Stage.ADDRESS;
        mBufferQueue = Buffer.buffer();
        setFinishHandler(mClientSocket);
        try{
            mCrypto = CryptoFactory.create(GlobalConfig.get().getMethod(), GlobalConfig.get().getPassword());
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

    private boolean handleStageAddress() {
        if (GlobalConfig.get().isNoise()) {
            int flag = deleteNoiseData();
            if (flag == -1) {
                return false;
            } else if (flag == -2) {
                return true;
            }
        }

        int bufferLength = mBufferQueue.length();
        String addr = null;
        int current = 0;

        int addrType = mBufferQueue.getByte(0);

        if (addrType == ADDR_TYPE_IPV4) {
            // addrType(1) + ipv4(4) + port(2)
            if (bufferLength < 7)
                return false;
            try{
                addr = InetAddress.getByAddress(mBufferQueue.getBytes(1, 5)).getHostAddress();
                log.info("ipv4 : " + addr);
            }catch(UnknownHostException e){
                log.error("UnknownHostException.", e);
                return true;
            }
            current = 5;
        } else if (addrType == ADDR_TYPE_IPV6){
            // addrType(1) + ipv6(16) + port(2)
            if (bufferLength < 19)
                return false;
            try{
                addr = InetAddress.getByAddress(mBufferQueue.getBytes(1, 17)).getHostAddress();
                log.info("ipv6 : " + addr);
            }catch(UnknownHostException e){
                log.error("UnknownHostException.", e);
                return true;
            }
            current = 17;
        } else if (addrType == ADDR_TYPE_HOST) {
            short hostLength = mBufferQueue.getUnsignedByte(1);
            // addrType(1) + len(1) + host + port(2)
            if (bufferLength < hostLength + 4)
                return false;
            addr = mBufferQueue.getString(2, hostLength + 2);
            log.info("hostname : " + addr);
            current = hostLength + 2;
        }else {
            log.error("Unsupport addr type " + addrType);
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


    /**
     * 删除噪声数据
     *
     * @return 返回 -1 表示长度不够；
     *          返回 -2 表示客户端伪造数据（非法的 noise 长度）；
     *          返回 1 表示运行正常，噪声数据已删除
     */
    private int deleteNoiseData() {
        int bufferLength = mBufferQueue.length();
        // noiseLen(4)
        if (bufferLength < 4) {
            return -1;
        }

        byte[] noiseLenArr = new byte[4];
        mBufferQueue.getBytes(0, 4, noiseLenArr);

        compactBuffer(4);

        int noiseLenInt = Utils.byteArrayToInt(noiseLenArr);

        if (noiseLenInt <= 0 || noiseLenInt > Utils.NOISE_MAX) {// 客户端伪造数据！
            log.error("noiseLenInt error : " + noiseLenInt);
            return -2;
        }

        if (bufferLength < 4 + noiseLenInt) {// 不够噪声数据的长度！
            return -1;
        }

        // noise data
//        byte[] noiseData = new byte[noiseLenInt];
//        mBufferQueue.getBytes(0, noiseLenInt, noiseData);

        compactBuffer(noiseLenInt);

        return 1;
    }

    private void connectToRemote(String addr, int port) {

        NetClientOptions options = new NetClientOptions().setConnectTimeout(GlobalConfig.get().getTimeout()).setTcpKeepAlive(true);
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
        sendToRemote(mBufferQueue);
        cleanBuffer();

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
