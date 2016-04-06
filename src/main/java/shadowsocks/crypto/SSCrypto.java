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
}
