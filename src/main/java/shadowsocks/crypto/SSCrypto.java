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
package shadowsocks.crypto; 

import java.io.ByteArrayOutputStream;
import java.util.Map;

import shadowsocks.crypto.CryptoException;

/**
 * Interface of crypt
 */
public interface SSCrypto {
    byte [] encrypt(byte[] data, int length) throws CryptoException;
    byte [] decrypt(byte[] data, int length) throws CryptoException;
    int getIVLength();
    int getKeyLength();
    byte [] getIV(boolean encrypt);
    byte [] getKey();
}
