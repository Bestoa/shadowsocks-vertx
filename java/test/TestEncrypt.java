import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Class created for StackOverflow by owlstead.
 * This is open source, you are free to copy and use for any purpose.
 */
public class TestEncrypt{
    private static final Charset ASCII = Charset.forName("ASCII");
    private static final int INDEX_KEY = 0;
    private static final int INDEX_IV = 1;
    private static final int ITERATIONS = 1;

    private static final int KEY_SIZE_BITS = 128;

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


    public static void main(String[] args) {
        try {
            byte password[] = "881123".getBytes(ASCII);
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            final byte[][] keyAndIV = EVP_BytesToKey(32, 16, md5, null, password, ITERATIONS);
            SecretKeySpec key = new SecretKeySpec(keyAndIV[0], "AES");
            IvParameterSpec encryptIvSpec = new IvParameterSpec(keyAndIV[1]);

            Cipher aesCFBEncrypt = Cipher.getInstance("AES/CFB/NoPadding");
            aesCFBEncrypt.init(Cipher.ENCRYPT_MODE, key, encryptIvSpec);

            byte buf[]  = new byte[4096];
            InputStream source1 = new FileInputStream("out/test_source");
            OutputStream target1 = new FileOutputStream("out/test_encrypt_java");
            target1.write(keyAndIV[1]);
            CipherOutputStream ctarget1 = new CipherOutputStream(target1, aesCFBEncrypt);
            while(true) {
                int size = source1.read(buf);
                if (size < 0)
                    break;
                ctarget1.write(buf, 0, size);

            }
            source1.close();
            ctarget1.close();

            InputStream source2;
            if (args.length != 1) {
                source2 = new FileInputStream("out/test_encrypt_java");
            }else{
                source2 = new FileInputStream("out/test_encrypt_py");
            }

            OutputStream target2 = new FileOutputStream("out/test_decrypt_java");
            byte iv[] = new byte[16];
            source2.read(iv);
            IvParameterSpec decryptIvSpec = new IvParameterSpec(iv);
            Cipher aesCFBDecrypt = Cipher.getInstance("AES/CFB/NoPadding");
            aesCFBDecrypt.init(Cipher.DECRYPT_MODE, key, decryptIvSpec);
            CipherInputStream csource2 = new CipherInputStream(source2, aesCFBDecrypt);
            while(true) {
                int size = csource2.read(buf);
                if (size < 0)
                    break;
                target2.write(buf, 0, size);
            }
            csource2.close();
            target2.close();

        }catch(Exception e){
            e.printStackTrace();
        }
    }

}
