package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import shadowsocks.util.GlobalConfig;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;

public class MyCrypto extends BaseCrypto {

    private final static int IV_LENGTH = GlobalConfig.get().getIvLen();

    private final static int KEY_LENGTH = 16;

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
        byte[] data = new byte[mKeyLength + mIVLength];
        System.arraycopy(mKey,0,data,0,mKeyLength);
        System.arraycopy(iv,0,data,mKeyLength,mIVLength);

        byte[] hash = hash(data);

        c.init(encrypt, new KeyParameter(hash));
        return c;
    }



    private byte[] hash(byte[] source) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
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
