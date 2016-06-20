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

    public static void send(SocketChannel remote, byte [] newData, ByteArrayOutputStream bufferedData) throws IOException
    {
        if (newData != null) {
            bufferedData.write(newData);
        }
        byte [] data = bufferedData.toByteArray();
        bufferedData.reset();
        ByteBuffer out = ByteBuffer.wrap(data);
        remote.write(out);
        if (out.hasRemaining()) {
            bufferedData.write(out.slice().array());
        }
    }
}
