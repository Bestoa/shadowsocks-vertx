package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * Crypt base class implementation
 */
public abstract class CryptBase implements ICrypt {

    protected abstract StreamBlockCipher getCipher(boolean isEncrypted) throws InvalidAlgorithmParameterException;
    protected abstract SecretKey getKey();
    protected abstract void doEncrypt(byte[] data, ByteArrayOutputStream stream);
    protected abstract void doDecrypt(byte[] data, ByteArrayOutputStream stream);

    protected final String mName;
    protected final SecretKey mKey;
    protected final ShadowSocksKey mSSKey;

    protected final int mIvLength;
    protected final int mKeyLength;
    //The IV is the head of stream
    protected boolean mEncryptIVSend;
    protected boolean mDecryptIVRecv;

    protected byte[] mEncryptIV;
    protected byte[] mDecryptIV;
    protected final Lock encLock = new ReentrantLock();
    protected final Lock decLock = new ReentrantLock();

    protected StreamBlockCipher mEncryptCipher;
    protected StreamBlockCipher mDecryptCipher;

    public CryptBase(String name, String password) {
        mName = name.toLowerCase();
        mIvLength = getIVLength();
        mKeyLength = getKeyLength();
        mSSKey = new ShadowSocksKey(password, mKeyLength);
        mKey = getKey();
    }

    protected void setIV(byte[] iv, boolean isEncrypt)
    {
        if (mIvLength == 0) {
            return;
        }

        if (isEncrypt)
        {
            mEncryptIV = new byte[mIvLength];
            System.arraycopy(iv, 0, mEncryptIV, 0, mIvLength);
            try {
                mEncryptCipher = getCipher(true);
                ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(mKey.getEncoded()), mEncryptIV);
                mEncryptCipher.init(true, parameterIV);
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
        }
        else
        {
            mDecryptIV = new byte[mIvLength];
            System.arraycopy(iv, 0, mDecryptIV, 0, mIvLength);
            try {
                mDecryptCipher = getCipher(false);
                ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(mKey.getEncoded()), mDecryptIV);
                mDecryptCipher.init(false, parameterIV);
            } catch (InvalidAlgorithmParameterException e) {
                e.printStackTrace();
            }
        }
    }

    public static byte[] randomBytes(int size) {
        byte[] bytes = new byte[size];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    @Override
    public void encrypt(byte[] data, ByteArrayOutputStream stream) {
        synchronized (encLock) {
            stream.reset();
            if (!mEncryptIVSend) {
                mEncryptIVSend = true;
                byte[] iv = randomBytes(mIvLength);
                setIV(iv, true);
                try {
                    stream.write(iv);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

            doEncrypt(data, stream);
        }
    }

    @Override
    public void encrypt(byte[] data, int length, ByteArrayOutputStream stream) {
        byte[] d = new byte[length];
        System.arraycopy(data, 0, d, 0, length);
        encrypt(d, stream);
    }

    @Override
    public void decrypt(byte[] data, ByteArrayOutputStream stream) {
        byte[] temp;

        synchronized (decLock) {
            stream.reset();
            if (!mDecryptIVRecv) {
                mDecryptIVRecv = true;
                setIV(data, false);
                temp = new byte[data.length - mIvLength];
                System.arraycopy(data, mIvLength, temp, 0, data.length - mIvLength);
            } else {
                temp = data;
            }

            doDecrypt(temp, stream);
        }
    }

    @Override
    public void decrypt(byte[] data, int length, ByteArrayOutputStream stream) {
        byte[] d = new byte[length];
        System.arraycopy(data, 0, d, 0, length);
        decrypt(d, stream);
    }
}
