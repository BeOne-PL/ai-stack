package org.alfresco.ai_framework.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.alfresco.ai_framework.Utils.createFileResource;

/**
 * REST controller for handling document ingestion requests, including upload and delete operations.
 */
@RestController
public class IngestionController {

    private static final Logger logger = LoggerFactory.getLogger(IngestionController.class);

    private final IngestionService ingestionService;

    public IngestionController(IngestionService ingestionService) {
        this.ingestionService = ingestionService;
    }

    /**
     * Uploads a document to the system, ingesting it by the provided document and folder IDs.
     *
     * @param documentId the unique ID of the document
     * @param folderId the ID of the folder containing the document
     * @param fileName the name of the file
     * @param file the uploaded file
     * @return 200 OK on success, 400/500 on failure
     */
    @PostMapping("/documents")
    public ResponseEntity<String> uploadDocument(
            @RequestParam("documentId") String documentId,
            @RequestParam("folderId") String folderId,
            @RequestParam("fileName") String fileName,
            @RequestParam("file") MultipartFile file
    ) {
        try {
            ingestionService.ingest(documentId, folderId, fileName, createFileResource(file));
            return ResponseEntity.ok("Document uploaded successfully with ID: " + documentId);
        } catch (IOException e) {
            return handleException("Failed to process file: ", e, HttpStatus.BAD_REQUEST);
        } catch (RuntimeException e) {
            return handleException("Failed to ingest document: ", e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Deletes a document by document ID.
     *
     * @param documentId the ID of the document to delete
     * @return 200 OK on success, 500 on failure
     */
    @DeleteMapping("/documents")
    public ResponseEntity<String> deleteDocument(@RequestParam("documentId") String documentId) {
        return deleteById(() -> ingestionService.deleteByDocumentId(documentId), "document", documentId);
    }

    /**
     * Deletes documents in a folder by folder ID.
     *
     * @param folderId the ID of the folder whose documents should be deleted
     * @return 200 OK on success, 500 on failure
     */
    @DeleteMapping("/folders")
    public ResponseEntity<String> deleteDocumentsByFolder(@RequestParam("folderId") String folderId) {
        return deleteById(() -> ingestionService.deleteByFolderId(folderId), "folder", folderId);
    }

    /**
     * Deletes entities by ID, encapsulating the common deletion logic.
     *
     * @param deleteAction the action to perform
     * @param entityType the type of entity (e.g., "document" or "folder")
     * @param id the identifier of the entity
     * @return 200 OK on success, 500 on failure
     */
    private ResponseEntity<String> deleteById(Runnable deleteAction, String entityType, String id) {
        try {
            deleteAction.run();
            return ResponseEntity.ok(entityType + " deleted successfully with ID: " + id);
        } catch (RuntimeException e) {
            return handleException("Failed to delete " + entityType + ": ", e, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Handles exceptions and creates a ResponseEntity with the given message and status.
     *
     * @param message error message prefix
     * @param e the exception thrown
     * @param status the HTTP status to return
     * @return error response entity
     */
    private ResponseEntity<String> handleException(String message, Exception e, HttpStatus status) {
        logger.error("Message: {}, status: {}", message, status, e);
        return ResponseEntity.status(status).body(message + e.getMessage());
    }
}
