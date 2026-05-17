package io.carranza.jpeg_trust_orchestrator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.carranza.jpeg_trust_orchestrator.dto.AiGenerationRecipe;
import io.carranza.jpeg_trust_orchestrator.dto.ManifestInfo; 
import io.carranza.jpeg_trust_orchestrator.security.CryptoSignerService;
import org.mipams.jumbf.entities.JumbfBox;
import org.mipams.jpegtrust.entities.assertions.actions.ActionAssertion;
import org.mipams.jpegtrust.entities.assertions.actions.ActionsAssertion;
import org.mipams.jpegtrust.entities.assertions.actions.GeneratorInfoMap;
import org.mipams.jpegtrust.entities.assertions.enums.ActionChoice;
import org.mipams.jpegtrust.entities.assertions.Assertion;
import org.mipams.jpegtrust.entities.assertions.BindingAssertion;
import org.mipams.jpegtrust.entities.DigestResultForJumbfBox;
import org.mipams.jpegtrust.entities.JpegTrustUtils;
import org.mipams.jpegtrust.jpeg_systems.content_types.StandardManifestContentType;
import org.mipams.jpegtrust.builders.ManifestBuilder;
import org.mipams.jpegtrust.services.JumbfBoxDigestService;
import org.mipams.jpegtrust.services.validation.discovery.AssertionDiscovery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.mipams.jpegtrust.entities.assertions.tfm.AiDisclosureAssertion;
import org.mipams.jpegtrust.entities.assertions.tfm.EmbeddedDataAssertion;
import org.mipams.jpegtrust.entities.assertions.ingredients.IngredientAssertion;
import org.mipams.jpegtrust.entities.assertions.ingredients.IngredientAssertionV1;
import org.mipams.jpegtrust.entities.assertions.enums.AssetTypeChoice;
import org.mipams.jpegtrust.entities.assertions.AssetType;
import org.mipams.jpegtrust.entities.HashedUriReference;
import io.carranza.jpeg_trust_orchestrator.utils.C2paSearchService;
import java.util.stream.Stream;

import java.util.HashMap;
import java.security.PrivateKey;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

@Service
public class JpegTrustAiLabellingService {

    @Autowired
    private org.mipams.jumbf.services.JpegCodestreamParser jpegCodestreamParser;
    
    private final C2paSearchService c2paSearchService;
    private final CryptoSignerService cryptoSignerService;
    private final JumbfBoxDigestService jumbfBoxDigestService;
    private final ObjectMapper objectMapper;

