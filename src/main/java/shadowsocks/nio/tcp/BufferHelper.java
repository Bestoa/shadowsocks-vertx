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
import java.io.EOFException;
import java.io.ByteArrayOutputStream;

public class BufferHelper {

    final private static int BUFF_LEN = 8192; /* 8K */

    public static ByteBuffer prepare(ByteBuffer buffer)
    {
        buffer.clear();
        return buffer;
    }

    public static ByteBuffer prepare(ByteBuffer buffer, int size)
    {
        prepare(buffer);
        if (size < buffer.capacity())
            buffer.limit(size);
        return buffer;
    }

    public static ByteBuffer create()
    {
        return ByteBuffer.allocate(BUFF_LEN);
    }

    public static ByteBuffer create(int size)
    {
        return ByteBuffer.allocate(size);
    }

    // This logic comes from Grizzly.
    // Block current thread, avoid 100% cpu loading.
    public static void send(SocketChannel remote, byte [] newData) throws IOException
    {
        SelectionKey key = null;
        Selector writeSelector = null;
        int attempts = 0;
        // 15s for each write timeout.
        int writeTimeout = 15 * 1000;
        ByteBuffer bb = ByteBuffer.wrap(newData);
        try {
            while (bb.hasRemaining()) {
                int len = remote.write(bb);
                attempts++;
                if (len < 0){
                    throw new EOFException();
                }
                if (len == 0) {
                    if (writeSelector == null){
                        writeSelector = Selector.open();
                    }
                    key = remote.register(writeSelector, key.OP_WRITE);
                    if (writeSelector.select(writeTimeout) == 0) {
                        if (attempts > 2)
                            throw new IOException("Client disconnected");
                    } else {
                        attempts--;
                    }
                } else {
                    attempts = 0;
                }
            }
        } finally {
            if (key != null) {
                key.cancel();
                key = null;
            }
            if (writeSelector != null) {
                writeSelector.close();
            }
        }
    }
}
