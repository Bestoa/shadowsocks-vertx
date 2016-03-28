package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamBlockCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.engines.AESFastEngine;
import org.bouncycastle.crypto.modes.CFBBlockCipher;
import org.bouncycastle.crypto.modes.OFBBlockCipher;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.InvalidAlgorithmParameterException;
import java.util.HashMap;
import java.util.Map;

import shadowsocks.crypto.CryptoException;

/**
 * AES Crypt implementation
 */
public class AESCrypto extends BaseCrypto {

    public final static String CIPHER_AES_128_CFB = "aes-128-cfb";
    public final static String CIPHER_AES_192_CFB = "aes-192-cfb";
    public final static String CIPHER_AES_256_CFB = "aes-256-cfb";
    public final static String CIPHER_AES_128_OFB = "aes-128-ofb";
    public final static String CIPHER_AES_192_OFB = "aes-192-ofb";
    public final static String CIPHER_AES_256_OFB = "aes-256-ofb";

    public AESCrypto(String name, String password) throws CryptoException {
        super(name, password);
    }

    @Override
    public int getKeyLength() {
        if(mName.equals(CIPHER_AES_128_CFB) || mName.equals(CIPHER_AES_128_OFB)) {
            return 16;
        }
        else if (mName.equals(CIPHER_AES_192_CFB) || mName.equals(CIPHER_AES_192_OFB)) {
            return 24;
        }
        else if (mName.equals(CIPHER_AES_256_CFB) || mName.equals(CIPHER_AES_256_OFB)) {
            return 32;
        }

        return 0;
    }

    @Override
    protected StreamBlockCipher getCipher(boolean isEncrypted) throws CryptoException
    {
        AESFastEngine engine = new AESFastEngine();
        StreamBlockCipher cipher;

        if (mName.equals(CIPHER_AES_128_CFB)) {
            cipher = new CFBBlockCipher(engine, getIVLength() * 8);
        }
        else if (mName.equals(CIPHER_AES_192_CFB)) {
            cipher = new CFBBlockCipher(engine, getIVLength() * 8);
        }
        else if (mName.equals(CIPHER_AES_256_CFB)) {
            cipher = new CFBBlockCipher(engine, getIVLength() * 8);
        }
        else if (mName.equals(CIPHER_AES_128_OFB)) {
            cipher = new OFBBlockCipher(engine, getIVLength() * 8);
        }
        else if (mName.equals(CIPHER_AES_192_OFB)) {
            cipher = new OFBBlockCipher(engine, getIVLength() * 8);
        }
        else if (mName.equals(CIPHER_AES_256_OFB)) {
            cipher = new OFBBlockCipher(engine, getIVLength() * 8);
        }
        else {
            throw new CryptoException("Invalid AlgorithmParameter: " + mName);
        }

        return cipher;
    }

    final static int IV_LENGTH = 16;

    @Override
    public int getIVLength() {
        return IV_LENGTH;
    }

    @Override
    protected SecretKey generateKey(ShadowSocksKey key) {
        return new SecretKeySpec(key.getEncoded(), "AES");
    }

    @Override
    protected void doEncrypt(byte[] data, ByteArrayOutputStream stream) {
        int size;
        byte[] buffer = new byte[data.length];
        size = mEncryptCipher.processBytes(data, 0, data.length, buffer, 0);
        stream.write(buffer, 0, size);
    }

    @Override
    protected void doDecrypt(byte[] data, ByteArrayOutputStream stream) {
        int size;
        byte[] buffer = new byte[data.length];

        size = mDecryptCipher.processBytes(data, 0, data.length, buffer, 0);
        stream.write(buffer, 0, size);
    }
}
