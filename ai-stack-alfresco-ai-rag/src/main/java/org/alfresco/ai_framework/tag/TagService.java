package org.alfresco.ai_framework.tag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import pl.beone.ai.models.request.PipelineMessage;
import pl.beone.ai.models.request.PipelineRequest;
import pl.beone.ai.models.request.TagDocRequest;
import pl.beone.ai.models.response.TagAnalysisResponse;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Service for handling document tagging using an external AI pipeline.
 * Supports sending tagging requests, parsing responses, and applying thresholds.
 */
@Slf4j
@Service
public class TagService {
    @Value("${ai.pipeline.url:http://open-webui:8080}")
    private String aiPipelineUrl;
    @Value("${ai.pipeline.uri:/api/chat/completions}")
    private String aiPipelineUri;
    @Value("${ai.pipeline.tag.model:classificationPipe}")
    private String aiPipelineTagModel;
    @Value("${ai.pipeline.api.key}")
    private String aiPipelineApiKey;
    @Value("${ai.pipeline.publicly.allowed.threshold:60}")
    private String aiPipelinePubliclyAllowedThresholdString;
    private Double aiPipelinePubliclyAllowedThreshold;
    @Value("${ai.pipeline.taggable.threshold:90}")
    private String aiPipelineTaggableThresholdString;
    private Double aiPipelineTaggableThreshold;
    private RestTemplate restTemplate;
    private ObjectMapper objectMapper;

    /**
     * Initializes internal components and parses threshold values from properties.
     */
    @PostConstruct
    private void init() {
        objectMapper = new ObjectMapper();
        restTemplate = new RestTemplate();
        aiPipelinePubliclyAllowedThreshold =
                aiPipelinePubliclyAllowedThresholdString != null ?
                        Long.parseLong(aiPipelinePubliclyAllowedThresholdString)/100.0 : 0.5 ;
        aiPipelineTaggableThreshold =
                aiPipelineTaggableThresholdString != null ?
                        Long.parseLong(aiPipelineTaggableThresholdString)/100.0 : 0.5 ;
        log.debug("ThresholdString public: {} taggable: {}", aiPipelinePubliclyAllowedThresholdString, aiPipelineTaggableThresholdString);
        log.debug("Threshold public: {} taggable: {}", aiPipelinePubliclyAllowedThreshold, aiPipelineTaggableThreshold);
    }

    /**
     * Sends a raw tagging request to the external AI pipeline.
     *
     * @param requestBody the pipeline request to send
     * @return the raw JSON response string
     */
    public String sendTagRequest(PipelineRequest requestBody) {
        try {
            HttpHeaders aiPipelineTagHeaders = new HttpHeaders();
            aiPipelineTagHeaders.setContentType(MediaType.APPLICATION_JSON);
            if(StringUtils.isNotBlank(aiPipelineApiKey)) {
                aiPipelineTagHeaders.set("Authorization", "Bearer " + aiPipelineApiKey);
            }
            HttpEntity<String> req = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), aiPipelineTagHeaders);
            ResponseEntity<String> res = restTemplate.postForEntity(aiPipelineUrl + aiPipelineUri, req, String.class);
            return res.getBody();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends a document to the tagging pipeline and parses the response.
     *
     * @param fileName name of the document
     * @param fileContent text content of the document
     * @param candidateTags list of suggested tags
     * @return parsed TagAnalysisResponse
     * @throws Exception if tagging or parsing fails
     */
    public TagAnalysisResponse tag(String fileName, String fileContent, List<String> candidateTags) throws Exception {
        TagDocRequest tagDocRequest = new TagDocRequest(fileName, fileContent, candidateTags);
        PipelineMessage pipelineMessage = new PipelineMessage("user", objectMapper.writeValueAsString(tagDocRequest));
        PipelineRequest pipelineRequest = new PipelineRequest(false, aiPipelineTagModel, List.of(pipelineMessage));
        String tagPipelineResponse = sendTagRequest(pipelineRequest);
        return parseTagResponse(tagPipelineResponse);
    }

    /**
     * Parses the raw JSON returned from the tagging pipeline.
     * Extracts main tag, tag list based on score threshold, and public access status.
     *
     * @param rawJson raw response from AI pipeline
     * @return TagAnalysisResponse containing extracted data
     * @throws Exception if response format is invalid
     */
    public TagAnalysisResponse parseTagResponse(String rawJson) throws Exception {
        log.debug("Parsing tag response: {}", rawJson);
        JsonNode root = objectMapper.readTree(rawJson);

        String innerJsonString = root
                .path("choices")
                .get(0)
                .path("message")
                .path("content")
                .asText();
        JsonNode contentJson = objectMapper.readTree(innerJsonString).path("data");

        JsonNode classification = contentJson
                .path("classification");
        JsonNode mainTagNode = classification.path("labels");
        String mainTag = mainTagNode.get(0).asText();

        JsonNode classificationMulti = contentJson
                .path("classification_multi");
        JsonNode labelsNode = classificationMulti.path("labels");
        JsonNode scoresNode = classificationMulti.path("scores");

        double threshold = aiPipelineTaggableThreshold;
        List<String> tags = new ArrayList<>();
        Iterator<JsonNode> lblIt = labelsNode.elements();
        Iterator<JsonNode> scrIt = scoresNode.elements();
        while(lblIt.hasNext() && scrIt.hasNext()) {
            String label = lblIt.next().asText();
            double score = scrIt.next().asDouble();
            if (score > threshold) {
                log.debug("Score: {}  Threshold: {} Label: {}", score, threshold, label);
                tags.add(label);
            }
        }
        if(!tags.contains(mainTag)) tags.add(mainTag);

        JsonNode publicScores = contentJson
                .path("classification_public")
                .path("scores");
        boolean publiclyAllowed = publicScores.get(0).asDouble() >= aiPipelinePubliclyAllowedThreshold;

        String errorMsg = contentJson.path("error").isNull() ? null : contentJson.path("error").asText();

        TagAnalysisResponse tagAnalysisResponse = new TagAnalysisResponse(tags, mainTag, publiclyAllowed, errorMsg);
        log.debug("Parsed tag response: {}", tagAnalysisResponse);
        return tagAnalysisResponse;
    }

}
