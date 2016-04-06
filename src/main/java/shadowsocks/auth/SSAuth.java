package shadowsocks.auth;

import java.nio.ByteBuffer;

import shadowsocks.auth.AuthException;

/**
 * Auth base class
 */
public abstract class SSAuth{
    
    public abstract boolean doAuth(byte[] key, byte[] data, byte[] auth) throws AuthException;

    public static byte [] prepareKey(byte [] i, int c){
        byte [] key = new byte[i.length + 4];
        ByteBuffer b = ByteBuffer.allocate(4);
        b.putInt(c);
        System.arraycopy(i, 0, key, 0, i.length);
        System.arraycopy(b.array(), 0, key, i.length, 4);
        return key;
    }

    public static byte [] prepareKey(byte [] i, byte [] k){
        byte [] key = new byte[i.length + k.length];
        System.arraycopy(i, 0, key, 0, i.length);
        System.arraycopy(k, 0, key, i.length, k.length);
        return key;
    }
}
