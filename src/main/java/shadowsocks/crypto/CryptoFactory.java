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
package shadowsocks.crypto; 

import shadowsocks.crypto.CryptoException;
import shadowsocks.crypto.AESCrypto;
import shadowsocks.crypto.Chacha20Crypto;
import shadowsocks.crypto.SSCrypto;

public class CryptoFactory{

    //V1 methods
    private static final String mSupportV1MethodsList[] =
    {
        "aes-128-cfb",
        "aes-192-cfb",
        "aes-256-cfb",
        "chacha20",
        "chacha20-ietf",
    };

    public static SSCrypto create(String name, String password)
    {
        String cipherName = name.toLowerCase();
        SSCrypto crypto = null;
        for (int i = 0; i < mSupportV1MethodsList.length; i++) {
            if (cipherName.equals(mSupportV1MethodsList[i])) {
                if (cipherName.startsWith("aes")) {
                    try {
                        crypto = new AESCrypto(name, password);
                    }catch (CryptoException e) {
                        crypto = null;
                    }
                }else if (cipherName.startsWith("chacha")) {
                    try {
                        crypto = new Chacha20Crypto(name, password);
                    }catch (CryptoException e) {
                        crypto = null;
                    }
                }
            }
        }
        return crypto;
    }
}
