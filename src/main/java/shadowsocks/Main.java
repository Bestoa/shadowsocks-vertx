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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.crypto.CryptoFactory;

import shadowsocks.util.GlobalConfig;

public class Main{

    public static Logger log = LogManager.getLogger(Main.class.getName());

    public static final String VERSION = "0.8.3";

    public static void main(String argv[])
    {
        log.info("Shadowsocks " + VERSION);
        if (!GlobalConfig.getConfigFromArgv(argv)) {
            return;
        }
        try {
            GlobalConfig.getConfigFromFile();
        }catch(ClassCastException e) {
            log.fatal("Get config from json file error.", e);
            return;
        }
        //make sure this method could work.
        if (CryptoFactory.create(GlobalConfig.get().getMethod(), GlobalConfig.get().getPassword()) == null) {
            log.fatal("Unsupport crypto method: " + GlobalConfig.get().getMethod());
            return;
        }
        GlobalConfig.get().printConfig();
        new ShadowsocksVertx(GlobalConfig.get().isServerMode()).start();
    }
}
