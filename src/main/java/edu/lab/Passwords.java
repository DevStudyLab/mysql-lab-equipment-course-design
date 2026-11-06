package edu.lab;

import java.security.*;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class Passwords {
    private Passwords() {}
    public static String hash(String value) throws GeneralSecurityException {
        byte[] salt=new byte[18]; new SecureRandom().nextBytes(salt);
        return "120000:"+Base64.getEncoder().encodeToString(salt)+":"+Base64.getEncoder().encodeToString(derive(value,salt,120000));
    }
    public static boolean verify(String value,String encoded) throws GeneralSecurityException {
        try {
            String[] parts=encoded.split(":");
            int rounds=Integer.parseInt(parts[0]);
            if(parts.length!=3||rounds<10000||rounds>1000000) return false;
            return MessageDigest.isEqual(Base64.getDecoder().decode(parts[2]),derive(value,Base64.getDecoder().decode(parts[1]),rounds));
        } catch(IllegalArgumentException|IndexOutOfBoundsException e) { return false; }
    }
    private static byte[] derive(String value,byte[] salt,int rounds) throws GeneralSecurityException {
        PBEKeySpec spec=new PBEKeySpec(value.toCharArray(),salt,rounds,256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }
}
