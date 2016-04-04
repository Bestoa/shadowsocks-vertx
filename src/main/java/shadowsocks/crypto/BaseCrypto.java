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

    public BaseCrypto(String name, String password) throws CryptoException
    {
        mName = name.toLowerCase();
        mIVLength = getIVLength();
        mKeyLength = getKeyLength();
        if (mKeyLength == 0) {
            throw new CryptoException("Unsupport method: " + mName);
        }
        mKey = Utils.getKey(password, mKeyLength, mIVLength);
    }

    @Override
    public synchronized void encrypt(byte[] in, ByteArrayOutputStream out) throws CryptoException
    {
        out.reset();
        if (mEncryptCipher == null) {
            byte[] iv = Utils.randomBytes(mIVLength);
            mEncryptCipher = createCipher(iv, true);
            try {
                out.write(iv);
            } catch (IOException e) {
                throw new CryptoException(e);
            }
        }
        process(in, out, true);
    }

    @Override
    public synchronized void encrypt(byte[] in, int length, ByteArrayOutputStream out) throws CryptoException
    {
        if (length != in.length){
            byte[] tmp = new byte[length];
            System.arraycopy(in, 0, tmp, 0, length);
            encrypt(tmp, out);
        }else{
            encrypt(in, out);
        }

    }

    @Override
    public synchronized void decrypt(byte[] in, ByteArrayOutputStream out) throws CryptoException
    {
        byte[] tmp;
        out.reset();
        if (mDecryptCipher == null) {
            mDecryptCipher = createCipher(in, false);
            tmp = new byte[in.length - mIVLength];
            System.arraycopy(in, mIVLength, tmp, 0, in.length - mIVLength);
        } else {
            tmp = in;
        }
        process(tmp, out, false);
    }

    @Override
    public synchronized void decrypt(byte[] in, int length, ByteArrayOutputStream out) throws CryptoException
    {
        if (length != in.length) {
            byte[] tmp = new byte[length];
            System.arraycopy(in, 0, tmp, 0, length);
            decrypt(tmp, out);
        }else{
            decrypt(in, out);
        }
    }
}
