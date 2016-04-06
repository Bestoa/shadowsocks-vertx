package shadowsocks.auth;

import shadowsocks.auth.AuthException;

/**
 * Interface of auth
 */
public interface SSAuth{
    boolean doAuth(byte[] key, byte[] data, int length, byte[] auth) throws AuthException;
}
