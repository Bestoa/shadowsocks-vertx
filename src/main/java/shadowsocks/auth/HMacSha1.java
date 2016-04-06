package shadowsocks.auth;

import java.util.Arrays;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import shadowsocks.auth.AuthException;

public class HMacSha1 implements SSAuth{

    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    public static final int AUTH_LEN = 10;

    public boolean doAuth(byte[] key, byte [] data, int length, byte [] auth) throws AuthException
    {
        try {
            SecretKeySpec signingKey = new SecretKeySpec(key, HMAC_SHA1_ALGORITHM);
            Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
            mac.init(signingKey);
            byte [] trim_data;
            byte [] original_result;
            byte [] result = new byte[AUTH_LEN];
            if (data.length != length){
                trim_data = new byte[length];
                System.arraycopy(data, 0, trim_data, 0, length);
            }else{
                trim_data = data;
            }
            original_result = mac.doFinal(trim_data);
            System.arraycopy(original_result, 0, result, 0, AUTH_LEN);
            return Arrays.equals(auth, result);
        }catch(NoSuchAlgorithmException | InvalidKeyException e){
            throw new AuthException(e);
        }
    }
}
