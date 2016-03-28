package shadowsocks.crypto; 

import java.io.ByteArrayOutputStream;
import java.util.Map;

import shadowsocks.crypto.CryptoException;

/**
 * Interface of crypt
 */
public interface SSCrypto {
    void encrypt(byte[] data, ByteArrayOutputStream stream) throws CryptoException;
    void encrypt(byte[] data, int length, ByteArrayOutputStream stream) throws CryptoException;
    void decrypt(byte[] data, ByteArrayOutputStream stream) throws CryptoException;
    void decrypt(byte[] data, int length, ByteArrayOutputStream stream) throws CryptoException;
    int getIVLength();
    int getKeyLength();
}
