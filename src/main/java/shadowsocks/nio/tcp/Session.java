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

import org.apache.logging.log4j.Logger;

import java.util.concurrent.atomic.AtomicInteger;

public class Session {

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

    //For server is client IP, for client is local IP(from LAN/localhost)
    private String mLocal;
    //Target IP
    private String mRemote;

    //Stream up
    private int mL2RSize;
    //Stream down 
    private int mR2LSize;

    //Current Session number as ID
    private int mSessionID;

    public void set(String addr, boolean local) {
        if (local)
            mLocal = addr;
        else
            mRemote = addr;
    }

    public void record(int size, int direct) {
        if (size <= 0)
            return;
        if (direct == Session.LOCAL2REMOTE)
            mL2RSize += size;
        else
            mR2LSize += size;
    }

    public void dump(Logger log, Exception e) {
        log.error("Session ID: " + mSessionID + ". Target address: " + mRemote + ", client address: " + mLocal
                + ". Stream down size: " + mR2LSize + ", stream up size: " + mL2RSize + ".", e);
    }
    public int getID(){
        return mSessionID;
    }
    public void destory(){
        Session.dec();
    }
    public Session(){
        mSessionID = Session.inc();
    }
}
