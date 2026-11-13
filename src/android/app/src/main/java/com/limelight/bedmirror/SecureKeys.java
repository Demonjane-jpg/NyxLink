package com.limelight.bedmirror;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import java.security.KeyStore;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the exportable GameStream RSA key using a non-exportable Android Keystore AES key. */
public final class SecureKeys {
    private static javax.crypto.SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias("BedMirrorClientKey"))return (javax.crypto.SecretKey)store.getKey("BedMirrorClientKey",null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder("BedMirrorClientKey",KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build());
        return generator.generateKey();
    }
    public static byte[] wrap(byte[] value) throws java.io.IOException {
        try{Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());byte[] iv=cipher.getIV(), encrypted=cipher.doFinal(value);byte[] result=new byte[4+iv.length+encrypted.length];result[0]='B';result[1]='M';result[2]='K';result[3]=1;System.arraycopy(iv,0,result,4,iv.length);System.arraycopy(encrypted,0,result,4+iv.length,encrypted.length);return result;}catch(Exception e){throw new java.io.IOException("Could not protect pairing key",e);}
    }
    public static byte[] unwrap(byte[] value) throws java.io.IOException {
        if(value==null||value.length<4||value[0]!='B'||value[1]!='M'||value[2]!='K')return value;
        try{if(value.length<32||value[3]!=1)throw new java.io.IOException("Invalid pairing-key format");Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Arrays.copyOfRange(value,4,16)));return cipher.doFinal(Arrays.copyOfRange(value,16,value.length));}catch(Exception e){throw new java.io.IOException("Pairing key could not be unlocked. Pair again.",e);}
    }
}
