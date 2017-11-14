package shadowsocks.crypto;

import java.util.Arrays;

/**
 * 我的算法，超简单
 */
public class MyCrypto implements SSCrypto {
    /**
     * 密码/随机数的长度
     */
    private final int len;

    /**
     * 密码
     */
    private final byte[] mKey = new byte[]{-100, 9, 17, -23, 77, -2, 65};

    /**
     * 加密的随机数
     */
    private byte[] mEncryptIV;

    /**
     * 解密的随机数
     */
    private byte[] mDecryptIV;

    public MyCrypto() {
        this.len = mKey.length;
    }


    private Object mLock = new Object();

    /**
     * 获得密码
     */
    public byte[] getKey() {
        return mKey;
    }

    /**
     * 获得随机数
     * true 加密随机数
     * false 解密随机数
     */
    public byte[] getIV(boolean encrypt) {
        if (encrypt) {
            if (mEncryptIV == null) {
                mEncryptIV = Utils.randomBytes(len);
            }
            return mEncryptIV;
        } else {
            if (mDecryptIV == null) {
                mDecryptIV = Utils.randomBytes(len);
            }
            return mDecryptIV;
        }
    }

    /**
     * 获得密码的长度
     */
    @Override
    public int getKeyLength() {
        return len;
    }

    /**
     * 获得随机数的长度
     */
    @Override
    public int getIVLength() {
        return len;
    }


    /**
     * 核心加密算法
     */
    private byte[] encryptLocked(byte[] in) throws CryptoException {
        final int length = in.length;
        byte[] ret = new byte[length];

        for (int i = 0; i < length; i++) {
            int indexInKey = i % len;
            ret[i] = (byte) (in[i] ^ mKey[indexInKey]);
        }
        return ret;
    }

    /**
     * 加密，只加密前 length 的数据
     */
    @Override
    public byte[] encrypt(byte[] in, int length) throws CryptoException {
        synchronized (mLock) {
            if (length != in.length) {
                byte[] data = new byte[length];
                System.arraycopy(in, 0, data, 0, length);
                return encryptLocked(data);
            } else {
                return encryptLocked(in);
            }
        }
    }

    /**
     * 核心解密算法
     */
    private byte[] decryptLocked(byte[] in) throws CryptoException {
        final int length = in.length;
        byte[] ret = new byte[length];

        for (int i = 0; i < length; i++) {
            int indexInKey = i % len;
            ret[i] = (byte) (in[i] ^ mKey[indexInKey]);
        }
        return ret;
    }

    /**
     * 解密，只解密前 length 的数据
     */
    @Override
    public byte[] decrypt(byte[] in, int length) throws CryptoException {
        synchronized (mLock) {
            if (length != in.length) {
                byte[] data = new byte[length];
                System.arraycopy(in, 0, data, 0, length);
                return decryptLocked(data);
            } else {
                return decryptLocked(in);
            }
        }
    }

    /**
     * 测试加密
     */
    public static void main(String[] args) throws Exception{
        byte[] testData = new byte[]{11,5,7,-100,66,-19,0,11,11,11,11,11,11,11,11,11,11};
        System.out.println(Arrays.toString(testData));

        byte[] encrypt = new MyCrypto().encryptLocked(testData);
        System.out.println(Arrays.toString(encrypt));

        byte[] decrypt = new MyCrypto().decryptLocked(encrypt);
        System.out.println(Arrays.toString(decrypt));
    }
}
