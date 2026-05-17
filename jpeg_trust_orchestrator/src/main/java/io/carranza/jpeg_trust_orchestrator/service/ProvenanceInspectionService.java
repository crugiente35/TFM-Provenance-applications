package io.carranza.jpeg_trust_orchestrator.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.mipams.jpegtrust.entities.validation.trustindicators.TrustIndicatorSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ProvenanceInspectionService {

    private final ObjectMapper objectMapper;

    public ProvenanceInspectionService() {
        this.objectMapper = new ObjectMapper();
    }

    public List<Map<String, Object>> buildInspectionNodes(TrustIndicatorSet trustSet, byte[] currentImageBytes) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        try {
            String jsonStr = objectMapper.writeValueAsString(trustSet);
            Map<String, Object> rootMap = objectMapper.readValue(jsonStr, new TypeReference<Map<String, Object>>() {});
            
            List<Map<String, Object>> manifests = new ArrayList<>();

            Object manifestsObj = rootMap.get("manifests");
            if (manifestsObj instanceof Map) {
                Map<?, ?> map = (Map<?, ?>) manifestsObj;
                for (Object value : map.values()) {
                    if (value instanceof Map) {
                        manifests.add((Map<String, Object>) value);
                    }
                }
            } else if (manifestsObj instanceof List) {
                List<?> list = (List<?>) manifestsObj;
                for (Object item : list) {
                    if (item instanceof Map) {
                        manifests.add((Map<String, Object>) item);
                    }
                }
            } else if (rootMap.containsKey("claim") || rootMap.containsKey("claim.v2")) {
                manifests.add(rootMap);
            }

            if (!manifests.isEmpty()) {
                for (int i = 0; i < manifests.size(); i++) {
                    Map<String, Object> manifest = manifests.get(i);
                    boolean isFinal = (i == 0); 

                    Map<String, Object> node = new HashMap<>();
                    
                    String label = (String) manifest.get("label");
                    if (label == null && manifest.get("claim") != null) {
                        Map<String, Object> claimMap = (Map<String, Object>) manifest.get("claim");
                        label = (String) claimMap.get("instanceID");
                    }
                    node.put("id", label != null ? label : "manifest-" + i);
                    node.put("isFinal", isFinal);

                    if (isFinal) {
                        node.put("thumbnail", Base64.getEncoder().encodeToString(currentImageBytes));
                    } else {
                        String thumbData = extractThumbnailByType(manifest, "thumbnail.claim");
                        
                        if (thumbData == null && i > 0) {
                            thumbData = extractThumbnailByType(manifests.get(i - 1), "thumbnail.ingredient");
                        }
                        
                        if (thumbData == null) {
                            thumbData = extractThumbnailByType(manifest, "thumbnail");
                        }
                        
                        node.put("thumbnail", thumbData);
                    }

                    String softwareAgent = "Desconocido";
                    Map<String, Object> claim = (Map<String, Object>) manifest.get("claim");
                    if (claim == null) claim = (Map<String, Object>) manifest.get("claim.v2");

                    if (claim != null && claim.get("claim_generator_info") != null) {
                        Object genInfo = claim.get("claim_generator_info");
                        if (genInfo instanceof Map) {
                            softwareAgent = (String) ((Map<String, Object>) genInfo).get("name");
                        } else if (genInfo instanceof String) {
                            softwareAgent = (String) genInfo;
                        }
                    }

                    String prompt = "N/A";
                    String aiDisclosure = "N/A";
                    String digitalSourceType = "N/A";
                    List<Map<String, Object>> actionsData = new ArrayList<>();
                    boolean isAI = false;

                    Map<String, Object> assertions = (Map<String, Object>) manifest.get("assertion_store");
                    if (assertions == null) assertions = (Map<String, Object>) manifest.get("assertions");
                    
                    if (assertions != null) {
                        String aiKey = Stream.of("c2pa.ai-disclosure", "ai-disclosure", "jpeg.trust.ai-disclosure")
                                .filter(assertions::containsKey)
                                .findFirst()
                                .orElse(null);

                        if (aiKey != null) {
                            Map<String, Object> aiDisc = (Map<String, Object>) assertions.get(aiKey);
                            Object oversight = aiDisc.get("humanOversightLevel");
                            String modelName = aiDisc.get("modelName") != null ? (String) aiDisc.get("modelName") : (String) aiDisc.get("model_name");
                            aiDisclosure = "AI Model: " + (modelName != null ? modelName : "Generative AI");
                            if (oversight != null) aiDisclosure += " (Oversight: " + oversight + ")";
                        }

                        String promptKey = assertions.containsKey("c2pa.embedded-data") ? "c2pa.embedded-data" : (assertions.containsKey("embedded-data") ? "embedded-data" : null);
                        if (promptKey != null) {
                            Map<String, Object> embData = (Map<String, Object>) assertions.get(promptKey);
                            if (embData.get("data") != null) {
                                String base64Prompt = (String) embData.get("data");
                                try {
                                    prompt = new String(Base64.getDecoder().decode(base64Prompt));
                                } catch (Exception e) {
                                    prompt = base64Prompt;
                                }
                            }
                        }

                        String actionKey = Stream.of("c2pa.actions.v2", "c2pa.actions", "actions")
                                .filter(assertions::containsKey)
                                .findFirst()
                                .orElse(null);
                        
                        if (actionKey != null) {
                            Map<String, Object> acts = (Map<String, Object>) assertions.get(actionKey);
                            List<Map<String, Object>> actList = (List<Map<String, Object>>) acts.get("actions");
                            if (actList != null) {
                                actionsData = actList;
                                for (Map<String, Object> action : actList) {
                                    if (action.containsKey("digitalSourceType")) {
                                        String dst = (String) action.get("digitalSourceType");
                                        digitalSourceType = dst;
                                        if ("http://cv.iptc.org/newscodes/digitalsourcetype/trainedAlgorithmicMedia".equals(dst)) {
                                            isAI = true;
                                        }
                                        if (dst != null && dst.toLowerCase().contains("algorithmic")) isAI = true;
                                    }
                                }
                            }
                        }
                    }

                    boolean isValid = false;
                    Map<String, Object> status = (Map<String, Object>) manifest.get("status");
                    if (status != null) {
                        String sigStatus = (String) status.get("signature");
                        if ("claimSignature.validated".equals(sigStatus)) isValid = true;
                    } else if (rootMap.containsKey("validation_state")) {
                        isValid = "Valid".equalsIgnoreCase((String) rootMap.get("validation_state"));
                    }

                    String actionsSummary = actionsData.stream()
                        .map(action -> (String) action.get("action"))
                        .filter(name -> name != null)
                        .collect(Collectors.joining(", "));
                    node.put("softwareAgent", softwareAgent);
                    node.put("prompt", prompt);
                    node.put("aiDisclosure", aiDisclosure);
                    node.put("digitalSourceType", digitalSourceType); 
                    node.put("isAI", isAI);                           
                    node.put("isValid", isValid);
                    node.put("actions", actionsSummary);
                    node.put("trustIndicatorSet", manifest);

                    nodes.add(node);
                }
            }
            return nodes;

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> fallbackNode = new HashMap<>();
            fallbackNode.put("id", "error-node");
            fallbackNode.put("isFinal", true);
            fallbackNode.put("thumbnail", Base64.getEncoder().encodeToString(currentImageBytes));
            fallbackNode.put("softwareAgent", "Error: " + e.getMessage());
            fallbackNode.put("prompt", "N/A");
            fallbackNode.put("aiDisclosure", "N/A");
            fallbackNode.put("isValid", false);
            nodes.add(fallbackNode);
            return nodes;
        }
    }

    private String extractThumbnailByType(Map<String, Object> manifestNode, String typeKeyword) {
        if (manifestNode == null) return null;
        Map<String, Object> assertions = (Map<String, Object>) manifestNode.get("assertion_store");
        if (assertions == null) assertions = (Map<String, Object>) manifestNode.get("assertions");
        if (assertions == null) return null;

        for (Map.Entry<String, Object> entry : assertions.entrySet()) {
            String key = entry.getKey();
            if (key != null && key.contains(typeKeyword)) {
                Object value = entry.getValue();
                if (value instanceof Map) {
                    Map<String, Object> thumbMap = (Map<String, Object>) value;
                    if (thumbMap.get("data") != null) {
                        return (String) thumbMap.get("data"); 
                    }
                }
            }
        }
        return null;
    }
}