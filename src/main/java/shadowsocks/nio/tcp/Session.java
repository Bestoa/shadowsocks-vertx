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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.ByteArrayOutputStream;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

public class Session {

    private static Logger log = LogManager.getLogger(Session.class.getName());

    final static public int ADDR_TYPE_IPV4 = 0x01;
    final static public int ADDR_TYPE_HOST = 0x03;

    final static public int LOCAL2REMOTE = 1;
    final static public int REMOTE2LOCAL = 2;

    final static public int OTA_FLAG = 0x10;

    private static AtomicInteger mSessionNumber = new AtomicInteger(0);

    private static int inc(){
            return mSessionNumber.incrementAndGet();
    }
    private static void dec(){
            mSessionNumber.decrementAndGet();
    }

    //For server is client, for client is local(from LAN/localhost)
    private SocketChannel mLocal;
    //For server is target, for client is server
    private SocketChannel mRemote;

    //Stream up
    private int mL2RSize;
    //Stream down 
    private int mR2LSize;

    private int mSessionID;

    private long mTimeout = 30 * 1000;

    private long mLastActiveTime;

    public void updateActiveTime(){
        mLastActiveTime = System.currentTimeMillis();
    }

    public boolean isTimeout() {
        return System.currentTimeMillis() - mLastActiveTime > mTimeout;
    }

    public void set(SocketChannel sc, boolean isLocal) {
        if (isLocal)
            mLocal = sc;
        else
            mRemote = sc;
    }

    public SocketChannel get(boolean isLocal) {
        return isLocal?mLocal:mRemote;
    }

    public void record(int size, int direct) {
        if (size <= 0)
            return;
        if (direct == Session.LOCAL2REMOTE)
            mL2RSize += size;
        else
            mR2LSize += size;
    }

    public void dump(Exception e) {
        log.error("Remote: " + mRemote + ", local: " + mLocal + ". Stream down size: " + mR2LSize + ", stream up size: " + mL2RSize + ".", e);
    }
    public int getID(){
        return mSessionID;
    }
    public void destory(){
        Session.dec();
    }
    public Session(){
        Session.inc();
        mSessionID = this.hashCode();
        updateActiveTime();
    }
}