    public JpegTrustAiLabellingService(CryptoSignerService cryptoSignerService,
                                       JumbfBoxDigestService jumbfBoxDigestService,
                                       ObjectMapper objectMapper,
                                       C2paSearchService c2paSearchService) {
        this.cryptoSignerService = cryptoSignerService;
        this.jumbfBoxDigestService = jumbfBoxDigestService;
        this.objectMapper = objectMapper;
        this.c2paSearchService = c2paSearchService;
    }

public JumbfBox labelGeneratedAsset(AiGenerationRecipe recipe) throws Exception {

    System.out.println("\n=======================================================");
    System.out.println("=== SUPER DEBUG: INICIO DE ETIQUETADO JPEG TRUST ===");
    System.out.println("=======================================================");

    System.out.println("[DEBUG] 1. Comprobando bytes de imagen original...");
    byte[] imageBytes = recipe.getGeneratedImageBytes();
    System.out.println("[DEBUG] recipe.getGeneratedImageBytes() es null?: " + (imageBytes == null));
    System.out.println("[DEBUG] Tamaño de imageBytes: " + (imageBytes != null ? imageBytes.length : 0) + " bytes");

    Path tempFile = Files.createTempFile("jpeg_trust_", ".jpg");
    Files.write(tempFile, imageBytes);
    String assetFileUrl = tempFile.toString();
    System.out.println("[DEBUG] Archivo temporal guardado en: " + assetFileUrl);

    ManifestBuilder builder = new ManifestBuilder(new StandardManifestContentType());
    System.out.println("[DEBUG] ManifestBuilder instanciado.");

    System.out.println("\n[DEBUG] 2. Generando lista de aserciones base...");
    List<JumbfBox> assertions = createAssertionList(recipe);
    System.out.println("[DEBUG] assertions es null?: " + (assertions == null));
    System.out.println("[DEBUG] Cantidad de aserciones generadas: " + (assertions != null ? assertions.size() : 0));

    if (assertions != null) {
        for (int i = 0; i < assertions.size(); i++) {
            JumbfBox assertionBox = assertions.get(i);
            System.out.println("[DEBUG] Evaluando aserción [" + i + "]. Es null?: " + (assertionBox == null));
            if (assertionBox != null) {
                DigestResultForJumbfBox digestResult = jumbfBoxDigestService.calculateDigestForJumbfBox(assertionBox);
                System.out.println("[DEBUG]   -> Digest calculado. Es null?: " + (digestResult == null));
                builder.addCreatedAssertion(assertionBox, digestResult);
            }
        }
    }

    System.out.println("\n[DEBUG] 3. Creando tempBindingAssertion...");
    final BindingAssertion tempBindingAssertion = new BindingAssertion();
    tempBindingAssertion.setAlgorithm("sha256");
    tempBindingAssertion.addExclusionRange(0, 0);
    byte[] pad = new byte[6];
    Arrays.fill(pad, Byte.parseByte("0"));
    tempBindingAssertion.setPadding(pad);
    JumbfBox tempBindingAssertionBox = tempBindingAssertion.toJumbfBox();
    System.out.println("[DEBUG] tempBindingAssertionBox es null?: " + (tempBindingAssertionBox == null));
    
    DigestResultForJumbfBox tempResult = jumbfBoxDigestService.calculateDigestForJumbfBox(tempBindingAssertionBox);
    builder.addCreatedAssertion(tempBindingAssertionBox, tempResult);

    builder.setTitle("AI Generated Asset");
    builder.setInstanceID("uuid:" + UUID.randomUUID());
    builder.setGeneratorInfoName("Fooocus Spring Orchestrator 1.0");
    builder.setAlgorithm("sha256");
    
    System.out.println("\n[DEBUG] 4. Obteniendo certificados del signer...");
    List<java.security.cert.X509Certificate> certs = cryptoSignerService.getCertificates();
    System.out.println("[DEBUG] cryptoSignerService.getCertificates() es null?: " + (certs == null));
    System.out.println("[DEBUG] Cantidad de certificados: " + (certs != null ? certs.size() : 0));
    builder.setClaimSignatureCertificates(certs);

    System.out.println("\n[DEBUG] 5. Construyendo manifiesto activo temporal (PRIMER BUILD)...");
    JumbfBox activeManifest = builder.build();
    System.out.println("[DEBUG] activeManifest es null?: " + (activeManifest == null));
    
    //JumbfBox trustRecord = JpegTrustUtils.buildTrustRecord(activeManifest);
    //System.out.println("[DEBUG] trustRecord base es null?: " + (trustRecord == null));
    
    System.out.println("\n[DEBUG] 6. Procesando manifiestos de referencia...");
    List<JumbfBox> refs = recipe.getReferenceTrustRecords();
    System.out.println("[DEBUG] recipe.getReferenceTrustRecords() es null?: " + (refs == null));
    System.out.println("[DEBUG] Cantidad de refs: " + (refs != null ? refs.size() : 0));
    
    JumbfBox[] referenceTrustRecords = refs != null ? refs.toArray(new JumbfBox[0]) : new JumbfBox[0];

    JumbfBox[] all = Stream.concat(Arrays.stream(referenceTrustRecords),Stream.of(activeManifest)).toArray(JumbfBox[]::new);
    JumbfBox tempTrustRecord = JpegTrustUtils.buildTrustRecord(all);
    
    long totalBytesRequired = JpegTrustUtils.getSizeOfJumbfInApp11SegmentsInBytes(tempTrustRecord);
    System.out.println("\n[DEBUG] -> TAMAÑO ESTIMADO (totalBytesRequired): " + totalBytesRequired + " bytes");

    System.out.println("\n[DEBUG] 7. Generando Content Binding real...");
    BindingAssertion contentBindingAssertion = cryptoSignerService.getBindingAssertionForAsset(assetFileUrl, totalBytesRequired);
    System.out.println("[DEBUG] contentBindingAssertion es null?: " + (contentBindingAssertion == null));
    
    builder.removeCreatedAssertion(AssertionDiscovery.MipamsAssertion.CONTENT_BINDING.getBaseLabel());
    JumbfBox contentBindingAssertionBox = contentBindingAssertion.toJumbfBox();
    DigestResultForJumbfBox contentBindingAssertionResult = jumbfBoxDigestService.calculateDigestForJumbfBox(contentBindingAssertionBox);
    builder.addCreatedAssertion(contentBindingAssertionBox, contentBindingAssertionResult);

    System.out.println("\n[DEBUG] 8. Firmando el manifiesto...");
    byte[] claimToBeSigned = builder.encodeClaimToBeSigned();
    System.out.println("[DEBUG] builder.encodeClaimToBeSigned() es null?: " + (claimToBeSigned == null));
    System.out.println("[DEBUG] Longitud de claimToBeSigned: " + (claimToBeSigned != null ? claimToBeSigned.length : 0) + " bytes");
    
    byte[] signature = cryptoSignerService.signClaim(claimToBeSigned);
    System.out.println("[DEBUG] cryptoSignerService.signClaim() devolvió null?: " + (signature == null));
    System.out.println("[DEBUG] Longitud de la FIRMA: " + (signature != null ? signature.length : 0) + " bytes");
    builder.setClaimSignature(signature);

    System.out.println("\n[DEBUG] 9. Construyendo manifiesto final (SEGUNDO BUILD)...");
    JumbfBox finalActiveManifest = builder.build();
    System.out.println("[DEBUG] finalActiveManifest es null?: " + (finalActiveManifest == null));
    
    //JumbfBox finaltrustRecord = JpegTrustUtils.buildTrustRecord(finalActiveManifest);
    //System.out.println("[DEBUG] finaltrustRecord es null?: " + (finaltrustRecord == null));

    JumbfBox[] allFinalManifests = Stream.concat(Arrays.stream(referenceTrustRecords), Stream.of(finalActiveManifest)).toArray(JumbfBox[]::new);
    JumbfBox recordToReturn = JpegTrustUtils.buildTrustRecord(allFinalManifests);

    long finalActualSize = JpegTrustUtils.getSizeOfJumbfInApp11SegmentsInBytes(recordToReturn);
    System.out.println("\n[DEBUG] -> TAMAÑO REAL FINAL A INYECTAR: " + finalActualSize + " bytes");
    
    System.out.println("=======================================================");
    System.out.println("=== FIN SUPER DEBUG ===");
    System.out.println("=======================================================\n");

    return recordToReturn;
}

