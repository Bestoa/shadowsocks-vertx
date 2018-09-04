package shadowsocks.crypto;

import org.bouncycastle.crypto.StreamBlockCipher;
import org.bouncycastle.crypto.StreamCipher;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.CFBBlockCipher;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.crypto.params.ParametersWithIV;
import shadowsocks.util.GlobalConfig;

import java.io.ByteArrayOutputStream;

public class AESCrypto extends BaseCrypto {

    private final static int IV_LENGTH = GlobalConfig.get().getIvLen();

    private final static int LEN = 16;

    private final static int KEY_LENGTH = 32;

    public AESCrypto(String name, String password) throws CryptoException {
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

    protected StreamBlockCipher getCipher() throws CryptoException
    {
        AESEngine engine = new AESEngine();
        return new CFBBlockCipher(engine, LEN * 8);
    }

    @Override
    protected StreamCipher createCipher(byte[] iv, boolean encrypt) throws CryptoException
    {
        StreamBlockCipher c = getCipher();

        ParametersWithIV parameterIV = new ParametersWithIV(new KeyParameter(mKey), iv);
        c.init(encrypt, parameterIV);
        return c;
    }

    @Override
    protected void process(byte[] in, ByteArrayOutputStream out, boolean encrypt){
        int size;
        byte[] buffer = new byte[in.length];
        StreamBlockCipher cipher;
        if (encrypt){
            cipher = (StreamBlockCipher)mEncryptCipher;
        }else{
            cipher = (StreamBlockCipher)mDecryptCipher;
        }
        size = cipher.processBytes(in, 0, in.length, buffer, 0);
        out.write(buffer, 0, size);
    }
}
