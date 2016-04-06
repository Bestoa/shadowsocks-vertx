package shadowsocks.auth;

import java.util.Arrays;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import shadowsocks.auth.AuthException;

public class HmacSHA1 extends SSAuth{

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    public static final int AUTH_LEN = 10;

    @Override
    public boolean doAuth(byte[] key, byte [] data, byte [] expect) throws AuthException
    {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte [] original_result;
            byte [] result = new byte[AUTH_LEN];
            original_result = mac.doFinal(data);
            System.arraycopy(original_result, 0, result, 0, AUTH_LEN);
            return Arrays.equals(expect, result);
        }catch(NoSuchAlgorithmException | InvalidKeyException e){
            throw new AuthException(e);
        }
    }
}
