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
import org.junit.BeforeClass;
import org.junit.AfterClass;
import static org.junit.Assert.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.util.Config;
import shadowsocks.Shadowsocks;

public class SystemTest{

    public static Logger log = LogManager.getLogger(SystemTest.class.getName());

    private static Shadowsocks mLocal;
    private static Shadowsocks mServer;

    private static void createShadowsocks(){
        mServer = new Shadowsocks(true);
        mLocal = new Shadowsocks(false);
    }

    @Before
    public void setUp(){
        log.info("Set up");
        Config.get().setPassowrd("testkey");
        Config.get().setMethod("aes-128-cfb");
        Config.get().setServer("127.0.0.1");
        Config.get().setPort(1024);
        Config.get().setLocalPort(2048);
        createShadowsocks();
    }
    @After
    public void tearDown(){
        log.info("Tear down");
    }
    @Test
    public void testStartStop() {
        //Can't shutdown before boot.
        assertFalse(mServer.shutdown());
        //Boot and shutdown
        assertTrue(mServer.boot());
        assertTrue(mLocal.boot());
        assertTrue(mServer.shutdown());
        assertTrue(mLocal.shutdown());
        //Boot again
        assertTrue(mServer.boot());
        //Two instances is not allowed.
        assertFalse(mServer.boot());
        assertTrue(mServer.shutdown());
    }
}
