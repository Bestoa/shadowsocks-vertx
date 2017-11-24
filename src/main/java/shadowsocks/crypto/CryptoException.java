package shadowsocks.crypto;

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
