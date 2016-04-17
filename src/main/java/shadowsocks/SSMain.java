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

import shadowsocks.util.Config;
import shadowsocks.crypto.CryptoFactory;

public class SSMain{
    public static void main(String argv[])
    {
        System.out.println("Shadowsocks v0.5");
        Config.getConfigFromArgv(argv);
        //make sure this method could work.
        try{
            CryptoFactory.create(Config.get().getMethod(), Config.get().getPassword());
        }catch(Exception e){
            e.printStackTrace();
            return;
        }
        if(Config.get().isServerMode()){
            new SSServer().start();;
        }else{
            new SSLocal().start();;
        }
    }
}
