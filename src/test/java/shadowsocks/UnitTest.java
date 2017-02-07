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
package shadowsocks;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import static org.junit.Assert.*;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.FileOutputStream;
import java.io.DataInputStream;

import shadowsocks.util.GlobalConfig;
import shadowsocks.ShadowsocksVertx;

public class UnitTest {
    public static Logger log = LogManager.getLogger(UnitTest.class.getName());

    @Test
    public void testSetConfigFromArgv() {
        String [] argv = {
            "-k", "test",
            "-m", "aes-128-cfb",
            "-s", "9.8.7.6",
            "-p", "4321",
            "-l", "1234",
            "-t", "400",
            "-t", "450",
            "-S",
        };
        Main.main(argv);
        assertEquals(GlobalConfig.get().getPassword(), "test");
        assertEquals(GlobalConfig.get().getMethod(), "aes-128-cfb");
        assertEquals(GlobalConfig.get().getServer(), "9.8.7.6");
        assertEquals(GlobalConfig.get().getPort(), 4321);
        assertEquals(GlobalConfig.get().getLocalPort(), 1234);
        //The last value go into effect.
        assertEquals(GlobalConfig.get().getTimeout(), 450);
        assertEquals(GlobalConfig.get().isServerMode(), true);
    }

    @Test
    public void testSetConfigFromLongArgv() {
        String [] argv = {
            "--password", "test1",
            "--method", "aes-192-cfb",
            "--server", "6.7.8.9",
            "--server_port", "7890",
            "--local_port", "6789",
            "--timeout", "200",
            "--server_mode",
        };
        Main.main(argv);
        assertEquals(GlobalConfig.get().getPassword(), "test1");
        assertEquals(GlobalConfig.get().getMethod(), "aes-192-cfb");
        assertEquals(GlobalConfig.get().getServer(), "6.7.8.9");
        assertEquals(GlobalConfig.get().getPort(), 7890);
        assertEquals(GlobalConfig.get().getLocalPort(), 6789);
        assertEquals(GlobalConfig.get().getTimeout(), 200);
        assertEquals(GlobalConfig.get().isServerMode(), true);
    }

    @Test
    public void testSetConfigFromFile() {
        try {
            //Read demo conf.
            DataInputStream in = new DataInputStream(this.getClass().getClassLoader().getResourceAsStream("demo-conf"));
            byte [] context = new byte[8192];
            int len = in.read(context);
            //Create tmp config.
            File temp = File.createTempFile("demo-tmp", ".conf");
            temp.deleteOnExit();
            String fileName = temp.getAbsolutePath();
            FileOutputStream out = new FileOutputStream(fileName);
            out.write(context, 0, len);
            out.close();
            //Start shadowsocks with config
            String [] argv = {
                "-c", fileName,
            };
            Main.main(argv);
        } catch(Exception e) {
            log.error("Failed with exception.", e);
            fail();
        }
        assertEquals(GlobalConfig.get().getPassword(), "fakekey");
        assertEquals(GlobalConfig.get().getMethod(), "aes-128-cfb");
        assertEquals(GlobalConfig.get().getServer(), "fakeserver");
        assertEquals(GlobalConfig.get().getPort(), 1111);
        assertEquals(GlobalConfig.get().getLocalPort(), 2222);
        assertEquals(GlobalConfig.get().getTimeout(), 360);
        assertEquals(GlobalConfig.get().isServerMode(), true);
    }

    @Test
    public void testHelp() {
        String [] argv = {
            "-h",
        };
        Main.main(argv);
    }
}
