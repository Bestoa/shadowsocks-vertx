package shadowsocks.crypto; 

import shadowsocks.crypto.CryptoException;
import shadowsocks.crypto.AESCrypto;
import shadowsocks.crypto.Chacha20Crypto;
import shadowsocks.crypto.SSCrypto;

public class CryptoFactory{

    private static final String AES = "aes";
    private static final String CHACHA20 = "chacha20";

    public static SSCrypto create(String name, String password) throws CryptoException
    {
        String cipherName = name.toLowerCase();
        if (cipherName.startsWith(AES)) {
            return new AESCrypto(name, password);
        }else if (cipherName.startsWith(CHACHA20)) {
            return new Chacha20Crypto(name, password);
        }else{
            throw new CryptoException("Unsupport method: " + name);
        }
    }
}
