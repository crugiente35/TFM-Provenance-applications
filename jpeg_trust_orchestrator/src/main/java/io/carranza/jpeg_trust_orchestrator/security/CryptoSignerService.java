package io.carranza.jpeg_trust_orchestrator.security;

import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.springframework.stereotype.Service;
import org.mipams.jpegtrust.entities.assertions.BindingAssertion;
import org.mipams.jpegtrust.entities.JpegTrustUtils;
import org.mipams.jumbf.util.CoreUtils;
import java.io.InputStream;
import java.security.MessageDigest;
import java.io.InputStreamReader;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.security.Security;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;

@Service
public class CryptoSignerService {

    private final List<X509Certificate> certificates;
    private final PrivateKey privateKey;

    public CryptoSignerService() throws Exception {

        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }

        // =========================
        // 1. LOAD CERTIFICATES
        // =========================
        CertificateFactory certFactory = CertificateFactory.getInstance("X.509");

        try (InputStream certStream = getClass().getClassLoader()
                .getResourceAsStream("resources/certs.pem")) {

            if (certStream == null) {
                throw new RuntimeException("certs.pem not found in resources");
            }

            this.certificates = new ArrayList<>(
                    certFactory.generateCertificates(certStream)
                            .stream()
                            .map(cert -> (X509Certificate) cert)
                            .toList()
            );
        }

        if (this.certificates.isEmpty()) {
            throw new RuntimeException("No certificates loaded from certs.pem");
        }

