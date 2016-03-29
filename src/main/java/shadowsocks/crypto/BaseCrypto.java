package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import javax.crypto.SecretKey;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;

import shadowsocks.crypto.CryptoException;

/**
 * Crypt base class implementation
 */
public abstract class BaseCrypto implements SSCrypto
{

    protected abstract StreamBlockCipher getCipher(boolean isEncrypted) throws CryptoException;
    protected abstract SecretKey generateKey(ShadowSocksKey key);
    protected abstract void doEncrypt(byte[] data, ByteArrayOutputStream stream);
    protected abstract void doDecrypt(byte[] data, ByteArrayOutputStream stream);

    protected final String mName;
    protected final SecretKey mKey;

    protected final int mIvLength;
    protected final int mKeyLength;

    protected StreamBlockCipher mEncryptCipher = null;
    protected StreamBlockCipher mDecryptCipher = null;

    public BaseCrypto(String name, String password) throws CryptoException
    {
        mName = name.toLowerCase();
        mIvLength = getIVLength();
        mKeyLength = getKeyLength();
        if (mKeyLength == 0) {
            throw new CryptoException("Unsupport method: " + mName);
        }
        mKey = generateKey(new ShadowSocksKey(password, mKeyLength));
    }

    protected StreamBlockCipher createCipher(byte[] i, boolean encrypt) throws CryptoException
    {
        byte[] iv = new byte[mIvLength];
        System.arraycopy(i, 0, iv, 0, mIvLength);
        StreamBlockCipher c = null;
        c = getCipher(encrypt);
        ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(mKey.getEncoded()), iv);
        c.init(encrypt, parameterIV);
        return c;
    }

    public static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    @Override
    public synchronized void encrypt(byte[] data, ByteArrayOutputStream stream) throws CryptoException
    {
        stream.reset();
        if (mEncryptCipher == null) {
            byte[] iv = randomBytes(mIvLength);
            mEncryptCipher = createCipher(iv, true);
            try {
                stream.write(iv);
            } catch (IOException e) {
                throw new CryptoException(e);
            }
        }
        doEncrypt(data, stream);
    }

    @Override
    public synchronized void encrypt(byte[] data, int length, ByteArrayOutputStream stream) throws CryptoException
    {
        byte[] d = new byte[length];
        System.arraycopy(data, 0, d, 0, length);
        encrypt(d, stream);
    }

    @Override
    public synchronized void decrypt(byte[] data, ByteArrayOutputStream stream) throws CryptoException
    {
        byte[] temp;

        stream.reset();
        if (mDecryptCipher == null) {
            mDecryptCipher = createCipher(data, false);
            temp = new byte[data.length - mIvLength];
            System.arraycopy(data, mIvLength, temp, 0, data.length - mIvLength);
        } else {
            temp = data;
        }

        doDecrypt(temp, stream);
    }

    @Override
    public synchronized void decrypt(byte[] data, int length, ByteArrayOutputStream stream) throws CryptoException
    {
        byte[] d = new byte[length];
        System.arraycopy(data, 0, d, 0, length);
        decrypt(d, stream);
    }
}