    /**
     * Builds a throwaway manifest with a zero-padded binding to obtain a realistic
     * initial size estimate, avoiding many stabilisation iterations.
     */
    private long estimateInitialSize(AiGenerationRecipe recipe, ManifestBuilder builder) throws Exception {
        // Use a dummy 72-byte signature (max ECDSA-P256 DER length) for the estimate
        byte[] dummySig = new byte[72];
        builder.setClaimSignature(dummySig);

        BindingAssertion dummyBinding = new BindingAssertion();
        dummyBinding.setAlgorithm("sha256");
        dummyBinding.addExclusionRange(65536 + 20, 2);
        dummyBinding.setDigest(new byte[32]);
        dummyBinding.setPadding(new byte[6]);

        JumbfBox dummyBox = dummyBinding.toJumbfBox();
        builder.addCreatedAssertion(dummyBox, jumbfBoxDigestService.calculateDigestForJumbfBox(dummyBox));

        List<JumbfBox> allManifests = new ArrayList<>();
        if (recipe.getReferenceTrustRecords() != null) {
            allManifests.addAll(Arrays.asList(recipe.getReferenceTrustRecords().toArray(new JumbfBox[0])));
        }
        allManifests.add(builder.build());

        JumbfBox tempRecord = JpegTrustUtils.buildTrustRecord(allManifests.toArray(new JumbfBox[0]));
        long size = JpegTrustUtils.getSizeOfJumbfInApp11SegmentsInBytes(tempRecord);

        // Clean up dummy binding so the main loop starts fresh
        builder.removeCreatedAssertion(AssertionDiscovery.MipamsAssertion.CONTENT_BINDING.getBaseLabel());
        return size;
    }

