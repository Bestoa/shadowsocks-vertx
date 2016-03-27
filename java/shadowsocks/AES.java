package shadowsocks;

import java.nio.charset.Charset;
import java.security.MessageDigest;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.NoSuchPaddingException;

import java.security.NoSuchAlgorithmException;
import java.security.InvalidKeyException;
import java.security.InvalidAlgorithmParameterException;

public class AES{
    private static final Charset ASCII = Charset.forName("ASCII");
    private static final int INDEX_KEY = 0;
    private static final int INDEX_IV = 1;
    private static final int ITERATIONS = 1;

    /**
     * Thanks go to Ola Bini for releasing this source on his blog.
     * The source was obtained from <a href="http://olabini.com/blog/tag/evp_bytestokey/">here</a> .
     */
    public static byte[][] EVP_BytesToKey(int key_len, int iv_len, MessageDigest md, byte[] salt, byte[] data, int count) {
        byte[][] both = new byte[2][];
        byte[] key = new byte[key_len];
        int key_ix = 0;
        byte[] iv = new byte[iv_len];
        int iv_ix = 0;
        both[0] = key;
        both[1] = iv;
        byte[] md_buf = null;
        int nkey = key_len;
        int niv = iv_len;
        int i = 0;
        if (data == null) {
            return both;
        }
        int addmd = 0;
        for (;;) {
            md.reset();
            if (addmd++ > 0) {
                md.update(md_buf);
            }
            md.update(data);
            if (null != salt) {
                md.update(salt, 0, 8);
            }
            md_buf = md.digest();
            for (i = 1; i < count; i++) {
                md.reset();
                md.update(md_buf);
                md_buf = md.digest();
            }
            i = 0;
            if (nkey > 0) {
                for (;;) {
                    if (nkey == 0)
                        break;
                    if (i == md_buf.length)
                        break;
                    key[key_ix++] = md_buf[i];
                    nkey--;
                    i++;
                }
            }
            if (niv > 0 && i != md_buf.length) {
                for (;;) {
                    if (niv == 0)
                        break;
                    if (i == md_buf.length)
                        break;
                    iv[iv_ix++] = md_buf[i];
                    niv--;
                    i++;
                }
            }
            if (nkey == 0 && niv == 0) {
                break;
            }
        }
        for (i = 0; i < md_buf.length; i++) {
            md_buf[i] = 0;
        }
        return both;
    }

    public static Cipher getCipher(String p, byte iv[], int type)
        throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException
    {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte password[] = p.getBytes(ASCII);
        byte[][] keyAndIV = EVP_BytesToKey(type/8, 16, md5, null, password, 1);
        SecretKeySpec key = new SecretKeySpec(keyAndIV[0], "AES");
        IvParameterSpec encryptIvSpec = new IvParameterSpec(iv);
        Cipher aesCFBEncrypt = Cipher.getInstance("AES/CFB/NoPadding");
        aesCFBEncrypt.init(Cipher.ENCRYPT_MODE, key, encryptIvSpec);

        return aesCFBEncrypt;
    }

    public static Cipher getDeCipher(String p, byte iv[], int type)
        throws InvalidKeyException, NoSuchAlgorithmException, NoSuchPaddingException, InvalidAlgorithmParameterException
    {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        byte password[] = p.getBytes(ASCII);
        byte[][] keyAndIV = EVP_BytesToKey(type/8, 16, md5, null, password, 1);
        SecretKeySpec key = new SecretKeySpec(keyAndIV[0], "AES");
        IvParameterSpec decryptIvSpec = new IvParameterSpec(iv);
        Cipher aesCFBDecrypt = Cipher.getInstance("AES/CFB/NoPadding");
        aesCFBDecrypt.init(Cipher.DECRYPT_MODE, key, decryptIvSpec);

        return aesCFBDecrypt;
    }
}
