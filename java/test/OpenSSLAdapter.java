import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.List;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Class created for StackOverflow by owlstead.
 * This is open source, you are free to copy and use for any purpose.
 */
public class OpenSSLAdapter {
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


            Cipher aesCFB = Cipher.getInstance("AES/CFB/NoPadding");
            MessageDigest md5 = MessageDigest.getInstance("MD5");

            // --- create key and IV  ---

            // the IV is useless, OpenSSL might as well have use zero's
            final byte[][] keyAndIV = EVP_BytesToKey( 16, 16, md5, null, password, ITERATIONS);
            SecretKeySpec key = new SecretKeySpec(keyAndIV[0], "AES");
            IvParameterSpec iv = new IvParameterSpec("0000000000000000".getBytes(ASCII));

            // --- initialize cipher instance and decrypt ---

            aesCFB.init(Cipher.DECRYPT_MODE, key, iv);

            byte buf[]  = new byte[409600];
            InputStream source2 = new FileInputStream("py_encrypt");
            OutputStream target2 = new FileOutputStream("java_encrypt_py");
            source2.read(buf, 0, 16);
            CipherInputStream csource2 = new CipherInputStream(source2, aesCFB);
            while(true) {
                int size = csource2.read(buf);
                System.out.println("size = " + size);
                if (size <= 0)
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
