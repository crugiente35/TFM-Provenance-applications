package io.carranza.jpeg_trust_orchestrator.dto;

public class MediaExtractionResult {
    public final byte[] cleanMedia;
    public final byte[] originalC2paPayload;

    public MediaExtractionResult(byte[] cleanMedia, byte[] originalC2paPayload) {
        this.cleanMedia = cleanMedia;
        this.originalC2paPayload = originalC2paPayload;
    }
}