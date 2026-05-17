package io.carranza.jpeg_trust_orchestrator.service;

import io.carranza.jpeg_trust_orchestrator.dto.AiGenerationRecipe;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.*;

@Service
public class AiGenerationService {

    private static final String FOOOCUS_BASE = "http://127.0.0.1:8888";

    private final WebClient webClient;

    public AiGenerationService(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .codecs(configurer -> configurer
                .defaultCodecs()
                .maxInMemorySize(10 * 1024 * 1024))
            .baseUrl(FOOOCUS_BASE)
            .build();
    }

    public byte[] generateImageFromFooocus(AiGenerationRecipe recipe) {
        recipe.setGuidanceScale(4.0);
        recipe.setSharpness(2.0);
        recipe.setAspectRatiosSelection("1024*1024");  
        recipe.setPerformanceSelection("Speed");
        if ("PROMPT".equals(recipe.getMode())) {
            return generateImageFromFooocusWithPrompt(recipe);
        } else if ("PROMPT_IMAGE".equals(recipe.getMode())) {
            return generateImageFromFooocusWithImage(recipe);
        } else {
            throw new IllegalArgumentException("Unsupported mode: " + recipe.getMode());
        }
    }
public byte[] generateImageFromFooocusWithImage(AiGenerationRecipe recipe) {
        String prompt = recipe.getPrompt();
        // Strip "data:image/...;base64," prefix if present — Fooocus expects raw base64
        String rawBase64 = recipe.getReferenceImageBase64();
        if (rawBase64 != null && rawBase64.contains(",")) {
            rawBase64 = rawBase64.split(",", 2)[1];
        }

        // 2. Configurar el ImagePrompt según la documentación de Fooocus
        Map<String, Object> imagePromptConfig = new HashMap<>();
        imagePromptConfig.put("cn_img", rawBase64); // input image en base64 str[cite: 10]
        imagePromptConfig.put("cn_stop", 0.6);        // 0-1, default to 0.5[cite: 10]
        imagePromptConfig.put("cn_weight", 0.6);      // weight, 0-2, default to 1.0[cite: 10]
        imagePromptConfig.put("cn_type", "ImagePrompt"); // Debe ser uno de "ImagePrompt", "FaceSwap", etc.[cite: 10]

        // 3. Crear job (ASYNC)
        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", prompt);
        payload.put("async_process", true);
        payload.put("image_prompts", List.of(imagePromptConfig)); // Añadimos la lista de imágenes[cite: 10]
            
        // Mantenemos los parámetros extra de generación (Fooocus suele heredarlos en sus endpoints)
        payload.put("image_number", 1);
        payload.put("guidance_scale", recipe.getGuidanceScale());
        payload.put("sharpness", recipe.getSharpness());
        payload.put("aspect_ratios_selection", recipe.getAspectRatiosSelection());
        payload.put("performance_selection", recipe.getPerformanceSelection());

        Map response = webClient.post()
                .uri("/v2/generation/image-prompt") 
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String jobId = (String) response.get("job_id");
        return processFooocusResult(jobId, recipe);
    }
    public byte[] generateImageFromFooocusWithPrompt(AiGenerationRecipe recipe) {

        // 1. Crear job (ASYNC)
        Map<String, Object> payload = new HashMap<>();
        payload.put("prompt", recipe.getPrompt());
        payload.put("async_process", true);
        payload.put("image_number", 1);
        payload.put("guidance_scale", recipe.getGuidanceScale());
        payload.put("sharpness", recipe.getSharpness());
        payload.put("aspect_ratios_selection", recipe.getAspectRatiosSelection());
        payload.put("performance_selection", recipe.getPerformanceSelection());

        Map response = webClient.post()
                .uri("/v1/generation/text-to-image")
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        String jobId = (String) response.get("job_id");
        return processFooocusResult(jobId, recipe);
    }
    private byte[] processFooocusResult(String jobId, AiGenerationRecipe recipe) {
        Map jobResult = waitForJob(jobId);

        List<Map<String, Object>> results = (List<Map<String, Object>>) jobResult.get("job_result");
        Map<String, Object> firstImageResult = results.get(0);

        if (firstImageResult.containsKey("seed")) {
            // Lo convertimos de forma segura a Long (Jackson a veces lo parsea como String o Integer)
            String seedStr = String.valueOf(firstImageResult.get("seed"));
            recipe.setSeed(Long.parseLong(seedStr));
            System.out.println("[AiGenerationService] Real Seed used by Fooocus: " + recipe.getSeed());
        }

        if (recipe.getNumInferenceSteps() == null) {
            if ("Quality".equalsIgnoreCase(recipe.getPerformanceSelection())) {
                recipe.setNumInferenceSteps(60);
            } else if ("Extreme Speed".equalsIgnoreCase(recipe.getPerformanceSelection())) {
                recipe.setNumInferenceSteps(8);
            } else {
                recipe.setNumInferenceSteps(30); 
            }
        }

        String imageUrl = (String) firstImageResult.get("url");
        System.out.println("[AiGenerationService] Image URL from Fooocus: " + imageUrl);
        byte[] imageBytes;
        if (imageUrl.startsWith("http://") || imageUrl.startsWith("https://")) {
            imageBytes = webClient.get().uri(imageUrl).retrieve().bodyToMono(byte[].class).block();
        } else {
            String fullUrl = FOOOCUS_BASE + (imageUrl.startsWith("/") ? imageUrl : "/" + imageUrl);
            imageBytes = webClient.get().uri(fullUrl).retrieve().bodyToMono(byte[].class).block();
        }

        if (imageBytes == null || imageBytes.length < 4) {
            throw new RuntimeException("Empty or null image bytes received from Fooocus");
        }

        boolean isJpeg = (imageBytes[0] & 0xFF) == 0xFF && (imageBytes[1] & 0xFF) == 0xD8;
        if (!isJpeg) {
            System.out.printf("[AiGenerationService] Image is not JPEG (first bytes: %02X %02X), converting...%n",
                    imageBytes[0] & 0xFF, imageBytes[1] & 0xFF);
            imageBytes = convertToJpeg(imageBytes);
        }

        System.out.println("[AiGenerationService] Valid JPEG ready, size: " + imageBytes.length + " bytes");
        return imageBytes;
    }
    private byte[] convertToJpeg(byte[] imageBytes) {
        try {
            BufferedImage bufferedImage = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (bufferedImage == null) {
                throw new RuntimeException("Could not decode image — unsupported format");
            }

            // PNG may have alpha channel — convert to RGB before writing as JPEG
            if (bufferedImage.getType() == BufferedImage.TYPE_4BYTE_ABGR
                    || bufferedImage.getType() == BufferedImage.TYPE_INT_ARGB
                    || bufferedImage.getColorModel().hasAlpha()) {
                BufferedImage rgbImage = new BufferedImage(
                        bufferedImage.getWidth(),
                        bufferedImage.getHeight(),
                        BufferedImage.TYPE_INT_RGB);
                rgbImage.createGraphics().drawImage(bufferedImage, 0, 0, java.awt.Color.WHITE, null);
                bufferedImage = rgbImage;
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean written = ImageIO.write(bufferedImage, "jpeg", baos);
            if (!written) {
                throw new RuntimeException("ImageIO could not write JPEG");
            }

            System.out.println("[AiGenerationService] PNG→JPEG conversion successful, size: " + baos.size() + " bytes");
            return baos.toByteArray();

        } catch (Exception e) {
            throw new RuntimeException("Failed to convert image to JPEG: " + e.getMessage(), e);
        }
    }

    private Map waitForJob(String jobId) {
        while (true) {
            Map status = webClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/generation/query-job")
                            .queryParam("job_id", jobId)
                            .build())
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            String stage = (String) status.get("job_stage");
            System.out.println("[AiGenerationService] Job " + jobId + " stage: " + stage);

            if ("SUCCESS".equals(stage)) {
                return status;
            }

            if ("FAILED".equals(stage)) {
                throw new RuntimeException("Fooocus job failed: " + jobId);
            }

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}