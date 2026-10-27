package com.vocagate.core.update;

import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;

public final class UpdateVerifier {
    private UpdateVerifier() {
    }

    public static String sha256Hex(byte[] payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload == null ? new byte[0] : payload);
            StringBuilder builder = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                builder.append(String.format(Locale.US, "%02x", b & 0xff));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new UpdateProtocolException("SHA-256 is unavailable", e);
        }
    }

    public static boolean matchesSha256(byte[] payload, String expectedHex) {
        if (expectedHex == null || expectedHex.trim().isEmpty()) {
            return false;
        }
        return constantTimeEquals(sha256Hex(payload), normalizeHex(expectedHex));
    }

    public static boolean verifyEd25519(byte[] payload, String signatureBase64, String publicKeyBase64) {
        try {
            byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyBase64);
            byte[] signatureBytes = Base64.getDecoder().decode(signatureBase64);
            PublicKey publicKey = KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(publicKeyBytes));
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(payload == null ? new byte[0] : payload);
            return verifier.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    private static String normalizeHex(String value) {
        String normalized = value.trim().toLowerCase(Locale.US);
        return normalized.startsWith("sha256:") ? normalized.substring("sha256:".length()) : normalized;
    }

    private static boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null || left.length() != right.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < left.length(); i++) {
            result |= left.charAt(i) ^ right.charAt(i);
        }
        return result == 0;
    }
}