        // =========================
        // 2. LOAD PRIVATE KEY
        // =========================
        try (InputStream keyStream = getClass().getClassLoader()
                .getResourceAsStream("resources/privKey.pem");
             PEMParser pemParser = new PEMParser(
                     new InputStreamReader(keyStream, StandardCharsets.UTF_8))) {

            if (keyStream == null) {
                throw new RuntimeException("privKey.pem not found in resources");
            }

            Object parsed = pemParser.readObject();
            JcaPEMKeyConverter converter = new JcaPEMKeyConverter().setProvider("BC");

            if (parsed instanceof PEMKeyPair) {
                this.privateKey = converter.getPrivateKey(((PEMKeyPair) parsed).getPrivateKeyInfo());
            } else if (parsed instanceof org.bouncycastle.asn1.pkcs.PrivateKeyInfo) {
                this.privateKey = converter.getPrivateKey((org.bouncycastle.asn1.pkcs.PrivateKeyInfo) parsed);
            } else {
                throw new RuntimeException("Unrecognized private key format: "
                        + (parsed == null ? "null" : parsed.getClass().getName()));
            }
        }
    }

    public List<X509Certificate> getCertificates() {
        return certificates;
    }
    public PrivateKey getPrivateKey() {
        return privateKey;
    }

    public byte[] signClaim(byte[] encodedClaimToBeSigned) throws Exception {
        Signature signature = Signature.getInstance("SHA256withECDSA");
        signature.initSign(privateKey);
        signature.update(encodedClaimToBeSigned);
        byte[] derSignature = signature.sign();
        // Convert DER ASN.1 signature to IEEE P1363 format (r||s)
        return derToP1363(derSignature, 32); // 32 bytes per component for P-256
    }

    /**
     * Converts an ECDSA signature from DER/ASN.1 encoding to IEEE P1363 (raw r||s).
     *
     * DER format: 30 len 02 rLen r... 02 sLen s...
     * P1363 format: r (fixed size) || s (fixed size)
     */
    private byte[] derToP1363(byte[] derSig, int componentSize) {
        // Parse DER: 0x30 (SEQUENCE) + length + 0x02 (INTEGER) + rLen + r + 0x02 + sLen + s
        int offset = 2; // skip 0x30 and total length

        // Parse r
        if (derSig[offset] != 0x02) throw new IllegalArgumentException("Expected INTEGER tag for r");
        int rLen = derSig[offset + 1] & 0xFF;
        offset += 2;
        byte[] r = Arrays.copyOfRange(derSig, offset, offset + rLen);
        offset += rLen;

        // Parse s
        if (derSig[offset] != 0x02) throw new IllegalArgumentException("Expected INTEGER tag for s");
        int sLen = derSig[offset + 1] & 0xFF;
        offset += 2;
        byte[] s = Arrays.copyOfRange(derSig, offset, offset + sLen);

        // Convert to fixed-size big-endian (remove leading 0x00 padding, left-pad if short)
        byte[] result = new byte[componentSize * 2];
        byte[] rFixed = toFixedSize(r, componentSize);
        byte[] sFixed = toFixedSize(s, componentSize);
        System.arraycopy(rFixed, 0, result, 0, componentSize);
        System.arraycopy(sFixed, 0, result, componentSize, componentSize);

        return result;
    }

    private byte[] toFixedSize(byte[] input, int size) {
        // BigInteger handles sign and strips/adds leading zeros
        BigInteger value = new BigInteger(1, input); // positive
        byte[] bytes = value.toByteArray();

        byte[] result = new byte[size];
        if (bytes.length <= size) {
            // Right-align (left-pad with zeros)
            System.arraycopy(bytes, 0, result, size - bytes.length, bytes.length);
        } else {
            // Trim leading zeros (BigInteger may add extra byte for sign)
            System.arraycopy(bytes, bytes.length - size, result, 0, size);
        }
        return result;
    }
    public static byte[] decodeFromDER(byte[] derSignature, int keyLength) throws Exception {
        if (derSignature[0] != 0x30)
            throw new IllegalArgumentException("Invalid DER format");

        int offset = 2;
        if (derSignature[1] > 0x80) {
            int lengthBytes = derSignature[1] & 0x7F;
            offset = 2 + lengthBytes;
        }

        if (derSignature[offset] != 0x02)
            throw new IllegalArgumentException("Invalid DER INTEGER for r");
        int rLength = derSignature[offset + 1];
        byte[] rBytes = new byte[keyLength];
        System.arraycopy(derSignature, offset + 2 + Math.max(0, rLength - keyLength),
                rBytes, Math.max(0, keyLength - rLength),
                Math.min(rLength, keyLength));

        int sOffset = offset + 2 + rLength;
        if (derSignature[sOffset] != 0x02)
            throw new IllegalArgumentException("Invalid DER INTEGER for s");
        int sLength = derSignature[sOffset + 1];
        byte[] sBytes = new byte[keyLength];
        System.arraycopy(derSignature, sOffset + 2 + Math.max(0, sLength - keyLength),
                sBytes, Math.max(0, keyLength - sLength),
                Math.min(sLength, keyLength));

        byte[] rawSignature = new byte[2 * keyLength];
        System.arraycopy(rBytes, 0, rawSignature, 0, keyLength);
        System.arraycopy(sBytes, 0, rawSignature, keyLength, keyLength);

        return rawSignature;
    }
    public static BindingAssertion getBindingAssertionForAsset(String assetFileUrl, long trustRecordRequiredBytes)
            throws Exception {

        final BindingAssertion contentBindingAssertion = new BindingAssertion();
        contentBindingAssertion.setAlgorithm("sha256");
        contentBindingAssertion.addExclusionRange(0, 0);

        byte[] digest = JpegTrustUtils.computeSha256DigestOfFileContents(assetFileUrl);
        contentBindingAssertion.setDigest(digest);

        int paddingSize = 6;

        int offsetOfExclusionRange = 0;

        offsetOfExclusionRange = CoreUtils.WORD_BYTE_SIZE;

        paddingSize = calculateMinimumBytesRequired(2, (int) trustRecordRequiredBytes);
        

        contentBindingAssertion.addExclusionRange((int) trustRecordRequiredBytes, offsetOfExclusionRange);

        byte[] pad = new byte[paddingSize];
        Arrays.fill(pad, Byte.parseByte("0"));
        contentBindingAssertion.setPadding(pad);

        return contentBindingAssertion;
    }

    private static byte[] computeDigestExcluding(byte[] data, int excludeStart, int excludeLength)
            throws Exception {
        MessageDigest sha = MessageDigest.getInstance("SHA-256");

        // Bytes before the excluded region
        if (excludeStart > 0) {
            int len = Math.min(excludeStart, data.length);
            sha.update(data, 0, len);
        }

        // Bytes after the excluded region (only relevant when C2PA is already present)
        int afterStart = excludeStart + excludeLength;
        if (afterStart < data.length) {
            sha.update(data, afterStart, data.length - afterStart);
        }

        return sha.digest();
    }

    /**
     * Returns the byte offset where the library (JpegCodestreamGenerator) will
     * insert the APP11 C2PA block into this JPEG.
     *
     * The library inserts APP11 immediately after the APP0/JFIF segment when
     * one is present (standard JFIF: offset 20), or right after the SOI (offset 2)
     * for stripped JPEGs.  If an APP11 C2PA block already exists its offset is
     * returned so the exclusion covers the old block's position.
     */
    private static int findInsertionOffset(byte[] jpeg) {
        if (jpeg == null || jpeg.length < 4) return 2;
        int i = 2; // skip SOI (FF D8)
        while (i + 3 < jpeg.length) {
            if ((jpeg[i] & 0xFF) != 0xFF) return i;
            int marker = jpeg[i + 1] & 0xFF;
            if (marker == 0xDA) return i; // SOS — insert before image data
            int segLen = ((jpeg[i + 2] & 0xFF) << 8) | (jpeg[i + 3] & 0xFF);
            if (marker == 0xEB
                    && i + 5 < jpeg.length
                    && (jpeg[i + 4] & 0xFF) == 0x4A
                    && (jpeg[i + 5] & 0xFF) == 0x50) {
                return i; // existing C2PA APP11
            }
            if (marker == 0xE0) {
                // APP0 (JFIF) — library inserts APP11 right after this segment
                return i + 2 + segLen;
            }
            i += 2 + segLen;
        }
        return 2;
    }

        public static int calculateMinimumBytesRequired(int i, int totalBytesRequired) throws Exception {
        CBORMapper mapper = new CBORMapper();
        int additionalBytesForA = mapper.writeValueAsBytes(i).length;
        int additionalBytesForB = mapper.writeValueAsBytes(totalBytesRequired).length;
        return (CoreUtils.INT_BYTE_SIZE * 2) - additionalBytesForA - additionalBytesForB;
    }
}