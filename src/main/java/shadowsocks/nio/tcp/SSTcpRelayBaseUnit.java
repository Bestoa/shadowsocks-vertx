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

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.io.IOException;
import java.util.Iterator;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.util.Config;

import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;

import shadowsocks.auth.AuthException;

/**
 * TcpRelay model
 * A--B--C--D
 * A is program B is SSLocal C is SSServer D is target
 * For B/C we hava 2 SocketChannels local/remote
 * For SSLocal local is the connection between A and B, remote is the connection between B and C
 * For SSServer local is the connection between B and C, remote is the connection between C and D.
 */

public abstract class SSTcpRelayBaseUnit implements Runnable {

    // Subclass should implement these methods.
    /**
     * Transform data from source to target.
     */
    protected abstract boolean send(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException;
    /**
     * Get remote address.
     */
    protected abstract InetSocketAddress getRemoteAddress(SocketChannel local) throws IOException, CryptoException, AuthException;
    /**
     * Do some work before real tcp relay.
     */
    protected abstract void preTcpRelay(SocketChannel local, SocketChannel remote) throws IOException, CryptoException, AuthException;
    /**
     * Do some work after real tcp relay.
     */
    protected abstract void postTcpTelay(SocketChannel local, SocketChannel remote) throws IOException, CryptoException, AuthException;
    /**
     * Init server/local special fields.
     */
    protected abstract void localInit() throws Exception;

    // Common work
    protected static Logger log = LogManager.getLogger(SSTcpRelayBaseUnit.class.getName());

    private SocketChannel mLocal;

    protected Session mSession;

    protected SSCrypto mCryptor;

    protected SSBufferWrap mBufferWrap;
    protected ByteBuffer mBuffer;

    protected void doTcpRelay(Selector selector, SocketChannel local, SocketChannel remote) throws IOException,InterruptedException,CryptoException,AuthException
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
                        finish = send(local, remote, SSTcpConstant.LOCAL2REMOTE);
                    }else{
                        finish = send(remote, local, SSTcpConstant.REMOTE2LOCAL);
                    }
                }
                it.remove();
            }
            if (finish)
                break;
        }
    }

    protected void TcpRelay(SocketChannel local) throws IOException, CryptoException, AuthException
    {
        int CONNECT_TIMEOUT = 3000;

        //For local this is server address, get from config
        //For server this is target address, get from parse head.
        InetSocketAddress remoteAddress = getRemoteAddress(local);

        try(SocketChannel remote = SocketChannel.open();
                Selector selector = Selector.open();)
        {
            remote.socket().setTcpNoDelay(true);
            //Still use socket with timeout since some time, remote is unreachable, then client closed
            //but this thread is still hold. This will decrease CLOSE_wait state
            remote.socket().connect(remoteAddress, CONNECT_TIMEOUT);

            //For local we need parse head and reply to proxy program.
            //For server this is dummy.
            preTcpRelay(local, remote);

            doTcpRelay(selector, local, remote);

            //This is dummy for both local and server.
            postTcpTelay(local, remote);

        }catch(SocketTimeoutException e){
            log.warn("Remote address " + remoteAddress + " is unreachable", e);
        }catch(InterruptedException e){
            //ignore
        }catch(IOException | CryptoException e){
            mSession.dump(log, e);
        }

    }


    @Override
    public void run(){
        //make sure this channel could be closed
        try(SocketChannel local = mLocal){

            mSession = new Session();
            mSession.set(local.socket().getRemoteSocketAddress(), true);

            mCryptor = CryptoFactory.create(Config.get().getMethod(), Config.get().getPassword());

            mBufferWrap = new SSBufferWrap();
            mBuffer = mBufferWrap.get();

            //Init subclass special field.
            localInit();

            TcpRelay(local);
        }catch(Exception e){
            mSession.dump(log, e);
        }finally{
            mSession.destory();
        }
    }
    public SSTcpRelayBaseUnit(SocketChannel sc)
    {
        mLocal = sc;
    }
}
