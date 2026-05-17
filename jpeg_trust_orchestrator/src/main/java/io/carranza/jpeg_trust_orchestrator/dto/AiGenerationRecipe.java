package io.carranza.jpeg_trust_orchestrator.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.mipams.jumbf.entities.JumbfBox;

public class AiGenerationRecipe {
    private String mode; // "PROMPT", "PROMPT_IMAGE", "IMAGE"
    private String prompt;
    private String referenceImageBase64;
    private byte[] generatedImageBytes;
    private List<JumbfBox> referenceTrustRecords;

    public List<JumbfBox> getReferenceTrustRecords() {
        return referenceTrustRecords;
    }

    public void setReferenceTrustRecords(List<JumbfBox> referenceTrustRecords) {
        this.referenceTrustRecords = referenceTrustRecords;
    }
    

    // --- Parámetros de Inferencia de Fooocus (Valores solicitados) ---
    private Long seed = 2847391052L;
    
    @JsonProperty("guidance_scale")
    private Double guidanceScale = 7.5;
    
    @JsonProperty("num_inference_steps")
    private Integer numInferenceSteps = 30;
    
    // Parámetros base de Fooocus que conviene mantener
    private Double sharpness = 2.0;
    private String aspectRatiosSelection = "1024*1024";
    private String performanceSelection = "Speed";

    public AiGenerationRecipe() {}

    // --- Getters y Setters base ---
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    
    public String getReferenceImageBase64() { return referenceImageBase64; }
    public void setReferenceImageBase64(String referenceImageBase64) { this.referenceImageBase64 = referenceImageBase64; }

    public byte[] getGeneratedImageBytes() { return generatedImageBytes; }
    public void setGeneratedImageBytes(byte[] generatedImageBytes) { this.generatedImageBytes = generatedImageBytes; }

    // --- Getters y Setters de los parámetros de inferencia ---
    public Long getSeed() { return seed; }
    public void setSeed(Long seed) { this.seed = seed; }

    public Double getGuidanceScale() { return guidanceScale; }
    public void setGuidanceScale(Double guidanceScale) { this.guidanceScale = guidanceScale; }

    public Integer getNumInferenceSteps() { return numInferenceSteps; }
    public void setNumInferenceSteps(Integer numInferenceSteps) { this.numInferenceSteps = numInferenceSteps; }

    public Double getSharpness() { return sharpness; }
    public void setSharpness(Double sharpness) { this.sharpness = sharpness; }

    public String getAspectRatiosSelection() { return aspectRatiosSelection; }
    public void setAspectRatiosSelection(String aspectRatiosSelection) { this.aspectRatiosSelection = aspectRatiosSelection; }

    public String getPerformanceSelection() { return performanceSelection; }
    public void setPerformanceSelection(String performanceSelection) { this.performanceSelection = performanceSelection; }
}