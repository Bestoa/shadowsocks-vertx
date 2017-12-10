
package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.ChaChaEngine;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import shadowsocks.util.GlobalConfig;

import java.io.ByteArrayOutputStream;

public class Chacha20Crypto extends BaseCrypto {


    private final static int IV_LENGTH = GlobalConfig.get().getIvLen();

    private final static int LEN = 8;

    private final static int KEY_LENGTH = 32;

    public Chacha20Crypto(String name, String password) throws CryptoException {
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
        StreamCipher c = new ChaChaEngine();
        byte[] newIv = new byte[8];
        if (IV_LENGTH < LEN) {// 长度不够填充0
            System.arraycopy(iv,0,newIv,0,IV_LENGTH);
            for (int i = IV_LENGTH; i < LEN; i++) {
                newIv[i] = 0;
            }
        } else if(IV_LENGTH == LEN) {
            newIv = iv;
        } else {// 长度超出部分舍弃掉
            System.arraycopy(iv,0,newIv,0,LEN);
        }

        ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(mKey), newIv, 0, LEN);
        c.init(encrypt, parameterIV);
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
