package shadowsocks.crypto;
import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;

public class CryptoException extends Exception
{
    private static final long serialVersionUID = 1L;

    public CryptoException(String message){
        super(message);
    }
    public CryptoException(Exception e){
        super(e);
    }
}