    private IngredientAssertion addReferenceImageAssertion(List<Assertion> existingAssertions,
                                                           AiGenerationRecipe recipe) throws Exception {

        byte[] referenceImageBytes = c2paSearchService.getReferenceImageBytes(recipe);
        ManifestInfo manifestInfo = extractManifestFromReferenceImage(referenceImageBytes);
        recipe.setReferenceTrustRecords(manifestInfo.getRawJumbfBoxes());
        manifestInfo.setOriginalImageBytes(referenceImageBytes);
        String mediaType = "image/jpeg";
        mediaType = c2paSearchService.detectMediaType(referenceImageBytes, mediaType);
        IngredientAssertion ingredient = c2paSearchService.addOriginalValidationResults(manifestInfo, existingAssertions, mediaType);
        return ingredient;
    }

    private ManifestInfo extractManifestFromReferenceImage(byte[] imageBytes) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("ref_image_manifest_", ".jpg");
            Files.write(tempFile, imageBytes);

            List<JumbfBox> jumbfBoxes = jpegCodestreamParser.parseMetadataFromFile(tempFile.toString());
            if (jumbfBoxes == null || jumbfBoxes.isEmpty()) return null;

            ManifestInfo manifestInfo = new ManifestInfo();
            manifestInfo.setRawJumbfBoxes(new ArrayList<>());

            for (JumbfBox box : jumbfBoxes) {
                c2paSearchService.searchC2paBoxes(box, "", manifestInfo);
            }

            return manifestInfo;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (tempFile != null) Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
        }
    }





