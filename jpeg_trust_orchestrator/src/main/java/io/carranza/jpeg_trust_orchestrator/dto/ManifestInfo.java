package io.carranza.jpeg_trust_orchestrator.dto;
import org.mipams.jpegtrust.entities.assertions.ingredients.ValidationResultsMap;
import org.mipams.jumbf.entities.JumbfBox;
import java.util.ArrayList;
import java.util.List;

public class ManifestInfo {
    private String activeManifestUrl;
    private byte[] activeManifestDigest;
    private String claimSignatureUrl;
    private byte[] claimSignatureDigest;
    private List<JumbfBox> rawJumbfBoxes = new ArrayList<>();
    private ValidationResultsMap validationResultsMap;
    private byte[] originalImageBytes;

    public String getActiveManifestUrl() { return activeManifestUrl; }
    public void setActiveManifestUrl(String activeManifestUrl) { this.activeManifestUrl = activeManifestUrl; }

    public byte[] getActiveManifestDigest() { return activeManifestDigest; }
    public void setActiveManifestDigest(byte[] activeManifestDigest) { this.activeManifestDigest = activeManifestDigest; }

    public String getClaimSignatureUrl() { return claimSignatureUrl; }
    public void setClaimSignatureUrl(String claimSignatureUrl) { this.claimSignatureUrl = claimSignatureUrl; }

    public byte[] getClaimSignatureDigest() { return claimSignatureDigest; }
    public void setClaimSignatureDigest(byte[] claimSignatureDigest) { this.claimSignatureDigest = claimSignatureDigest; }

    public List<JumbfBox> getRawJumbfBoxes() { return rawJumbfBoxes; }
    public void setRawJumbfBoxes(List<JumbfBox> rawJumbfBoxes) { this.rawJumbfBoxes = rawJumbfBoxes; }

    public ValidationResultsMap getValidationResultsMap() { return validationResultsMap; }
    public void setValidationResultsMap(ValidationResultsMap validationResultsMap) { 
        this.validationResultsMap = validationResultsMap; }

    public byte[] getOriginalImageBytes() { return originalImageBytes; }
    public void setOriginalImageBytes(byte[] originalImageBytes) { this.originalImageBytes = originalImageBytes; }
}