
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
        byte[] newIv = new byte[LEN];
        if(IV_LENGTH == LEN) {// 兼容原生
            newIv = iv;
        } else {// 计算 iv 的 md5 ，取前 8 byte 做 newIv
            byte[] md5 = Utils.md5(iv);
            System.arraycopy(md5,0,newIv,0,LEN);
        }

        ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(mKey), newIv);
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
