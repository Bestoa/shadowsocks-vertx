package shadowsocks.crypto;

import javax.crypto.SecretKey;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.util.logging.Logger;

/**
 * Shadowsocks key generator
 */
public class ShadowSocksKey implements SecretKey {

    private final static int KEY_LENGTH = 32;
    private byte[] mKey;
    private int mLength;

    public ShadowSocksKey(String password) {
        mLength = KEY_LENGTH;
        mKey = init(password);
    }

    public ShadowSocksKey(String password, int length) {
        // TODO: Invalid key length
        mLength = length;
        mKey = init(password);
    }

    private byte[] init(String password) {
        MessageDigest md = null;
        byte[] keys = new byte[KEY_LENGTH];
        byte[] temp = null;
        byte[] hash = null;
        byte[] passwordBytes = null;
        int i = 0;

        try {
            md = MessageDigest.getInstance("MD5");
            passwordBytes = password.getBytes("ASCII");
        }
        /*
        catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        */
        catch (Exception e) {
            return null;
        }

        while (i < keys.length) {
            if (i == 0) {
                hash = md.digest(passwordBytes);
                temp = new byte[passwordBytes.length+hash.length];
            }
            else {
                System.arraycopy(hash, 0, temp, 0, hash.length);
                System.arraycopy(passwordBytes, 0, temp, hash.length, passwordBytes.length);
                hash = md.digest(temp);
            }
            System.arraycopy(hash, 0, keys, i, hash.length);
            i += hash.length;
        }

        if (mLength != KEY_LENGTH) {
            byte[] keysl = new byte[mLength];
            System.arraycopy(keys, 0, keysl, 0, mLength);
            return keysl;
        }
        return keys;
    }

    @Override
    public String getAlgorithm() {
        return "shadowsocks";
    }

    @Override
    public String getFormat() {
        return "RAW";
    }

    @Override
    public byte[] getEncoded() {
        return mKey;
    }
}
