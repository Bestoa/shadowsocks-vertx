package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.params.KeyParameter;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

public class MyCrypto extends BaseCrypto {

    private final static int IV_LENGTH = 11;

    private final static int KEY_LENGTH = 29;

    public MyCrypto(String name, String password) throws CryptoException {
        super(name, password);
    }

    @Override
    public int getIVLength() {
        return IV_LENGTH;
    }

    @Override
    public int getKeyLength() {
        return KEY_LENGTH;
    }

    @Override
    protected StreamCipher createCipher(byte[] iv, boolean encrypt) throws CryptoException
    {
        StreamCipher c = new RC4Engine();
        byte[] data = new byte[mKeyLength];
        // 异或
        for (int i = 0; i < mKeyLength; i++) {
            int index = i % mIVLength;
            data[i] = (byte) (mKey[i] ^ iv[index]);
        }

        byte[] hash = hash(data);

        c.init(encrypt, new KeyParameter(hash));
        return c;
    }



    private byte[] hash(byte[] source) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-512");
            return md.digest(source);
        } catch (Exception e) {
            // 抛出去
            throw new RuntimeException(e);
        }
    }

    @Override
    protected void process(byte[] in, ByteArrayOutputStream out, boolean encrypt){
        int size;
        byte[] buffer = new byte[in.length];
        StreamCipher cipher;
        if (encrypt){
            cipher = mEncryptCipher;
        }else{
            cipher = mDecryptCipher;
        }
        size = cipher.processBytes(in, 0, in.length, buffer, 0);
        out.write(buffer, 0, size);
    }
}
