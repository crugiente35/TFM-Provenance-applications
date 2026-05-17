package io.carranza.jpeg_trust_orchestrator.util;

import java.util.List;
import java.util.Map;

import io.carranza.jpeg_trust_orchestrator.dto.ManifestInfo;

public class MediaFormatUtils {
    
    public static long readUint32BE(byte[] buf, int off) {
        return ((buf[off] & 0xFFL) << 24) | ((buf[off+1] & 0xFFL) << 16) | ((buf[off+2] & 0xFFL) << 8) | (buf[off+3] & 0xFFL);
    }

    public static long readUint64BE(byte[] buf, int off) {
        return ((readUint32BE(buf, off) & 0xFFFFFFFFL) << 32) | (readUint32BE(buf, off + 4) & 0xFFFFFFFFL);
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\","\\\\").replace("\"","\\\"").replace("\n","\\n").replace("\r","\\r").replace("\t","\\t");
    }
    public static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02X", b));
        return sb.toString();
    }
}