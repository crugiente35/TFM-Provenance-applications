package io.carranza.jpeg_trust_orchestrator.service;

import io.carranza.jpeg_trust_orchestrator.dto.AiGenerationRecipe;
import io.carranza.jpeg_trust_orchestrator.security.CryptoSignerService;
import org.mipams.jpegtrust.builders.ManifestBuilder;
import org.mipams.jpegtrust.entities.DigestResultForJumbfBox;
import org.mipams.jpegtrust.entities.JpegTrustUtils;
import io.carranza.jpeg_trust_orchestrator.dto.ManifestInfo; 
import org.mipams.jpegtrust.entities.assertions.Assertion;
import org.mipams.jpegtrust.entities.assertions.BindingAssertion;
import org.mipams.jpegtrust.entities.assertions.actions.ActionAssertion;
import org.mipams.jpegtrust.entities.assertions.actions.ActionsAssertion;
import org.mipams.jpegtrust.entities.assertions.enums.ActionChoice;
import org.mipams.jpegtrust.entities.assertions.ingredients.IngredientAssertion;
import org.mipams.jpegtrust.entities.HashedUriReference;

import org.mipams.jpegtrust.jpeg_systems.content_types.StandardManifestContentType;
import org.mipams.jpegtrust.services.JumbfBoxDigestService;
import org.mipams.jpegtrust.services.validation.discovery.AssertionDiscovery;
import org.mipams.jumbf.entities.JumbfBox;
import org.mipams.jumbf.services.JpegCodestreamParser;
import org.mipams.jpegtrust.jpeg_systems.PngCodestreamParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import io.carranza.jpeg_trust_orchestrator.utils.C2paSearchService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class UploadedImageLabellingService {

    @Autowired
    private JpegCodestreamParser jpegCodestreamParser;

    @Autowired
    private PngCodestreamParser pngCodestreamParser;

    private final CryptoSignerService cryptoSignerService;
    private final JumbfBoxDigestService jumbfBoxDigestService;
    private final C2paSearchService c2paSearchService;

    public UploadedImageLabellingService(CryptoSignerService cryptoSignerService,
                                         JumbfBoxDigestService jumbfBoxDigestService,
                                         C2paSearchService c2paSearchService) {
        this.cryptoSignerService = cryptoSignerService;
        this.jumbfBoxDigestService = jumbfBoxDigestService;
        this.c2paSearchService = c2paSearchService;
    }

public JumbfBox labelUploadedAsset(AiGenerationRecipe recipe) throws Exception {
        byte[] originalBytes = recipe.getGeneratedImageBytes();
        boolean wasTranscoded = !c2paSearchService.detectMediaType(originalBytes, "image/jpeg").equals("image/jpeg");

        // 1. Extract original manifests (JPEG or PNG)
        ManifestInfo originalProvenance = null;
        String detectedType = c2paSearchService.detectMediaType(originalBytes, "image/jpeg");
        if (detectedType.equals("image/jpeg") || detectedType.equals("image/png")) {
            originalProvenance = extractOriginalManifest(originalBytes);
        }

        // 2. Convert to clean JPEG (strip old C2PA APP11 segments to avoid collisions)
        byte[] jpegBytes = ensureCleanJpeg(originalBytes);
        if (jpegBytes == null || jpegBytes.length == 0) {
            throw new Exception("La conversión a JPEG falló o devolvió un archivo vacío.");
        }
        recipe.setGeneratedImageBytes(jpegBytes);

        // Write clean JPEG to disk – this is the file we hash for the binding.
        // It must NOT change after this point.
        Path tempFile = Files.createTempFile("upload_binding_", ".jpg");
        Files.write(tempFile, jpegBytes);

        try {
            // 3. Build builder and add all non-binding assertions
            ManifestBuilder builder = new ManifestBuilder(new StandardManifestContentType());
            builder.setTitle("Authenticated Uploaded Asset");
            builder.setInstanceID("uuid:" + UUID.randomUUID());
            builder.setGeneratorInfoName("JPEG Trust Orchestrator 1.0");
            builder.setAlgorithm("sha256");
            builder.setClaimSignatureCertificates(cryptoSignerService.getCertificates());

            List<JumbfBox> assertionBoxes = createAssertionListForUpload(originalProvenance, wasTranscoded, recipe);
            for (JumbfBox box : assertionBoxes) {
                builder.addCreatedAssertion(box, jumbfBoxDigestService.calculateDigestForJumbfBox(box));
            }

            // 4. PASS 1 – Creación de binding temporal para obtener el tamaño estimado
            final BindingAssertion tempBindingAssertion = new BindingAssertion();
            tempBindingAssertion.setAlgorithm("sha256");
            tempBindingAssertion.addExclusionRange(0, 0);
            byte[] pad = new byte[6];
            Arrays.fill(pad, Byte.parseByte("0"));
            tempBindingAssertion.setPadding(pad);
            
            JumbfBox tempBindingAssertionBox = tempBindingAssertion.toJumbfBox();
            DigestResultForJumbfBox tempResult = jumbfBoxDigestService.calculateDigestForJumbfBox(tempBindingAssertionBox);
            builder.addCreatedAssertion(tempBindingAssertionBox, tempResult);

            // Construir el manifiesto activo temporal (PRIMER BUILD)
            JumbfBox activeManifest = builder.build();

            // Ensamblar temporalmente con los manifiestos de referencia para medir bytes totales
            List<JumbfBox> initialManifests = new ArrayList<>();
            if (originalProvenance != null && originalProvenance.getRawJumbfBoxes() != null) {
                initialManifests.addAll(originalProvenance.getRawJumbfBoxes());
            }
            initialManifests.add(activeManifest);

            JumbfBox tempRecord = JpegTrustUtils.buildTrustRecord(initialManifests.toArray(new JumbfBox[0]));
            long totalBytesRequired = JpegTrustUtils.getSizeOfJumbfInApp11SegmentsInBytes(tempRecord);

            // 5. PASS 2 – Generación del Content Binding real y firma
            BindingAssertion contentBindingAssertion = cryptoSignerService.getBindingAssertionForAsset(
                    tempFile.toString(), totalBytesRequired);
            
            builder.removeCreatedAssertion(AssertionDiscovery.MipamsAssertion.CONTENT_BINDING.getBaseLabel());
            JumbfBox contentBindingAssertionBox = contentBindingAssertion.toJumbfBox();
            DigestResultForJumbfBox contentBindingAssertionResult = jumbfBoxDigestService.calculateDigestForJumbfBox(contentBindingAssertionBox);
            builder.addCreatedAssertion(contentBindingAssertionBox, contentBindingAssertionResult);

            // Firmar el manifiesto
            byte[] claimToBeSigned = builder.encodeClaimToBeSigned();
            byte[] signature = cryptoSignerService.signClaim(claimToBeSigned);
            builder.setClaimSignature(signature);

            // 6. Construir manifiesto final (SEGUNDO BUILD)
            JumbfBox finalActiveManifest = builder.build();
            
            List<JumbfBox> finalManifests = new ArrayList<>();
            if (originalProvenance != null && originalProvenance.getRawJumbfBoxes() != null) {
                finalManifests.addAll(originalProvenance.getRawJumbfBoxes());
            }
            finalManifests.add(finalActiveManifest);

            return JpegTrustUtils.buildTrustRecord(finalManifests.toArray(new JumbfBox[0]));

        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private List<JumbfBox> createAssertionListForUpload(ManifestInfo originalProvenance, boolean wasTranscoded, AiGenerationRecipe recipe) throws Exception {
        List<Assertion> existingAssertions = new ArrayList<>();
        List<HashedUriReference> actionIngredients = new ArrayList<>();

        // 0. ASERCIÓN DE PROMPT (Si se proporcionó)
        if (recipe.getPrompt() != null && !recipe.getPrompt().trim().isEmpty()) {
            IngredientAssertion promptIngredient = c2paSearchService.addPromptAssertions(existingAssertions, recipe.getPrompt());
            HashedUriReference promptRef = new HashedUriReference();
            promptRef.setAlgorithm("sha256");
            promptRef.setUrl("self#jumbf=c2pa.assertions/" + c2paSearchService.getBareLabel(promptIngredient.toJumbfBox()));
            promptRef.setDigest(jumbfBoxDigestService.calculateDigestForJumbfBox(promptIngredient.toJumbfBox()).getDigest());
            actionIngredients.add(promptRef);
        }

        if (originalProvenance != null && originalProvenance.getActiveManifestUrl() != null) {
            byte[] referenceImageBytes = c2paSearchService.getReferenceImageBytes(recipe);
            String mediaType = "image/jpeg";
            mediaType = c2paSearchService.detectMediaType(referenceImageBytes, mediaType);
            IngredientAssertion ingredient =  c2paSearchService.addOriginalValidationResults(originalProvenance,existingAssertions,mediaType);
            HashedUriReference ingRef = new HashedUriReference();
            ingRef.setAlgorithm("sha256");
            ingRef.setUrl("self#jumbf=c2pa.assertions/" + c2paSearchService.getBareLabel(ingredient.toJumbfBox()));
            ingRef.setDigest(jumbfBoxDigestService.calculateDigestForJumbfBox(ingredient.toJumbfBox()).getDigest());
            actionIngredients.add(ingRef);
        }

        // 2. ASERCIÓN DE ACCIONES
        List<ActionAssertion> actionList = new ArrayList<>();

        if (!actionIngredients.isEmpty()) {
            ActionAssertion actionOpened = new ActionAssertion();
            actionOpened.setAction(ActionChoice.C2PA_OPENED.getValue()); 

            Map<String, String> agentOpened = new HashMap<>();
            agentOpened.put("name", "JPEG Trust Orchestrator v1.0");
            actionOpened.setSoftwareAgent(agentOpened);
            
            actionOpened.setWhen(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            actionOpened.setIngredients(actionIngredients); 
            actionList.add(actionOpened);
        }

        if (wasTranscoded) {
            c2paSearchService.addTranscodeAssertion(actionList, "PNG", "JPEG");
        }

        ActionAssertion actionConverted = new ActionAssertion();
        actionConverted.setAction(ActionChoice.C2PA_CONVERTED.getValue());
        
        Map<String, String> agentConverted = new HashMap<>();
        agentConverted.put("name", "JPEG Trust Orchestrator v1.0");
        actionConverted.setSoftwareAgent(agentConverted);

        actionConverted.setDescription("Converted to JPEG Trust Standard Manifest");
        actionConverted.setWhen(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        actionList.add(actionConverted);

        ActionsAssertion actionsAssertion = new ActionsAssertion();
        actionsAssertion.setActions(actionList);
        c2paSearchService.addExistingAssertionUnique(actionsAssertion, existingAssertions);

        // 3. EMPAQUETADO
        List<JumbfBox> assertionJumbfBoxes = new ArrayList<>();
        for (Assertion assertion : existingAssertions) {
            assertionJumbfBoxes.add(assertion.toJumbfBox());
        }

        return assertionJumbfBoxes;
    }



    private byte[] ensureCleanJpeg(byte[] bytes) throws Exception {
        if (c2paSearchService.detectMediaType(bytes, "image/jpeg").equals("image/jpeg")) {
            return stripC2paApp11(bytes);
        } else {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) throw new Exception("ImageIO no pudo leer los bytes de la imagen.");
            
            BufferedImage rgbImage = new BufferedImage(
                    image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = rgbImage.createGraphics();
            g2d.setColor(java.awt.Color.WHITE);
            g2d.fillRect(0, 0, image.getWidth(), image.getHeight());
            g2d.drawImage(image, 0, 0, null);
            g2d.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean wrote = ImageIO.write(rgbImage, "jpg", baos);
            if (!wrote) throw new Exception("ImageIO falló al convertir a JPG.");
            
            return baos.toByteArray();
        }
    }

    private byte[] stripC2paApp11(byte[] jpeg) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        int i = 0;
        baos.write(jpeg, 0, 2);
        i = 2;

        while (i < jpeg.length - 1) {
            if ((jpeg[i] & 0xFF) != 0xFF) {
                baos.write(jpeg, i, jpeg.length - i);
                break;
            }
            byte marker = jpeg[i+1];
            
            if ((marker & 0xFF) == 0xDA) { // Start Of Scan (SOS)
                baos.write(jpeg, i, jpeg.length - i);
                break;
            }
            
            int length = ((jpeg[i+2] & 0xFF) << 8) | (jpeg[i+3] & 0xFF);
            
            if ((marker & 0xFF) == 0xEB) { // APP11
                if (i + 5 < jpeg.length && jpeg[i+4] == 0x4A && jpeg[i+5] == 0x50) {
                    i += length + 2; 
                    continue;
                }
            }
            
            baos.write(jpeg, i, length + 2);
            i += length + 2;
        }
        return baos.toByteArray();
    }

    private ManifestInfo extractOriginalManifest(byte[] imageBytes) {
        Path tempFile = null;
        try {
            String extension = c2paSearchService.detectMediaType(imageBytes, "image/jpeg").equals("image/png") ? ".png" : ".jpg";
            tempFile = Files.createTempFile("extraction_", extension);
            Files.write(tempFile, imageBytes);
            
            List<JumbfBox> jumbfBoxes = null;
            if (extension.equals(".jpg")) {
                jumbfBoxes = jpegCodestreamParser.parseMetadataFromFile(tempFile.toString());
            } else if (extension.equals(".png")) {
                jumbfBoxes = pngCodestreamParser.parseMetadataFromFile(tempFile.toString());
            }

            if (jumbfBoxes == null || jumbfBoxes.isEmpty()) {
                return null;
            }

            ManifestInfo manifestInfo = new ManifestInfo();
            manifestInfo.setRawJumbfBoxes(new ArrayList<>());
            manifestInfo.setOriginalImageBytes(imageBytes);

            for (JumbfBox box : jumbfBoxes) {
                System.out.println("[DEBUG] Caja encontrada: " + box.toString());
                c2paSearchService.searchC2paBoxes(box, "", manifestInfo);
            }

            return manifestInfo.getRawJumbfBoxes().isEmpty() ? null : manifestInfo;

        } catch (Exception e) {
            return null;
        } finally {
            try { if (tempFile != null) Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
        }
    }
}