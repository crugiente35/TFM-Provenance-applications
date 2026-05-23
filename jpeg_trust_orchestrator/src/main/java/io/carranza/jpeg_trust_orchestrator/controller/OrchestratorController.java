package io.carranza.jpeg_trust_orchestrator.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carranza.jpeg_trust_orchestrator.dto.AiGenerationRecipe;
import io.carranza.jpeg_trust_orchestrator.service.AiGenerationService;
import io.carranza.jpeg_trust_orchestrator.service.JpegTrustAiLabellingService;
import io.carranza.jpeg_trust_orchestrator.service.ProvenanceInspectionService;
import io.carranza.jpeg_trust_orchestrator.service.UploadedImageLabellingService;
import io.carranza.jpeg_trust_orchestrator.service.VideoLabellingService;

import org.mipams.jpegtrust.services.validation.consumer.ManifestStoreConsumer;
import org.mipams.jumbf.services.JpegCodestreamGenerator;
import org.mipams.jumbf.services.JpegCodestreamParser;
import org.mipams.jpegtrust.entities.validation.trustindicators.TrustIndicatorSet;
import org.mipams.jumbf.entities.JumbfBox;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin(origins = "*")
public class OrchestratorController {

    @Autowired
    JpegCodestreamGenerator jpegCodestreamGenerator;

    @Autowired
    JpegCodestreamParser jpegCodestreamParser;

    @Autowired
    ManifestStoreConsumer manifestStoreConsumer;

    private final AiGenerationService aiGenerationService;
    private final JpegTrustAiLabellingService jpegTrustService;
    private final UploadedImageLabellingService uploadedImageLabellingService;
    private final VideoLabellingService videoLabellingService;
    private final ProvenanceInspectionService provenanceInspectionService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String LOG_FILE_PATH = "trust_validation_logs.txt";

    public OrchestratorController(AiGenerationService aiGenerationService,
                                  JpegTrustAiLabellingService jpegTrustService,
                                  UploadedImageLabellingService uploadedImageLabellingService,
                                  VideoLabellingService videoLabellingService,
                                  ProvenanceInspectionService provenanceInspectionService) {
        this.aiGenerationService = aiGenerationService;
        this.jpegTrustService = jpegTrustService;
        this.uploadedImageLabellingService = uploadedImageLabellingService;
        this.videoLabellingService = videoLabellingService;
        this.provenanceInspectionService = provenanceInspectionService;
    }

