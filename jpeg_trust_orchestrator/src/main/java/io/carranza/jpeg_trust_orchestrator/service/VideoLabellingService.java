package io.carranza.jpeg_trust_orchestrator.service;

import io.carranza.jpeg_trust_orchestrator.dto.ManifestInfo;
import io.carranza.jpeg_trust_orchestrator.dto.MediaExtractionResult;
import io.carranza.jpeg_trust_orchestrator.handler.Mp3LabellingHandler;
import io.carranza.jpeg_trust_orchestrator.handler.Mp4LabellingHandler;
import io.carranza.jpeg_trust_orchestrator.security.CryptoSignerService;
import io.carranza.jpeg_trust_orchestrator.util.MediaFormatUtils;
import io.carranza.jpeg_trust_orchestrator.utils.C2paSearchService;

import org.mp4parser.Box;
import org.mp4parser.IsoFile;
import org.mp4parser.boxes.UserBox;
import java.nio.file.Files;
import java.nio.file.Path;

import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;
import java.security.MessageDigest;

import org.mipams.jpegtrust.builders.ManifestBuilder;
import org.mipams.jpegtrust.entities.DigestResultForJumbfBox;
import org.mipams.jpegtrust.entities.JpegTrustUtils;
import org.mipams.jpegtrust.entities.assertions.Assertion;
import org.mipams.jpegtrust.entities.assertions.BindingAssertionBMFF;
import org.mipams.jpegtrust.entities.assertions.BindingAssertion;
import org.mipams.jpegtrust.entities.assertions.BindingAssertionBMFF.XPathExclusion;
import org.mipams.jpegtrust.entities.assertions.BindingAssertionBMFF.XPathData;
import org.mipams.jpegtrust.entities.assertions.actions.ActionAssertion;
import org.mipams.jpegtrust.entities.assertions.actions.ActionsAssertion;
import org.mipams.jpegtrust.entities.assertions.actions.GeneratorInfoMap;
import org.mipams.jpegtrust.entities.assertions.enums.ActionChoice;
import org.mipams.jpegtrust.entities.assertions.ingredients.IngredientAssertion;
import org.mipams.jpegtrust.entities.HashedUriReference;
import org.mipams.jpegtrust.jpeg_systems.content_types.StandardManifestContentType;
import org.mipams.jpegtrust.services.JumbfBoxDigestService;
import org.mipams.jpegtrust.services.validation.discovery.AssertionDiscovery;
import org.mipams.jumbf.entities.JumbfBox;
import org.mipams.jumbf.entities.JsonBox;
import org.mipams.jumbf.entities.BmffBox;
import org.mipams.jumbf.services.JpegCodestreamGenerator;
import org.mipams.jumbf.services.JpegCodestreamParser;
import org.springframework.stereotype.Service;
import org.jcodec.api.FrameGrab;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.model.Picture;
import org.jcodec.scale.AWTUtil;
import com.mpatric.mp3agic.Mp3File;
import com.mpatric.mp3agic.ID3v2;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class VideoLabellingService {

    private final CryptoSignerService cryptoSignerService;
    private final JumbfBoxDigestService jumbfBoxDigestService;
    private final JpegCodestreamGenerator jpegCodestreamGenerator;
    private final JpegCodestreamParser jpegCodestreamParser;
    private final Mp4LabellingHandler mp4LabellingHandler;
    private final Mp3LabellingHandler mp3LabellingHandler;
    private final C2paSearchService c2paSearchService;

    public VideoLabellingService(CryptoSignerService cryptoSignerService,
                                 JumbfBoxDigestService jumbfBoxDigestService,
                                 JpegCodestreamGenerator jpegCodestreamGenerator,
                                 JpegCodestreamParser jpegCodestreamParser,
                                 Mp4LabellingHandler mp4LabellingHandler,
                                 Mp3LabellingHandler mp3LabellingHandler,
                                 C2paSearchService c2paSearchService) {
        this.cryptoSignerService = cryptoSignerService;
        this.jumbfBoxDigestService = jumbfBoxDigestService;
        this.jpegCodestreamGenerator = jpegCodestreamGenerator;
        this.jpegCodestreamParser = jpegCodestreamParser;
        this.mp4LabellingHandler = mp4LabellingHandler;
        this.mp3LabellingHandler = mp3LabellingHandler;
        this.c2paSearchService = c2paSearchService;
    }

    public byte[] labelMedia(byte[] mediaBytes, String prompt, String orchestratorName) throws Exception {
        boolean isMp3 = mp3LabellingHandler.isMp3(mediaBytes);
        boolean isMp4 = !isMp3 && mp4LabellingHandler.isMp4(mediaBytes);

        if (!isMp4 && !isMp3) {
            throw new IllegalArgumentException("El archivo proporcionado no es MP4 o MP3 válido.");
        }

        MediaExtractionResult extraction = isMp4 
                ? mp4LabellingHandler.extract(mediaBytes) 
                : mp3LabellingHandler.extract(mediaBytes);

        ManifestInfo originalProvenance = null;
        if (extraction.originalC2paPayload != null) {
            originalProvenance = extractManifestInfoFromBytes(extraction.originalC2paPayload);
        }

        ManifestBuilder builder = new ManifestBuilder(new StandardManifestContentType());
        builder.setTitle("Authenticated Video/Audio Asset");
        builder.setInstanceID("uuid:" + UUID.randomUUID());
        builder.setGeneratorInfoName(orchestratorName);
        builder.setAlgorithm("sha256");
        builder.setClaimSignatureCertificates(cryptoSignerService.getCertificates());

        List<Assertion> existingAssertions = new ArrayList<>();
        List<HashedUriReference> actionIngredients = new ArrayList<>();

        if (prompt != null && !prompt.trim().isEmpty()) {
            IngredientAssertion promptIngredient = c2paSearchService.addPromptAssertions(existingAssertions, prompt);
            HashedUriReference pIngRef = new HashedUriReference();
            pIngRef.setAlgorithm("sha256");
            pIngRef.setUrl("self#jumbf=c2pa.assertions/" + c2paSearchService.getBareLabel(promptIngredient.toJumbfBox()));
            pIngRef.setDigest(jumbfBoxDigestService.calculateDigestForJumbfBox(promptIngredient.toJumbfBox()).getDigest());
            actionIngredients.add(pIngRef);
        }

        if (originalProvenance != null && originalProvenance.getActiveManifestUrl() != null) {
            try {
                String mediaType = isMp4 ? "video/mp4" : "audio/mpeg";
                byte[] thumbnailBytes = getMetadataThumbnail(mediaBytes, isMp4);
                originalProvenance.setOriginalImageBytes(thumbnailBytes);
                IngredientAssertion ingredient = c2paSearchService.addOriginalValidationResults(originalProvenance, existingAssertions, mediaType);
                
                JumbfBox ingredientBox = ingredient.toJumbfBox();
                if (ingredientBox != null) {
                    HashedUriReference ingRef = new HashedUriReference();
                    ingRef.setAlgorithm("sha256");
                    ingRef.setUrl("self#jumbf=c2pa.assertions/" + c2paSearchService.getBareLabel(ingredientBox));
                    ingRef.setDigest(jumbfBoxDigestService.calculateDigestForJumbfBox(ingredientBox).getDigest());
                    actionIngredients.add(ingRef);
                }
            } catch (Exception e) {
                System.err.println("[WARN] Fallo procesando ingrediente original: " + e.getMessage());
            }
        }

        if (!actionIngredients.isEmpty()) {
            List<ActionAssertion> actionList = new ArrayList<>();
            ActionAssertion actionOpened = new ActionAssertion();
            actionOpened.setAction(ActionChoice.C2PA_OPENED.getValue());
            GeneratorInfoMap agentOpened = new GeneratorInfoMap();
            agentOpened.setName(orchestratorName);
            actionOpened.setSoftwareAgent(agentOpened);
            actionOpened.setWhen(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            actionOpened.setIngredients(actionIngredients);
            actionList.add(actionOpened);

            ActionAssertion actionConverted = new ActionAssertion();
            actionConverted.setAction(ActionChoice.C2PA_CONVERTED.getValue());
            GeneratorInfoMap agentConverted = new GeneratorInfoMap();
            agentConverted.setName(orchestratorName);
            actionConverted.setSoftwareAgent(agentConverted);
            actionConverted.setDescription("Converted to JPEG Trust Standard Manifest");
            actionConverted.setWhen(DateTimeFormatter.ISO_INSTANT.format(Instant.now()));
            actionList.add(actionConverted);

            ActionsAssertion actions = new ActionsAssertion();
            actions.setActions(actionList);
            c2paSearchService.addExistingAssertionUnique(actions, existingAssertions);
        }

        for (Assertion a : existingAssertions) {
            JumbfBox box = a.toJumbfBox();
            builder.addCreatedAssertion(box, jumbfBoxDigestService.calculateDigestForJumbfBox(box));
        }

        JumbfBox realBindBox;
        byte[] realMediaHash;

        if (isMp4) {
            BindingAssertionBMFF realBinding = new BindingAssertionBMFF();
            realBinding.setAlgorithm("sha256");
            realBinding.setName("BMFF file hash");
            realBinding.setExclusions(buildXPathExclusions(isMp4)); 
            realBinding.setPadding(null); 
            realBinding.setPadding2(null); 
            // Para MP4, solemos heredar el digest si no modificamos el mdat, o recalcularlo
            realMediaHash = extractOriginalBindingDigest(extraction.originalC2paPayload);
            realBinding.setDigest(realMediaHash);
            realBindBox = realBinding.toJumbfBox();
        } else {
            // --- PASO 1: estimar el tamaño del store C2PA final ---
            BindingAssertion tempBinding = new BindingAssertion();
            tempBinding.setAlgorithm("sha256");
            tempBinding.setDigest(new byte[32]);
            // Exclusión placeholder con len=1, start=0 (parámetros: len primero, start segundo)
            tempBinding.addExclusionRange(1, 0);
            JumbfBox tempBox = tempBinding.toJumbfBox();
            builder.addCreatedAssertion(tempBox, jumbfBoxDigestService.calculateDigestForJumbfBox(tempBox));

            // Firma dummy para calcular overhead de RSA/ECDSA
            builder.setClaimSignature(new byte[72]);

            JumbfBox estimatedManifest = builder.build();
            byte[] estimatedStore = serializeJumbfToStoreBytes(estimatedManifest);

            // El store C2PA se envuelve en un ID3 tag al inicio del MP3.
            // El tamaño de exclusión es exactamente el del ID3 container que buildContainer() producirá.
            int id3ContainerSize = mp3LabellingHandler.buildContainer(estimatedStore).length;

            builder.removeCreatedAssertion(AssertionDiscovery.MipamsAssertion.CONTENT_BINDING.getBaseLabel());

            // --- PASO 2: calcular el hash sobre el archivo completo (ID3 placeholder + audio) ---
            // Construimos el archivo final con un ID3 tag de ceros del mismo tamaño para que
            // el offset de exclusión sea correcto, y hasheamos todo excepto ese bloque inicial.
            byte[] placeholderStore = new byte[estimatedStore.length]; // ceros, mismo tamaño
            byte[] placeholderId3 = mp3LabellingHandler.buildContainer(placeholderStore);
            byte[] fullFileForHash = new byte[placeholderId3.length + extraction.cleanMedia.length];
            System.arraycopy(placeholderId3, 0, fullFileForHash, 0, placeholderId3.length);
            System.arraycopy(extraction.cleanMedia, 0, fullFileForHash, placeholderId3.length, extraction.cleanMedia.length);

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            // Hashear todo excepto el bloque de exclusión (start=0, len=id3ContainerSize)
            md.update(fullFileForHash, id3ContainerSize, fullFileForHash.length - id3ContainerSize);
            realMediaHash = md.digest();

            // --- PASO 3: crear el binding real con la exclusión correcta ---
            // addExclusionRange(len, start): excluye desde start=0 durante id3ContainerSize bytes
            BindingAssertion dataBinding = new BindingAssertion();
            dataBinding.setAlgorithm("sha256");
            dataBinding.setDigest(realMediaHash);
            dataBinding.addExclusionRange(id3ContainerSize, 0);
            realBindBox = dataBinding.toJumbfBox();
        }

        // Añadir la aserción de binding definitiva
        builder.addCreatedAssertion(realBindBox, jumbfBoxDigestService.calculateDigestForJumbfBox(realBindBox));
        
        // --- FIN DE ESTIMACIÓN Y BINDING ---

        // Firma final y construcción
        builder.setClaimSignature(cryptoSignerService.signClaim(builder.encodeClaimToBeSigned()));
        JumbfBox finalManifestBox = builder.build();
        
        byte[] finalStoreBytes = serializeJumbfToStoreBytes(finalManifestBox);
        byte[] finalStore = combineStores(finalStoreBytes, extraction.originalC2paPayload, isMp4);
        
        return isMp4 
            ? mp4LabellingHandler.inject(extraction.cleanMedia, finalStore) 
            : mp3LabellingHandler.inject(extraction.cleanMedia, finalStore);
    }


    private List<XPathExclusion> buildXPathExclusions(boolean isMp4) {
        List<XPathExclusion> exclusions = new ArrayList<>();
        if (isMp4) {
            exclusions.add(new XPathExclusion("/ftyp"));
            
            XPathExclusion uuidExclusion = new XPathExclusion("/uuid");
            byte[] googleUuidBytes = java.util.Base64.getDecoder().decode("2P7D1hsOSDySl1goh37EgQ==");
            XPathData uuidData = new XPathData(8, googleUuidBytes);
            uuidExclusion.setData(List.of(uuidData));
            
            exclusions.add(uuidExclusion);
            exclusions.add(new XPathExclusion("/mfra"));
            exclusions.add(new XPathExclusion("/free"));
        } else {
            exclusions.add(new XPathExclusion("/id3v2"));
        }
        return exclusions;
    }

    public byte[] getMetadataThumbnail(byte[] mediaBytes, boolean isMp4) {
        Path tempFile = null;
        try {
            tempFile = Files.createTempFile("thumb_ext_", isMp4 ? ".mp4" : ".mp3");
            Files.write(tempFile, mediaBytes);
            File file = tempFile.toFile();

            if (isMp4) {
                Picture picture = FrameGrab.createFrameGrab(NIOUtils.readableChannel(file)).seekToSecondPrecise(0.0).getNativeFrame();
                if (picture != null) {
                    BufferedImage bufferedImage = AWTUtil.toBufferedImage(picture);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    ImageIO.write(bufferedImage, "jpg", baos);
                    return baos.toByteArray();
                }
            } else {
                Mp3File mp3file = new Mp3File(file);
                if (mp3file.hasId3v2Tag()) {
                    byte[] img = mp3file.getId3v2Tag().getAlbumImage();
                    if (img != null && img.length > 0) return img;
                }
                return getDefaultMp3Icon();
            }
        } catch (Exception e) {} finally {
            if (tempFile != null) try { Files.deleteIfExists(tempFile); } catch (Exception ignored) {}
        }
        return null; 
    }
    
    private byte[] getDefaultMp3Icon() {
        try {
            InputStream is = getClass().getResourceAsStream("/mp3.png");
            if (is != null) return is.readAllBytes();
        } catch (Exception e) {}
        return new byte[0]; 
    }

    private byte[] combineStores(byte[] newStoreBytes, byte[] oldStoreBytes, boolean isMp4) {
        if (oldStoreBytes == null || oldStoreBytes.length < 16) return newStoreBytes;
        if (oldStoreBytes[12] != 'j' || oldStoreBytes[13] != 'u' || oldStoreBytes[14] != 'm' || oldStoreBytes[15] != 'd') return newStoreBytes; 
        
        int jumdSize = (int) MediaFormatUtils.readUint32BE(oldStoreBytes, 8);
        int payloadStart = 8 + jumdSize; 
        int payloadLen = oldStoreBytes.length - payloadStart;
        if (payloadLen <= 0) return newStoreBytes;

        long newSize = MediaFormatUtils.readUint32BE(newStoreBytes, 0) + payloadLen;
        byte[] combined = new byte[newStoreBytes.length + payloadLen];

        System.arraycopy(newStoreBytes, 0, combined, 0, newStoreBytes.length);
        System.arraycopy(oldStoreBytes, payloadStart, combined, newStoreBytes.length, payloadLen);
        ByteBuffer.wrap(combined).putInt(0, (int) newSize);
        return combined;
    }

    private ManifestInfo extractManifestInfoFromBytes(byte[] storeBytes) {
        if (storeBytes == null || storeBytes.length < 8) return null;
        Path tempIn = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(new byte[]{(byte)0xFF, (byte)0xD8});
            int offset = 0, zsqn = 1;
            while (offset < storeBytes.length) {
                int chunk = Math.min(storeBytes.length - offset, 65535 - 12);
                baos.write(new byte[]{(byte)0xFF, (byte)0xEB});
                int segmentLen = chunk + 10; 
                baos.write((segmentLen >> 8) & 0xFF); baos.write(segmentLen & 0xFF);
                baos.write(new byte[]{0x4A, 0x50, 0x00, 0x01}); 
                baos.write((zsqn >> 24) & 0xFF); baos.write((zsqn >> 16) & 0xFF); baos.write((zsqn >> 8) & 0xFF); baos.write(zsqn & 0xFF);
                baos.write(storeBytes, offset, chunk);
                offset += chunk; zsqn++;
            }
            baos.write(new byte[]{(byte)0xFF, (byte)0xD9});

            tempIn = Files.createTempFile("dummy_parse", ".jpg");
            Files.write(tempIn, baos.toByteArray());

            List<JumbfBox> boxes = jpegCodestreamParser.parseMetadataFromFile(tempIn.toString());
            ManifestInfo info = new ManifestInfo();
            if (boxes != null) for (JumbfBox b : boxes) c2paSearchService.searchC2paBoxes(b, "", info);
            return info;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (tempIn != null) Files.deleteIfExists(tempIn); } catch (Exception ignored) {}
        }
    }

    private byte[] serializeJumbfToStoreBytes(JumbfBox newManifestBox) throws Exception {
        JumbfBox store = JpegTrustUtils.buildTrustRecord(newManifestBox);
        Path tempIn = Files.createTempFile("dummy_in", ".jpg"), tempOut = Files.createTempFile("dummy_out", ".jpg");
        try {
            Files.write(tempIn, new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xD9});
            jpegCodestreamGenerator.generateJumbfMetadataToFile(List.of(store), tempIn.toString(), tempOut.toString());
            byte[] jpeg = Files.readAllBytes(tempOut);

            for (int i = 0; i <= jpeg.length - 8; i++) {
                if (jpeg[i+4] == 'j' && jpeg[i+5] == 'u' && jpeg[i+6] == 'm' && jpeg[i+7] == 'b') {
                    long size = MediaFormatUtils.readUint32BE(jpeg, i);
                    if (size > 0 && i + size <= jpeg.length) {
                        byte[] jumbBytes = new byte[(int)size];
                        System.arraycopy(jpeg, i, jumbBytes, 0, (int)size);
                        return jumbBytes;
                    }
                }
            }
            throw new Exception("No se encontró la firma JUMBF en el JPEG.");
        } finally {
            Files.deleteIfExists(tempIn); Files.deleteIfExists(tempOut);
        }
    }

    private byte[] extractOriginalBindingDigest(byte[] storeBytes) {
        Path tempIn = null;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            baos.write(new byte[]{(byte)0xFF, (byte)0xD8});
            int offset = 0, zsqn = 1;
            while (offset < storeBytes.length) {
                int chunk = Math.min(storeBytes.length - offset, 65535 - 12);
                baos.write(new byte[]{(byte)0xFF, (byte)0xEB});
                int segmentLen = chunk + 10; 
                baos.write((segmentLen >> 8) & 0xFF); baos.write(segmentLen & 0xFF);
                baos.write(new byte[]{0x4A, 0x50, 0x00, 0x01}); 
                baos.write((zsqn >> 24) & 0xFF); baos.write((zsqn >> 16) & 0xFF); baos.write((zsqn >> 8) & 0xFF); baos.write(zsqn & 0xFF);
                baos.write(storeBytes, offset, chunk);
                offset += chunk; zsqn++;
            }
            baos.write(new byte[]{(byte)0xFF, (byte)0xD9});

            tempIn = Files.createTempFile("extraction_parse", ".jpg");
            Files.write(tempIn, baos.toByteArray());

            List<JumbfBox> boxes = jpegCodestreamParser.parseMetadataFromFile(tempIn.toString());
            if (boxes != null) {
                for (JumbfBox box : boxes) {
                    byte[] digest = findBindingDigestRecursive(box);
                    if (digest != null) return digest;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Fallo en la extracción: " + e.getMessage());
        } finally {
            try { if (tempIn != null) Files.deleteIfExists(tempIn); } catch (Exception ignored) {}
        }
        return null;
    }


    private byte[] findBindingDigestRecursive(BmffBox box) {
        String targetLabel = "c2pa.hash.bmff.v3";
        
        if (box instanceof JumbfBox) {
            JumbfBox jumbf = (JumbfBox) box;
            if (jumbf.getDescriptionBox() != null && targetLabel.equals(jumbf.getDescriptionBox().getLabel())) {
                for (BmffBox child : jumbf.getContentBoxList()) {
                    if (child instanceof JsonBox) {
                        byte[] contentBytes = ((JsonBox) child).getContent();
                        String json = new String(contentBytes, StandardCharsets.UTF_8);
                        return extractHashFromJson(json);
                    }
                }
            }
            for (BmffBox child : jumbf.getContentBoxList()) {
                byte[] found = findBindingDigestRecursive(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private byte[] extractHashFromJson(String json) {
        try {
            if (json.contains("\"hash\"")) {
                String part = json.split("\"hash\"\\s*:\\s*\"")[1].split("\"")[0];
                return Base64.getDecoder().decode(part);
            }
        } catch (Exception e) {}
        return null;
    }
}