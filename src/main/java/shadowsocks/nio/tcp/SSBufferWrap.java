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

public class SSBufferWrap {

    final private int BUFF_LEN = 16384; /* 16K */

    private ByteBuffer mBuffer;

    public ByteBuffer get(){
        return mBuffer;
    }

    public void prepare(){
        mBuffer.clear();
    }
    public void prepare(int size){
        prepare();
        // if data len is longer than buffer size, just read buffer size one time.
        if (size < BUFF_LEN)
            mBuffer.limit(size);
    }

    //At least read min size, otherwise will throw Exception
    //return the size has been read
    public int readWithCheck(SocketChannel s, int min) throws IOException
    {
        int size = 0;
        size = s.read(mBuffer);
        if (size < min)
            throw new IOException("Data is too short");
        return size;
    }

    public SSBufferWrap() {
        mBuffer = ByteBuffer.allocate(BUFF_LEN);
    }
}
