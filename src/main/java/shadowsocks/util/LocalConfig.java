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
package shadowsocks.util;

import java.net.InetSocketAddress;

public class LocalConfig{
    public String password;
    public String method;
    public String server;
    public int serverPort;
    public int localPort;
    public int timeout;
    //For server is target, for local is server.
    public InetSocketAddress remoteAddress;

    public String target;

    public LocalConfig(String k, String m, String s, int p, int lp, int t){
        password = k;
        method = m;
        server = s;
        serverPort = p;
        localPort = lp;
        timeout = t;
    }
}
