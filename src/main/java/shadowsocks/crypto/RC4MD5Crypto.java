package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.RC4Engine;
import org.bouncycastle.crypto.params.KeyParameter;
import shadowsocks.GlobalConfig;

import java.io.ByteArrayOutputStream;

public class RC4MD5Crypto extends BaseCrypto {

    private final static int IV_LENGTH = GlobalConfig.get().getIvLen();

    private final static int KEY_LENGTH = 16;

    public RC4MD5Crypto(String name, String password) throws CryptoException {
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
        byte[] data = new byte[KEY_LENGTH + IV_LENGTH];
        System.arraycopy(mKey,0,data,0,KEY_LENGTH);
        System.arraycopy(iv,0,data,KEY_LENGTH,IV_LENGTH);

        byte[] hash = Utils.md5(data);

        c.init(encrypt, new KeyParameter(hash));
        return c;
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
