package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamCipher;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import shadowsocks.crypto.CryptoException;
import shadowsocks.crypto.Utils;

/**
 * Crypt base class implementation
 */
public abstract class BaseCrypto implements SSCrypto
{

    protected abstract StreamCipher createCipher(byte[] iv, boolean encrypt) throws CryptoException;
    protected abstract void process(byte[] in, ByteArrayOutputStream out, boolean encrypt);

    protected final String mName;
    protected final byte[] mKey;
    protected final int mIVLength;
    protected final int mKeyLength;

    protected StreamCipher mEncryptCipher = null;
    protected StreamCipher mDecryptCipher = null;

    // One SSCrypto could only do one decrypt/encrypt at the same time.
    protected ByteArrayOutputStream mData;

    private Object mLock = new Object();

    public BaseCrypto(String name, String password) throws CryptoException
    {
        mName = name.toLowerCase();
        mIVLength = getIVLength();
        mKeyLength = getKeyLength();
        if (mKeyLength == 0) {
            throw new CryptoException("Unsupport method: " + mName);
        }
        mKey = Utils.getKey(password, mKeyLength, mIVLength);
        mData = new ByteArrayOutputStream();
    }

    private byte [] encryptLocked(byte[] in) throws CryptoException
    {
        mData.reset();
        if (mEncryptCipher == null) {
            byte[] iv = Utils.randomBytes(mIVLength);
            mEncryptCipher = createCipher(iv, true);
            try {
                mData.write(iv);
            } catch (IOException e) {
                throw new CryptoException(e);
            }
        }
        process(in, mData, true);
        return mData.toByteArray();
    }

    @Override
    public byte [] encrypt(byte[] in, int length) throws CryptoException
    {
        synchronized(mLock) {
            if (length != in.length){
                byte[] tmp = new byte[length];
                System.arraycopy(in, 0, tmp, 0, length);
                return encryptLocked(tmp);
            }else{
                return encryptLocked(in);
            }
        }
    }

    private byte[] decryptLocked(byte[] in) throws CryptoException
    {
        byte[] tmp;
        mData.reset();
        if (mDecryptCipher == null) {
            mDecryptCipher = createCipher(in, false);
            tmp = new byte[in.length - mIVLength];
            System.arraycopy(in, mIVLength, tmp, 0, in.length - mIVLength);
        } else {
            tmp = in;
        }
        process(tmp, mData, false);
        return mData.toByteArray();
    }

    @Override
    public byte [] decrypt(byte[] in, int length) throws CryptoException
    {
        synchronized(mLock) {
            if (length != in.length) {
                byte[] tmp = new byte[length];
                System.arraycopy(in, 0, tmp, 0, length);
                return decryptLocked(tmp);
            }else{
                return decryptLocked(in);
            }
        }
    }
}
