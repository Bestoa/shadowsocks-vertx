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
package shadowsocks;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.net.InetSocketAddress;
import java.io.IOException;
import java.util.concurrent.Future;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.nio.tcp.TcpWorker;
import shadowsocks.nio.tcp.LocalTcpWorker;
import shadowsocks.nio.tcp.ServerTcpWorker;
import shadowsocks.crypto.CryptoFactory;

import shadowsocks.util.GlobalConfig;
import shadowsocks.util.LocalConfig;

public class Shadowsocks{

    public static Logger log = LogManager.getLogger(Shadowsocks.class.getName());

    final private static int RUNNING = 1;
    final private static int STOP = 2;

    final private String mName;

    final private int mPort;

    final private byte [] mStateLock = new byte[0];

    final private boolean mIsServer;

    final private ExecutorService mExecutorService;

    final private ReentrantLock mLock = new ReentrantLock();

    private Future<Boolean> mFuture;

    private int mRunningState;

    private int getState(){
        synchronized (mStateLock) {
            return mRunningState;
        }
    }

    private void setState(int state){
        synchronized (mStateLock) {
            mRunningState = state;
        }
    }

    public Shadowsocks(boolean isServer){
        setState(STOP);
        mExecutorService = Executors.newCachedThreadPool();
        mIsServer = isServer;
        mName = (isServer ? "server" : "local") + "[" + this.hashCode() + "]";
        mPort = isServer ? GlobalConfig.get().getPort() : GlobalConfig.get().getLocalPort();
    }

    public boolean boot()
    {
        mLock.lock();
        if (getState() == RUNNING) {
            log.warn(mName + " is running.");
            mLock.unlock();
            return false;
        }

        setState(RUNNING);

        final Object finish = new Object();

        mFuture = mExecutorService.submit(new Callable<Boolean>() {
            public Boolean call() throws Exception {
                try(ServerSocketChannel server = ServerSocketChannel.open()) {
                    server.socket().bind(new InetSocketAddress(mPort));
                    server.socket().setReuseAddress(true);
                    log.info("Starting " + mName + " at " + server.socket().getLocalSocketAddress());
                    //Tell main thread, bind finish, enter mainloop.
                    synchronized(finish){
                        finish.notify();
                    }
                    while (true) {
                        SocketChannel local = server.accept();
                        if (getState() == STOP) {
                            break;
                        }
                        local.socket().setTcpNoDelay(true);
                        mExecutorService.execute(TcpWorker.create(local, mIsServer));
                    }
                    log.info("Stop " + mName + " done.");
                    return Boolean.TRUE;
                }catch(IOException e){
                    log.error(mName + " running error.", e);
                    setState(STOP);
                    return Boolean.FALSE;
                }
            }
        });
        boolean result = true;
        try{
            synchronized(finish){
                //If bind failed, they don't notify us, so just wait 2s.
                finish.wait(2000);
                if (getState() == STOP){
                    result = false;
                }
            }
        }catch(InterruptedException e){
            log.error("Waiting " + mName + " start finish error.", e);
        }
        mLock.unlock();
        return result;
    }

    public boolean shutdown()
    {
        mLock.lock();
        if(getState() == STOP){
            log.warn(mName + " is not running.");
            mLock.unlock();
            return false;
        }
        setState(STOP);
        log.info("Prepare to stop " + mName + ".");
        boolean result = false;
        try(SocketChannel sc = SocketChannel.open()){
            sc.connect(new InetSocketAddress("127.0.0.1", mPort));
        }catch(IOException e){
            //If some other thread connect to the server also make future stop;
            //ignore;
        }
        try{
            result = mFuture.get().booleanValue();
        }catch(InterruptedException | ExecutionException e){
            log.error("Get " + mName + " running result failed.", e);
            result = false;
        }
        mLock.unlock();
        return result;
    }
}
