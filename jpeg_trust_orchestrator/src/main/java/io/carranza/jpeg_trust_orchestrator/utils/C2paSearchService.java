package io.carranza.jpeg_trust_orchestrator.utils;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper;
import org.mipams.jpegtrust.entities.assertions.ingredients.ValidationResultsMap;
import org.mipams.jpegtrust.entities.assertions.ingredients.StatusCodesMap;
import org.mipams.jpegtrust.entities.assertions.ingredients.StatusMap;
import io.carranza.jpeg_trust_orchestrator.dto.ManifestInfo;
import org.mipams.jpegtrust.services.JumbfBoxDigestService;
import org.mipams.jumbf.entities.BmffBox;
import org.mipams.jumbf.entities.JsonBox;
import org.mipams.jumbf.entities.JumbfBox;
import org.mipams.jumbf.entities.CborBox;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.mipams.jpegtrust.entities.HashedUriReference;
import org.mipams.jpegtrust.entities.assertions.Assertion;
import org.mipams.jpegtrust.entities.assertions.actions.ActionAssertion;
import org.mipams.jpegtrust.entities.assertions.enums.ActionChoice;
import org.mipams.jpegtrust.entities.assertions.BindingAssertion;
import org.mipams.jpegtrust.entities.validation.ValidationCode;
import org.mipams.jpegtrust.entities.assertions.tfm.EmbeddedDataAssertion;
import org.mipams.jpegtrust.entities.assertions.ThumbnailAssertion;
import org.mipams.jpegtrust.entities.assertions.enums.AssetTypeChoice;
import org.mipams.jpegtrust.entities.assertions.AssetType;
import org.mipams.jpegtrust.entities.assertions.ingredients.IngredientAssertion;
import org.mipams.jpegtrust.entities.assertions.ingredients.IngredientAssertionV1;
import org.mipams.jpegtrust.entities.DigestResultForJumbfBox;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.util.Base64;
import io.carranza.jpeg_trust_orchestrator.dto.AiGenerationRecipe;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class C2paSearchService {

    private final JumbfBoxDigestService jumbfBoxDigestService;
    private final ObjectMapper objectMapper;

    public C2paSearchService(JumbfBoxDigestService jumbfBoxDigestService, ObjectMapper objectMapper) {
        this.jumbfBoxDigestService = jumbfBoxDigestService;
        this.objectMapper = objectMapper;
    }

public void searchC2paBoxes(JumbfBox box, String relativePath, ManifestInfo manifestInfo) throws Exception {
    if (box == null || box.getDescriptionBox() == null) return;
    
    String label = box.getDescriptionBox().getLabel();
    String currentPath = (label != null && label.equals("c2pa")) ? "" : (relativePath.isEmpty() ? label : relativePath + "/" + label);

    System.out.println("[DEBUG] Analizando caja: " + label + " en el path: " + currentPath);

    if (label != null && (label.startsWith("urn:c2pa:") || label.startsWith("urn:uuid:"))) {
        manifestInfo.getRawJumbfBoxes().add(box);
        manifestInfo.setActiveManifestUrl(label); 
        manifestInfo.setActiveManifestDigest(jumbfBoxDigestService.calculateDigestForJumbfBox(box).getDigest());
    } else if (label != null && label.contains("c2pa.signature")) {
        manifestInfo.setClaimSignatureUrl(currentPath);
        manifestInfo.setClaimSignatureDigest(jumbfBoxDigestService.calculateDigestForJumbfBox(box).getDigest());
    } 
    else if (label != null && label.contains("c2pa.ingredient")) {
        System.out.println("[DEBUG] -> ¡Caja de ingrediente detectada! Intentando extraer JSON...");
        
        Map<String, Object> ingredientData = extractValidationResults(box);
        System.out.println("[DEBUG] -> Llaves encontradas en el JSON del ingrediente: " + ingredientData.keySet());

        if (ingredientData.containsKey("validationResults")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> validationResults = (Map<String, Object>) ingredientData.get("validationResults");
            System.out.println("[DEBUG] -> 'validationResults' encontrado. Contenido: " + validationResults);

            ValidationResultsMap resultsMap = new ValidationResultsMap();
            StatusCodesMap activeManifest = new StatusCodesMap();

            @SuppressWarnings("unchecked")
            Map<String, Object> activeManifestMap = (Map<String, Object>) validationResults.get("activeManifest");

            if (activeManifestMap != null) {
                System.out.println("[DEBUG] -> 'activeManifest' detectado. Procesando listas de éxito/error...");
                activeManifest.setSuccess(parseStatusList((List<Map<String, Object>>) activeManifestMap.get("success"), true));
                activeManifest.setInformational(parseStatusList((List<Map<String, Object>>) activeManifestMap.get("informational"), true));
                activeManifest.setFailure(parseStatusList((List<Map<String, Object>>) activeManifestMap.get("failure"), false));
                System.out.println("[DEBUG] -> Éxitos procesados: " + activeManifest.getSuccess().size());
            } else {
                StatusMap status1 = new StatusMap();
                status1.setCode(ValidationCode.CLAIM_SIGNATURE_VALIDATED.getCode());
                StatusMap status2 = new StatusMap();
                status2.setCode(ValidationCode.ASSERTION_DATA_HASH_MATCH.getCode());
                StatusCodesMap statusCodeMap = new StatusCodesMap();
                statusCodeMap.setSuccess(List.of(status1, status2));
                resultsMap.setActiveManifest(statusCodeMap);
                System.out.println("[DEBUG] -> ERROR: No se encontró 'activeManifest' dentro de 'validationResults'");
            }

            resultsMap.setActiveManifest(activeManifest);
            resultsMap.setIngredientDeltas(new ArrayList<>());
            manifestInfo.setValidationResultsMap(resultsMap);
        } else {
            System.out.println("[DEBUG] -> ERROR: El ingrediente no contiene la llave 'validationResults'");
        }
    }
    if (box.getContentBoxList() != null) {
        for (BmffBox contentBox : box.getContentBoxList()) {
            if (contentBox instanceof JumbfBox) {
                searchC2paBoxes((JumbfBox) contentBox, currentPath, manifestInfo);
            }
        }
    }
}

private Map<String, Object> extractValidationResults(JumbfBox box) throws Exception {
    if (box.getContentBoxList() == null) return new HashMap<>();

    for (BmffBox contentBox : box.getContentBoxList()) {
        System.out.println("[DEBUG] -> Analizando hijo de tipo: " + contentBox.getClass().getSimpleName());

        if (contentBox.getClass().getSimpleName().equals("CborBox") || contentBox.getType().equals("cbor")) {
            System.out.println("[DEBUG] -> ¡Caja CBOR detectada! Usando CBORMapper...");
            
            byte[] cborData = null;
            
            if (contentBox instanceof CborBox cborBox) {
                cborData = cborBox.getContent();
            } else {
                try {
                    cborData = (byte[]) contentBox.getClass().getMethod("getContent").invoke(contentBox);
                } catch (Exception e) {
                    System.err.println("[DEBUG] -> No se pudo extraer el contenido de la CborBox");
                }
            }

            if (cborData != null) {
                CBORMapper cborMapper = new CBORMapper();
                Map<String, Object> data = cborMapper.readValue(cborData, Map.class);
                System.out.println("[DEBUG] -> CBOR decodificado. Llaves: " + data.keySet());
                return data;
            }
        }

        if (contentBox instanceof JsonBox jsonBox) {
            String json = new String(jsonBox.getContent(), StandardCharsets.UTF_8);
            return objectMapper.readValue(json, Map.class);
        }

        if (contentBox instanceof JumbfBox nestedBox) {
            Map<String, Object> result = extractValidationResults(nestedBox);
            if (!result.isEmpty()) return result;
        }
    }
    return new HashMap<>();
}
    private List<StatusMap> parseStatusList(List<Map<String, Object>> list, boolean successValue) {
        List<StatusMap> result = new ArrayList<>();
        if (list == null) {
            return result;
        }

        for (Map<String, Object> item : list) {
            StatusMap status = new StatusMap();
            status.setCode((String) item.get("code"));
            status.setUrl((String) item.get("url"));
            status.setExplanation((String) item.get("explanation"));
            status.setSuccess(successValue);
            result.add(status);
        }
        return result;
    }

    public String getBareLabel(JumbfBox box) {
        String label = box.getDescriptionBox().getLabel();
        if (label != null && label.startsWith("c2pa.assertions/")) {
            label = label.substring("c2pa.assertions/".length());
        }
        return label;
    }

    private void ensureUniqueLabel(Assertion newAssertion, List<Assertion> existingAssertions) {
        Set<String> usedLabels = existingAssertions.stream().map(Assertion::getLabel).collect(Collectors.toSet());
        if (usedLabels.contains(newAssertion.getLabel())) {
            String base = newAssertion.getDefaultLabel();
            int c = 1;
            while (usedLabels.contains(base + "__" + c)) c++;
            newAssertion.setLabel(base + "__" + c);
        }
    }

    public void addExistingAssertionUnique(Assertion assertion, List<Assertion> existingAssertions) {
        ensureUniqueLabel(assertion, existingAssertions);
        existingAssertions.add(assertion);
    }

    public IngredientAssertion addPromptAssertions(List<Assertion> existingAssertions, String promptText) throws Exception {
        EmbeddedDataAssertion promptData = new EmbeddedDataAssertion();
        promptData.setMediaType("text/plain");
        promptData.setData(promptText.getBytes(StandardCharsets.UTF_8));

        addExistingAssertionUnique(promptData, existingAssertions);

        JumbfBox data = promptData.toJumbfBox();
        DigestResultForJumbfBox dataDigest = jumbfBoxDigestService.calculateDigestForJumbfBox(data);

        IngredientAssertion promptIngredient = new IngredientAssertion();
        promptIngredient.setTitle("Generation Prompt");
        promptIngredient.setMediaType("text/plain");
        promptIngredient.setRelationship(IngredientAssertionV1.RELATIONSHIP_INPUT_OF);
        promptIngredient.setDescription("Text prompt provided to the model for this generation");
        promptIngredient.setData(addEmbededData(promptData, dataDigest));

        AssetType assetType = new AssetType();
        assetType.setType("c2pa.types.generator.prompt");
        promptIngredient.setDataTypes(List.of(assetType));

        addExistingAssertionUnique(promptIngredient, existingAssertions);

        return promptIngredient;
    }

    public HashedUriReference addEmbededData(EmbeddedDataAssertion embeddedData, DigestResultForJumbfBox data) throws Exception {
        HashedUriReference ref = new HashedUriReference();
        ref.setAlgorithm(data.getAlgorithm());
        ref.setUrl("self#jumbf=c2pa.assertions/" + getBareLabel(embeddedData.toJumbfBox()));
        ref.setDigest(data.getDigest());
        return ref;
    }
    
    public HashedUriReference addThumbnailData(ThumbnailAssertion thumbnailData, DigestResultForJumbfBox data) throws Exception {
        HashedUriReference ref = new HashedUriReference();
        ref.setAlgorithm(data.getAlgorithm());
        ref.setUrl("self#jumbf=c2pa.assertions/" + getBareLabel(thumbnailData.toJumbfBox()));
        ref.setDigest(data.getDigest());
        return ref;
    }
    
    public String detectMediaType(byte[] bytes, String defaultType) {
        if (bytes == null || bytes.length < 4) return defaultType;
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) return "image/jpeg";
        if ((bytes[0] & 0xFF) == 0x89 && (bytes[1] & 0xFF) == 0x50 && (bytes[2] & 0xFF) == 0x4E && (bytes[3] & 0xFF) == 0x47) return "image/png";
        if ((bytes[0] & 0xFF) == 0x49 && (bytes[1] & 0xFF) == 0x44 && (bytes[2] & 0xFF) == 0x33) return "audio/mpeg";
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xE0) == 0xE0) return "audio/mpeg";
        if (bytes.length >= 12 && (bytes[4] & 0xFF) == 0x66 && (bytes[5] & 0xFF) == 0x74 && (bytes[6] & 0xFF) == 0x79 && (bytes[7] & 0xFF) == 0x70) return "video/mp4";
        return defaultType;
    }

    public void addTranscodeAssertion(List<ActionAssertion> existingAssertions, String originalFormat, String newFormat) {
        ActionAssertion actionTranscoded = new ActionAssertion();
        actionTranscoded.setAction(ActionChoice.C2PA_TRANSCODED.getValue());
        actionTranscoded.setWhen(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));


        Map<String, String> agentTranscoded = new HashMap<>();
        agentTranscoded.put("name", "JPEG Trust Orchestrator v1.0");
        actionTranscoded.setSoftwareAgent(agentTranscoded);
        
        actionTranscoded.setDescription("Transcoded from " + originalFormat + " to " + newFormat);
        existingAssertions.add(actionTranscoded);
    }
    
    public IngredientAssertion addOriginalValidationResults(ManifestInfo originalProvenance, List<Assertion> existingAssertions, String mediaType) throws Exception {
        IngredientAssertion ingredient = new IngredientAssertion();
        ingredient.setTitle("Reference Image");
        
        ingredient.setMediaType(mediaType);
        ingredient.setDescription("Reference image used to guide the diffusion model's visual style");
        ingredient.setRelationship("parentOf");
        ingredient.setInstanceId("uuid:" + java.util.UUID.randomUUID().toString());
        ThumbnailAssertion thumbnailData = new ThumbnailAssertion();
        thumbnailData.setMediaType(MediaType.valueOf(mediaType));
        byte[] thumbnailBytes = generateThumbnailBytes(originalProvenance.getOriginalImageBytes(), mediaType);
        thumbnailData.setData(thumbnailBytes);
        thumbnailData.setIsClaimIngredientThumbnail();
        addExistingAssertionUnique(thumbnailData, existingAssertions);
        DigestResultForJumbfBox dataDigest = jumbfBoxDigestService.calculateDigestForJumbfBox(thumbnailData.toJumbfBox());
        ingredient.setThumbnail(addThumbnailData(thumbnailData, dataDigest));
        HashedUriReference manifestRef = new HashedUriReference();
        manifestRef.setAlgorithm("sha256");
        manifestRef.setUrl(originalProvenance.getActiveManifestUrl());
        manifestRef.setDigest(originalProvenance.getActiveManifestDigest());
        ingredient.setActiveManifestOfIngredient(manifestRef);

        if (originalProvenance.getClaimSignatureUrl() != null) {
            HashedUriReference sigRef = new HashedUriReference();
            sigRef.setAlgorithm("sha256");
            sigRef.setUrl(originalProvenance.getClaimSignatureUrl());
            sigRef.setDigest(originalProvenance.getClaimSignatureDigest());
            ingredient.setClaimSignatureOfIngredient(sigRef);
        }

        ValidationResultsMap vrm = originalProvenance.getValidationResultsMap();
        if (vrm == null) {
            vrm = buildMinimalValidationResultsMap();
        } else if (vrm.getActiveManifest() == null) {
            vrm.setActiveManifest(buildMinimalStatusCodesMap());
        }
        ingredient.setValidationResults(vrm);

        addExistingAssertionUnique(ingredient, existingAssertions);
        return ingredient;
    }

    private ValidationResultsMap buildMinimalValidationResultsMap() {
        ValidationResultsMap vrm = new ValidationResultsMap();
        vrm.setActiveManifest(buildMinimalStatusCodesMap());
        vrm.setIngredientDeltas(new ArrayList<>());
        return vrm;
    }

    private StatusCodesMap buildMinimalStatusCodesMap() {
        StatusCodesMap map = new StatusCodesMap();

        StatusMap infoEntry = new StatusMap();
        infoEntry.setCode("c2pa.status.informational");
        infoEntry.setExplanation("Original provenance preserved; not re-validated by this service.");
        infoEntry.setSuccess(true);

        map.setInformational(List.of(infoEntry));
        map.setSuccess(new ArrayList<>());
        map.setFailure(new ArrayList<>());
        return map;
    }
    
    public byte[] getReferenceImageBytes(AiGenerationRecipe recipe) {
        String rawBase64 = recipe.getReferenceImageBase64();
        String mediaType = "image/jpeg"; 
        if (rawBase64 != null && rawBase64.contains(",")) {
            String prefix = rawBase64.substring(0, rawBase64.indexOf(','));
            if (prefix.startsWith("data:") && prefix.contains(";")) {
                mediaType = prefix.substring("data:".length(), prefix.indexOf(';'));
            }
            rawBase64 = rawBase64.split(",", 2)[1];
        }

        byte[] referenceImageBytes = Base64.getDecoder().decode(rawBase64);
        return referenceImageBytes;
    }
    
    private byte[] generateThumbnailBytes(byte[] originalBytes, String mediaType) {
        if (originalBytes == null || originalBytes.length == 0) {
            return originalBytes;
        }
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(originalBytes)) {
            BufferedImage originalImage = ImageIO.read(bais);
            
            if (originalImage == null) {
                return originalBytes; 
            }

            int maxWidth = 256;
            int maxHeight = 256;
            int width = originalImage.getWidth();
            int height = originalImage.getHeight();

            if (width <= maxWidth && height <= maxHeight) {
                return originalBytes; 
            }

            double ratio = Math.min((double) maxWidth / width, (double) maxHeight / height);
            int newWidth = (int) (width * ratio);
            int newHeight = (int) (height * ratio);

            Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, Image.SCALE_SMOOTH);
            
            int imageType = (mediaType != null && mediaType.contains("png")) ? 
                            BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
                            
            BufferedImage thumbnail = new BufferedImage(newWidth, newHeight, imageType);

            Graphics2D g2d = thumbnail.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                String format = "jpeg"; 
                if (mediaType != null && mediaType.contains("/")) {
                    format = mediaType.split("/")[1]; 
                    if (format.equalsIgnoreCase("jpg")) format = "jpeg";
                }
                ImageIO.write(thumbnail, format, baos);
                return baos.toByteArray();
            }
        } catch (Exception e) {
            System.err.println("[DEBUG] -> Error generando el thumbnail, usando imagen original de respaldo: " + e.getMessage());
            return originalBytes;
        }
    }
}