private IngredientAssertion addInferenceAssertions(List<Assertion> existingAssertions, AiGenerationRecipe recipe) throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("seed", recipe.getSeed());
        params.put("guidance_scale", recipe.getGuidanceScale());

        EmbeddedDataAssertion data = new EmbeddedDataAssertion();
        data.setMediaType("application/json");
        data.setData(objectMapper.writeValueAsBytes(params));
        c2paSearchService.addExistingAssertionUnique(data, existingAssertions);

        // --- DEBUG DE REFERENCIAS INICIO ---
        System.out.println("\n[DEBUG URI] Creando EmbeddedDataAssertion para Inferencia...");
        String dataLabel = data.getLabel(); 
        System.out.println("[DEBUG URI] Etiqueta asignada a EmbeddedDataAssertion: " + (dataLabel == null ? "¡NULL! (Peligro)" : dataLabel));
        // -----------------------------------

        IngredientAssertion ingredient = new IngredientAssertion();
        ingredient.setTitle("Inference Parameters");
        ingredient.setMediaType("application/json");
        ingredient.setRelationship(IngredientAssertionV1.RELATIONSHIP_INPUT_OF);
        
        HashedUriReference uriRef = c2paSearchService.addEmbededData(data, jumbfBoxDigestService.calculateDigestForJumbfBox(data.toJumbfBox()));
        ingredient.setData(uriRef);

        // --- DEBUG DE REFERENCIAS FIN ---
        System.out.println("[DEBUG URI] HashedUriReference generado en el ingrediente: " + uriRef.getUrl());
        if (dataLabel == null && uriRef.getUrl().contains("null")) {
            System.err.println("[! PELIGRO !] La URI apunta a null. Al validar, el parser no encontrará la aserción y lanzará assertion.inaccessible.");
        }
        // -----------------------------------

        AssetType assetType = new AssetType();
        assetType.setType(AssetTypeChoice.C2PA_TYPES_GENERATOR_PARAMETERS);
        ingredient.setDataTypes(List.of(assetType));

        c2paSearchService.addExistingAssertionUnique(ingredient, existingAssertions);
        return ingredient;
    }


    public List<HashedUriReference> addIngredients(List<HashedUriReference> currentList, IngredientAssertion assertion) throws Exception {
        List<HashedUriReference> result = new ArrayList<>(currentList != null ? currentList : new ArrayList<>());
        if (assertion == null) return result;
        JumbfBox box = assertion.toJumbfBox();
        DigestResultForJumbfBox digest = jumbfBoxDigestService.calculateDigestForJumbfBox(box);
        HashedUriReference ref = new HashedUriReference();
        ref.setAlgorithm(digest.getAlgorithm());
        ref.setUrl("self#jumbf=c2pa.assertions/" + c2paSearchService.getBareLabel(box));
        ref.setDigest(digest.getDigest());
        result.add(ref);
        return result;
    }

    private List<JumbfBox> createAssertionList(AiGenerationRecipe recipe) throws Exception {
        List<Assertion> existingAssertions = new ArrayList<>();
        List<HashedUriReference> actionIngredients = new ArrayList<>();

        if (recipe.getPrompt() != null && !recipe.getPrompt().isEmpty()) {
            actionIngredients = addIngredients(actionIngredients, c2paSearchService.addPromptAssertions(existingAssertions, recipe.getPrompt()));
        }

        if ("PROMPT_IMAGE".equals(recipe.getMode()) && recipe.getReferenceImageBase64() != null) {
            actionIngredients = addIngredients(actionIngredients, addReferenceImageAssertion(existingAssertions, recipe));
        }

        actionIngredients = addIngredients(actionIngredients, addInferenceAssertions(existingAssertions, recipe));

        ActionAssertion actionCreated = new ActionAssertion();
        actionCreated.setAction(ActionChoice.C2PA_CREATED.getValue());
        actionCreated.setWhen(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
        
        GeneratorInfoMap agentCreated = new GeneratorInfoMap();
        agentCreated.setName("Fooocus 2.5.0");
        actionCreated.setSoftwareAgent(agentCreated);
        
        actionCreated.setDigitalSourceType("http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia");
        actionCreated.setIngredients(actionIngredients);

        ActionsAssertion actionsAssertion = new ActionsAssertion();
        actionsAssertion.setActions(List.of(actionCreated));
        c2paSearchService.addExistingAssertionUnique(actionsAssertion, existingAssertions);

        AiDisclosureAssertion aiDisclosure = new AiDisclosureAssertion();
        aiDisclosure.setModelType("diffusion");
        aiDisclosure.setModelName("Fooocus 2.5.0");
        aiDisclosure.setModelIdentifier("https://github.com/lllyasviel/Fooocus");
        aiDisclosure.setHumanOversightLevel(AiDisclosureAssertion.HumanOversightLevel.PROMPT_GUIDED);
        aiDisclosure.setScientificDomain(List.of("cs.AI", "cs.CV"));
        c2paSearchService.addExistingAssertionUnique(aiDisclosure, existingAssertions);

        List<JumbfBox> boxes = new ArrayList<>();
        for (Assertion a : existingAssertions) boxes.add(a.toJumbfBox());
        return boxes;
    }
}