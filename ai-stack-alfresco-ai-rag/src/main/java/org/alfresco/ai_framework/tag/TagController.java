package org.alfresco.ai_framework.tag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pl.beone.ai.models.response.TagAnalysisResponse;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.alfresco.ai_framework.Utils.*;

/**
 * REST controller exposing an endpoint for document tagging.
 * Accepts a file, extracts its content, and delegates the analysis to the AI tagging service.
 */
@Slf4j
@RestController
public class TagController {
    @Autowired
    private TagService tagService;

    /**
     * Handles a document tagging request by transforming the uploaded file,
     * extracting its textual content, and invoking the AI-based tagging pipeline.
     *
     * @param documentId unique identifier of the document
     * @param fileName original name of the uploaded file
     * @param file the file to be analyzed
     * @param candidateTags a predefined set of tags suggested for classification
     * @return a TagAnalysisResponse encapsulating the result of the AI-driven tagging operation
     */
    @PostMapping("/tags")
    public ResponseEntity<TagAnalysisResponse> tagDocument(
            @RequestParam("documentId") String documentId,
            @RequestParam("fileName") String fileName,
            @RequestParam("file") MultipartFile file,
            @RequestParam("candidateTags") List<String> candidateTags
    ) {
        try {
            log.info("[REQUEST /tag] Received request on /tag:" +
                    "\n     -documentId - " + documentId +
                    "\n     -fileName - " + fileName +
                    "\n     -candidateTags - " + candidateTags
            );
            List<Document> documents = transformDocument(createFileResource(file));
            String fileContent = documents.get(0).getFormattedContent();
            log.debug("[REQUEST /tag] fileContent: {}", fileContent);
            TagAnalysisResponse res = tagService.tag(fileName, fileContent, candidateTags);
            log.info("[REQUEST /tag] response: {}", res);
            return ResponseEntity.status(HttpStatus.OK).body(res);
        } catch (Exception e) {
            return handleTagException("Failed to tag document: ", e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles exceptions arising during the tagging process,
     * providing a standardized error response with diagnostic detail.
     *
     * @param message context-specific error message
     * @param e the originating exception
     * @param status the HTTP status to return
     * @return a response entity with error encapsulated in a TagAnalysisResponse
     */
    private ResponseEntity<TagAnalysisResponse> handleTagException(String message, Exception e, HttpStatus status) {
        log.error("[ErrorHandler TagController] Message: {}, status: {}", message, status, e);
        TagAnalysisResponse tagAnalysisResponse =
                new TagAnalysisResponse(
                        null,
                        null,
                        false,
                        message + e.getMessage());
        return ResponseEntity.status(status).body(tagAnalysisResponse);
    }

}
