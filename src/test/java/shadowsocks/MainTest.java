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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.util.Config;
import shadowsocks.Shadowsocks;

public class MainTest  {

    public static Logger log = LogManager.getLogger(MainTest.class.getName());

    private Shadowsocks mLocal;
    private Shadowsocks mServer;

    private void createShadowsocks(){
        if (mLocal == null)
            mLocal = new Shadowsocks(false);
        if (mServer == null)
            mServer = new Shadowsocks(true);
    }

    private void startAll(){
        createShadowsocks();
        Thread l = new Thread(()-> { mLocal.boot(); } );
        Thread s = new Thread(()-> { mServer.boot(); } );
        l.start();
        s.start();
        try{
            //Wait for server/local boot completed.
            Thread.sleep(5000);
        }catch(InterruptedException e){
            assertTrue(false);
        }
    }

    private void stopAll(){
        mLocal.shutdown();
        mServer.shutdown();
    }

    @Before
    public void setUp(){
        log.info("Set up");
        Config.get().setPassowrd("testkey");
        Config.get().setMethod("aes-128-cfb");
        Config.get().setServer("127.0.0.1");
        Config.get().setPort(1024);
        Config.get().setLocalPort(2048);
    }
    @Test
    public void testStartStop() {
        startAll();
        stopAll();
        startAll();
        stopAll();
    }
}
