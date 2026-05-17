package io.carranza.jpeg_trust_orchestrator.handler;

import io.carranza.jpeg_trust_orchestrator.dto.MediaExtractionResult;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
public class Mp3LabellingHandler {

    public boolean isMp3(byte[] bytes) {
        if (bytes == null || bytes.length < 3) return false;
        return (bytes[0] == 'I' && bytes[1] == 'D' && bytes[2] == '3') || ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0);
    }

    public MediaExtractionResult extract(byte[] mp3) {
        byte[] c2paPayload = null;
        byte[] cleanMp3 = mp3;

        if (mp3.length >= 10 && mp3[0] == 'I' && mp3[1] == 'D' && mp3[2] == '3') {
            int id3Size = ((mp3[6] & 0x7F) << 21) | ((mp3[7] & 0x7F) << 14) | ((mp3[8] & 0x7F) << 7) | (mp3[9] & 0x7F);
            int totalId3Length = 10 + id3Size;

            byte[] mime1 = "application/c2pa".getBytes(StandardCharsets.US_ASCII);
            byte[] mime2 = "application/x-c2pa-manifest-store".getBytes(StandardCharsets.US_ASCII);
            
            int matchIndex = indexOf(mp3, mime1);
            if (matchIndex == -1) matchIndex = indexOf(mp3, mime2);
            
            if (matchIndex != -1 && matchIndex < totalId3Length) {
                for (int i = matchIndex; i < Math.min(matchIndex + 100, totalId3Length - 4); i++) {
                    if (mp3[i] == 'j' && mp3[i+1] == 'u' && mp3[i+2] == 'm' && mp3[i+3] == 'b') {
                        long boxSize = readUint32BE(mp3, i - 4);
                        if (boxSize > 0 && i - 4 + boxSize <= totalId3Length) {
                            c2paPayload = new byte[(int)boxSize];
                            System.arraycopy(mp3, i - 4, c2paPayload, 0, (int)boxSize);
                            break;
                        }
                    }
                }
            }

            if (totalId3Length < mp3.length) {
                cleanMp3 = new byte[mp3.length - totalId3Length];
                System.arraycopy(mp3, totalId3Length, cleanMp3, 0, cleanMp3.length);
            }
        }
        
        return new MediaExtractionResult(cleanMp3, c2paPayload); 
    }

    public byte[] inject(byte[] cleanMedia, byte[] jumbfStore) throws IOException {
        byte[] id3Tag = buildContainer(jumbfStore);
        ByteArrayOutputStream out = new ByteArrayOutputStream(cleanMedia.length + id3Tag.length);
        out.write(id3Tag); 
        out.write(cleanMedia);
        return out.toByteArray();
    }

    public byte[] buildContainer(byte[] jumbfStore) {
        byte[] mime = "application/c2pa".getBytes(StandardCharsets.US_ASCII);
        byte[] filename = "c2pa".getBytes(StandardCharsets.US_ASCII);
        byte[] desc = "c2pa manifest store".getBytes(StandardCharsets.US_ASCII);

        int framePayloadLen = 1 + mime.length + 1 + filename.length + 1 + desc.length + 1 + jumbfStore.length;
        byte[] frame = new byte[10 + framePayloadLen];
        frame[0] = 'G'; frame[1] = 'E'; frame[2] = 'O'; frame[3] = 'B';
        
        frame[4] = (byte)((framePayloadLen >> 24) & 0xFF);
        frame[5] = (byte)((framePayloadLen >> 16) & 0xFF);
        frame[6] = (byte)((framePayloadLen >> 8) & 0xFF);
        frame[7] = (byte)(framePayloadLen & 0xFF);
        frame[8] = 0; frame[9] = 0; 
        
        int offset = 10;
        frame[offset++] = 0; 
        System.arraycopy(mime, 0, frame, offset, mime.length); offset += mime.length; frame[offset++] = 0;
        System.arraycopy(filename, 0, frame, offset, filename.length); offset += filename.length; frame[offset++] = 0;
        System.arraycopy(desc, 0, frame, offset, desc.length); offset += desc.length; frame[offset++] = 0;
        System.arraycopy(jumbfStore, 0, frame, offset, jumbfStore.length);
        
        int id3Size = frame.length;
        byte[] header = new byte[10];
        header[0] = 'I'; header[1] = 'D'; header[2] = '3';
        header[3] = 3; header[4] = 0; header[5] = 0;
        header[6] = (byte)((id3Size >> 21) & 0x7F);
        header[7] = (byte)((id3Size >> 14) & 0x7F);
        header[8] = (byte)((id3Size >> 7) & 0x7F);
        header[9] = (byte)(id3Size & 0x7F);
        
        byte[] r = new byte[header.length + frame.length];
        System.arraycopy(header, 0, r, 0, header.length);
        System.arraycopy(frame, 0, r, header.length, frame.length);
        return r;
    }

    private int indexOf(byte[] data, byte[] pattern) {
        for (int i = 0; i <= data.length - pattern.length; i++) {
            boolean found = true;
            for (int j = 0; j < pattern.length; j++) {
                if (data[i+j] != pattern[j]) { found = false; break; }
            }
            if (found) return i;
        }
        return -1;
    }

    private static long readUint32BE(byte[] buf, int off) {
        return ((buf[off] & 0xFFL) << 24) | ((buf[off+1] & 0xFFL) << 16) | ((buf[off+2] & 0xFFL) << 8) | (buf[off+3] & 0xFFL);
    }
}