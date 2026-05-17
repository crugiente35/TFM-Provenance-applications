package io.carranza.jpeg_trust_orchestrator.handler;

import io.carranza.jpeg_trust_orchestrator.dto.MediaExtractionResult;
import io.carranza.jpeg_trust_orchestrator.util.MediaFormatUtils;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Component
public class Mp4LabellingHandler {

    private static final byte[] C2PA_ISOBMFF_UUID = hexToBytes("D8FEC3D61B0E4B9A829B3E7B91DD5F35");
    private static final byte[] GOOGLE_C2PA_UUID = hexToBytes("D8FEC3D61B0E483C92975828877EC481"); 
    private static final byte[] BOX_UUID = {'u','u','i','d'};

    // Cabecera "manifest" utilizada por el estándar de Google (21 bytes)
    private static final byte[] GOOGLE_MANIFEST_HEADER = {
        0x00, 0x00, 0x00, 0x00,
        0x6D, 0x61, 0x6E, 0x69, 0x66, 0x65, 0x73, 0x74,
        0x00, 0x00, 0x00, 0x00,
        0x00, 0x00, 0x00, 0x00,
        0x00
    };

    public boolean isMp4(byte[] bytes) {
        if (bytes == null || bytes.length < 8) return false;
        String t = new String(bytes, 4, 4, StandardCharsets.US_ASCII);
        return "ftyp".equals(t); 
    }

    public MediaExtractionResult extract(byte[] mp4) throws IOException {
        byte[] c2paPayload = null;
        int foundOffset = -1;

        for (int i = 0; i <= mp4.length - 20; i++) {
            if (mp4[i] == 'u' && mp4[i+1] == 'u' && mp4[i+2] == 'i' && mp4[i+3] == 'd') {
                int boxStart = i - 4;
                if (boxStart < 0) continue;

                long totalSize = MediaFormatUtils.readUint32BE(mp4, boxStart);
                int uuidOffset = i + 4;
                if (totalSize == 1) uuidOffset = i + 12;

                if (uuidOffset + 16 <= mp4.length) {
                    byte[] actualUuid = Arrays.copyOfRange(mp4, uuidOffset, uuidOffset + 16);
                    if (Arrays.equals(actualUuid, C2PA_ISOBMFF_UUID) || Arrays.equals(actualUuid, GOOGLE_C2PA_UUID)) {
                        int payloadStart = uuidOffset + 16;
                        int payloadSize = (int) (totalSize - (payloadStart - boxStart));
                        byte[] rawPayload = new byte[payloadSize];
                        System.arraycopy(mp4, payloadStart, rawPayload, 0, payloadSize);
                        
                        c2paPayload = cleanGoogleHeader(rawPayload);
                        foundOffset = boxStart;
                        break;
                    }
                }
            }
        }

        if (foundOffset != -1) {
            byte[] cleanMp4Array = mp4.clone();
            // Convertimos la caja original a 'free' (padding) para no romper el video
            cleanMp4Array[foundOffset + 4] = 'f';
            cleanMp4Array[foundOffset + 5] = 'r';
            cleanMp4Array[foundOffset + 6] = 'e';
            cleanMp4Array[foundOffset + 7] = 'e';
            return new MediaExtractionResult(cleanMp4Array, c2paPayload);
        }
        return new MediaExtractionResult(mp4, null);
    }

    private byte[] cleanGoogleHeader(byte[] raw) {
        if (raw.length < 25) return raw;
        for (int i = 0; i < Math.min(100, raw.length - 8); i++) {
            if (raw[i+4] == 'j' && raw[i+5] == 'u' && raw[i+6] == 'm' && raw[i+7] == 'b') {
                // FIX: Leer el tamaño EXACTO del bloque JUMBF descartando bytes basura al final
                long jumbSize = MediaFormatUtils.readUint32BE(raw, i);
                if (jumbSize > 0 && i + jumbSize <= raw.length) {
                    byte[] cleaned = new byte[(int) jumbSize];
                    System.arraycopy(raw, i, cleaned, 0, (int) jumbSize);
                    return cleaned;
                }
            }
        }
        return raw;
    }
    
    public byte[] inject(byte[] cleanMedia, byte[] jumbfStore) throws IOException {
        byte[] finalContainer = buildContainer(jumbfStore);
        byte[] result = new byte[cleanMedia.length + finalContainer.length];
        
        System.arraycopy(cleanMedia, 0, result, 0, cleanMedia.length);
        System.arraycopy(finalContainer, 0, result, cleanMedia.length, finalContainer.length);
        
        return result;
    }

    public byte[] buildContainer(byte[] jumbfPayload) {
        byte[] payloadWithHeader = new byte[GOOGLE_MANIFEST_HEADER.length + jumbfPayload.length];
        System.arraycopy(GOOGLE_MANIFEST_HEADER, 0, payloadWithHeader, 0, GOOGLE_MANIFEST_HEADER.length);
        System.arraycopy(jumbfPayload, 0, payloadWithHeader, GOOGLE_MANIFEST_HEADER.length, jumbfPayload.length);

        int totalSize = 8 + 16 + payloadWithHeader.length;
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(totalSize);
        buf.put(BOX_UUID); 
        buf.put(GOOGLE_C2PA_UUID); 
        buf.put(payloadWithHeader);
        return buf.array();
    }

    private static byte[] hexToBytes(String hex) {
        byte[] data = new byte[hex.length() / 2];
        for (int i = 0; i < data.length; i++) data[i] = (byte)((Character.digit(hex.charAt(i*2), 16) << 4) + Character.digit(hex.charAt(i*2+1), 16));
        return data;
    }
}