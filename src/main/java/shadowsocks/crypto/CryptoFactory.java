package shadowsocks.crypto;

public class CryptoFactory{

    public static SSCrypto create(String name, String password) throws CryptoException
    {
        String cipherName = name.toLowerCase();
        if (cipherName.equals("aes-256-cfb")) {
            return new AESCrypto(name, password);
        }else if (cipherName.equals("chacha20")) {
            return new Chacha20Crypto(name, password);
        } else if (cipherName.equals("rc4-md5")) {
            return new RC4MD5Crypto("rc4-md5",password);
        } else{
            throw new CryptoException("Unsupport method: " + name);
        }
    }
}
