package shadowsocks.crypto; 

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * Interface of crypt
 */
public interface ICrypt {
    void encrypt(byte[] data, ByteArrayOutputStream stream);
    void encrypt(byte[] data, int length, ByteArrayOutputStream stream);
    void decrypt(byte[] data, ByteArrayOutputStream stream);
    void decrypt(byte[] data, int length, ByteArrayOutputStream stream);
    int getIVLength();
    int getKeyLength();
}
