import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.Cipher;
public class encrypt{
    public static void main(String argv[]){
        try {
            byte[] iv = new byte[] { 0x0, 0x1, 0x2, 0x3, 0x4, 0x5, 0x6, 0x7, 0x8, 0x9, 0xA, 0xB, 0xC, 0xD, 0xE, 0xF }; 
            IvParameterSpec ivSpec = new IvParameterSpec(iv);
            byte[] b = new String("This is a test string").getBytes();

            SecretKeySpec skeySpec = new SecretKeySpec(new String("8811230000000000").getBytes(), "AES");
            Cipher cipher1 = Cipher.getInstance("AES/CFB/NoPadding");
            cipher1.init(Cipher.ENCRYPT_MODE, skeySpec, ivSpec);
            byte[] c = cipher1.doFinal(b);
            System.out.println(new String(c));
            Cipher cipher2 = Cipher.getInstance("AES/CFB/NoPadding");
            cipher2.init(Cipher.DECRYPT_MODE, skeySpec, ivSpec);
            System.out.println(new String(cipher2.doFinal(c)));
        }catch(Exception e){
            e.printStackTrace();
        }

    }
}
