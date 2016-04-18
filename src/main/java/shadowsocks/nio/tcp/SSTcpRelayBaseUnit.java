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

public abstract class SSTcpRelayBaseUnit implements Runnable {

    // Subclass should implement these methods.
    protected abstract boolean send(SocketChannel source, SocketChannel target, int direct)
        throws IOException,CryptoException,AuthException;
    protected abstract InetSocketAddress getRemote(SocketChannel local)
        throws IOException, CryptoException, AuthException;
    protected abstract void preTcpRelay(SocketChannel local, SocketChannel remote)
        throws IOException, CryptoException, AuthException;
    protected abstract void postTcpTelay(SocketChannel local, SocketChannel remote)
        throws IOException, CryptoException, AuthException;
    protected abstract void localInit() throws Exception;

    // Common work
    protected static Logger log = LogManager.getLogger(SSTcpRelayBaseUnit.class.getName());

    protected SocketChannel mClient;

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

        InetSocketAddress rAddress = getRemote(local);

        try(SocketChannel remote = SocketChannel.open();
                Selector selector = Selector.open();)
        {
            remote.socket().setTcpNoDelay(true);
            //Still use socket with timeout since some time, remote is unreachable, then client closed
            //but this thread is still hold. This will decrease CLOSE_wait state
            remote.socket().connect(rAddress, CONNECT_TIMEOUT);

            preTcpRelay(local, remote);

            doTcpRelay(selector, local, remote);

            postTcpTelay(local, remote);

        }catch(SocketTimeoutException e){
            log.warn("Remote address " + rAddress + " is unreachable", e);
        }catch(InterruptedException e){
            //ignore
        }catch(IOException | CryptoException e){
            mSession.dump(log, e);
        }

    }


    @Override
    public void run(){
        //make sure this channel could be closed
        try(SocketChannel client = mClient){

            mSession = new Session();
            mSession.set(client.socket().getRemoteSocketAddress(), true);

            mCryptor = CryptoFactory.create(Config.get().getMethod(), Config.get().getPassword());

            mBufferWrap = new SSBufferWrap();
            mBuffer = mBufferWrap.get();

            //Init subclass special field.
            localInit();

            TcpRelay(client);
        }catch(Exception e){
            mSession.dump(log, e);
        }finally{
            mSession.destory();
        }
    }
    public SSTcpRelayBaseUnit(SocketChannel c)
    {
        mClient = c;
    }
}
