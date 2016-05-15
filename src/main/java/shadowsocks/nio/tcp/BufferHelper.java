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
import java.io.IOException;

public class BufferHelper {
    /* return true means send success */
    public static boolean writeToRemote(SocketChannel remote, ByteBuffer buffer) throws IOException
    {
        //Add timeout to avoid 100% cpu when write failed for a long time.
        long timeout = System.currentTimeMillis() + 15*1000L;
        while(buffer.hasRemaining()) {
            remote.write(buffer);
            if (System.currentTimeMillis() > timeout) {
                return false;
            }
        }
        return true;
    }

    public static int readFormRemote(SocketChannel remote, ByteBuffer buffer) throws IOException
    {
        //Add timeout to avoid 100% cpu when write failed for a long time.
        long timeout = System.currentTimeMillis() + 15*1000L;
        int size = 0;
        int total_size = 0;
        while(buffer.hasRemaining()) {
            size = remote.read(buffer);
            if (size < 0)
                break;
            else
                total_size += size;
            if (System.currentTimeMillis() > timeout) {
                break;
            }
        }
        return total_size;
    }
}