    @PostMapping(value = "/api/generate", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> generateAndSignImage(@RequestBody AiGenerationRecipe recipe) {
        Path tempAsset = null;
        Path tempTarget = null;
        Map<String, String> response = new HashMap<>();

        try {
            JumbfBox trustRecord;

            if ("IMAGE".equals(recipe.getMode())) {
                String base64Data = recipe.getReferenceImageBase64();
                if (base64Data.contains(",")) base64Data = base64Data.split(",")[1];
                byte[] rawImage = java.util.Base64.getDecoder().decode(base64Data);
                recipe.setGeneratedImageBytes(rawImage);
                trustRecord = uploadedImageLabellingService.labelUploadedAsset(recipe);
            } else {
                byte[] rawImage = aiGenerationService.generateImageFromFooocus(recipe);
                recipe.setGeneratedImageBytes(rawImage);
                trustRecord = jpegTrustService.labelGeneratedAsset(recipe);
            }

            tempAsset = Files.createTempFile("raw_asset_", ".jpg");
            Files.write(tempAsset, recipe.getGeneratedImageBytes());

            tempTarget = Files.createTempFile("signed_target_", ".jpg");
            
            jpegCodestreamGenerator.generateJumbfMetadataToFile(
                    List.of(trustRecord),
                    tempAsset.toString(),
                    tempTarget.toString());

            byte[] signedBytes = Files.readAllBytes(tempTarget);

            
            saveLogToFile("=== NUEVA VALIDACIÓN DE TRUST INDICATORS ===");
            saveLogToFile("Modo: " + recipe.getMode() + " | Prompt/Info: " + recipe.getPrompt());
            saveLogToFile("Trust Record: " + trustRecord.toString());
            try {
                TrustIndicatorSet set = manifestStoreConsumer.validate(trustRecord, tempTarget.toString());
                response.put("validationJson", objectMapper.writeValueAsString(set));
                saveLogToFile("[OK] Validación exitosa. TrustIndicatorSet resultante:");
                saveLogToFile(set.toString()); 
            } catch (Exception valEx) {
                saveLogToFile("[ERROR CRÍTICO] Fallo en la validación de Trust Indicators.");
                saveLogToFile("Motivo: " + valEx.getMessage());
            }
            saveLogToFile("=== FIN DE LA VALIDACIÓN ===\n");

            response.put("finalImage", java.util.Base64.getEncoder().encodeToString(signedBytes));
            response.put("originalImage", java.util.Base64.getEncoder().encodeToString(recipe.getGeneratedImageBytes()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            saveLogToFile("[ERROR DE SERVIDOR] Fallo general en el orquestador: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().build();
        } finally {
            try { if (tempAsset != null) Files.deleteIfExists(tempAsset); } catch (Exception ignored) {}
            try { if (tempTarget != null) Files.deleteIfExists(tempTarget); } catch (Exception ignored) {}
        }
    }

@PostMapping(value = "/api/video/label", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> labelVideoOrAudio(@RequestBody Map<String, String> request) {
        Map<String, String> response = new HashMap<>();
        
        try {
            // 1. Obtener los datos del JSON que envía el frontend
            String base64Data = request.get("videoBase64");
            String prompt = request.get("prompt");
            String orchestratorName = request.get("orchestratorName");

            if (base64Data == null || base64Data.isEmpty()) {
                throw new IllegalArgumentException("No se recibió el video en Base64");
            }

            // 2. Limpiar el prefijo de Base64 si viene incluido desde el frontend
            if (base64Data.contains(",")) {
                base64Data = base64Data.split(",")[1];
            }

            // 3. Decodificar de Base64 a bytes
            byte[] mediaBytes = java.util.Base64.getDecoder().decode(base64Data);

            // 4. Llamar al servicio que hace la inyección C2PA (que ya tienes programado)
            byte[] labelledBytes = videoLabellingService.labelMedia(mediaBytes, prompt, orchestratorName);

            // 5. Volver a codificar a Base64 para enviarlo al frontend
            String labelledBase64 = java.util.Base64.getEncoder().encodeToString(labelledBytes);
            
            // 6. Colocarlo en la respuesta con la llave exacta que espera el frontend
            response.put("labelledVideoBase64", labelledBase64);

            // 7. Intentar parsear y validar el C2PA del video etiquetado para devolver el TrustIndicatorSet
            Path tempVideoPath = null;
            try {
                tempVideoPath = Files.createTempFile("video_inspect_", ".mp4");
                Files.write(tempVideoPath, labelledBytes);
                List<JumbfBox> parsedBoxes = jpegCodestreamParser.parseMetadataFromFile(tempVideoPath.toString());
                if (parsedBoxes != null && !parsedBoxes.isEmpty()) {
                    TrustIndicatorSet set = manifestStoreConsumer.validate(parsedBoxes.get(0), tempVideoPath.toString());
                    response.put("validationJson", objectMapper.writeValueAsString(set));
                    saveLogToFile("[VIDEO] TrustIndicatorSet generado: " + set.toString());
                }
            } catch (Exception valEx) {
                saveLogToFile("[VIDEO WARN] No se pudo generar TrustIndicatorSet para el video: " + valEx.getMessage());
            } finally {
                try { if (tempVideoPath != null) Files.deleteIfExists(tempVideoPath); } catch (Exception ignored) {}
            }

            saveLogToFile("[VIDEO] Etiquetado C2PA exitoso para media con prompt: " + prompt);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            saveLogToFile("[ERROR VIDEO] Fallo al etiquetar el video/audio: " + e.getMessage());
            response.put("error", e.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping(value = "/api/inspect", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> inspectAsset(@RequestParam("file") MultipartFile file) {
        Path tempTarget = null;
        try {
            tempTarget = Files.createTempFile("inspect_target_", ".jpg");
            Files.write(tempTarget, file.getBytes());

            List<JumbfBox> parsedBoxes = jpegCodestreamParser.parseMetadataFromFile(tempTarget.toString());
            
            if (parsedBoxes == null || parsedBoxes.isEmpty()) {
                saveLogToFile("[INSPECT ERROR] No se encontraron cajas JUMBF en la imagen subida.");
                Map<String, Object> response = new HashMap<>();
                response.put("nodes", new ArrayList<>()); 
                return ResponseEntity.ok(response);
            }

            TrustIndicatorSet trustSet = manifestStoreConsumer.validate(parsedBoxes.get(0), tempTarget.toString());
            
            // Llama al nuevo servicio de inspección
            List<Map<String, Object>> nodes = provenanceInspectionService.buildInspectionNodes(trustSet, file.getBytes());

            Map<String, Object> response = new HashMap<>();
            response.put("nodes", nodes);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            saveLogToFile("[INSPECT ERROR] Excepción al inspeccionar: " + e.getMessage());
            return ResponseEntity.internalServerError().build();
        } finally {
            try { if (tempTarget != null) Files.deleteIfExists(tempTarget); } catch (Exception ignored) {}
        }
    }

    private void saveLogToFile(String message) {
        try {
            Path logPath = Path.of(LOG_FILE_PATH);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            String formattedMessage = "[" + timestamp + "] " + message + System.lineSeparator();
            
            Files.writeString(logPath, formattedMessage, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            
            System.out.print(formattedMessage);
        } catch (Exception e) {
            System.err.println("No se pudo escribir en el archivo de log: " + e.getMessage());
        }
    }
}