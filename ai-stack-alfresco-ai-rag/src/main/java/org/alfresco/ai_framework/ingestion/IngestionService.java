package org.alfresco.ai_framework.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

import static org.alfresco.ai_framework.Utils.addMetadata;
import static org.alfresco.ai_framework.Utils.transformDocument;

/**
 * Service for ingesting documents into the vector store, utilizing document parsing and transformation.
 */
@Service
public class IngestionService {

    private static final Logger logger = LoggerFactory.getLogger(IngestionService.class);

    private final VectorStore vectorStore;

    /**
     * Constructs the IngestionService with the given vector store.
     *
     * @param vectorStore the vector store used for indexing and deletion
     */
    public IngestionService(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    /**
     * Ingests a document into the vector store by reading, transforming, and storing it.
     *
     * @param documentId unique document identifier
     * @param folderId ID of the folder containing the document
     * @param fileName name of the file
     * @param file resource to be ingested
     */
    public void ingest(String documentId, String folderId, String fileName, Resource file) {
        logger.info("Starting ingestion for document ID: {}, folder: {}", documentId, folderId);

        List<Document> documents = transformDocument(file);
        addMetadata(documents, documentId, folderId, fileName);

        List<Document> processedDocs = DocumentSplitter.splitLargeDocuments(documents);

        deleteByDocumentId(documentId);
        vectorStore.add(processedDocs);

        logger.info("Ingestion complete for document ID: {}", documentId);
    }

    /**
     * Deletes documents from the vector store matching the specified document ID.
     *
     * @param documentId the document ID to match for deletion
     */
    public void deleteByDocumentId(String documentId) {
        deleteDocuments("documentId", documentId);
    }

    /**
     * Deletes documents from the vector store matching the specified folder ID.
     *
     * @param folderId the folder ID to match for deletion
     */
    public void deleteByFolderId(String folderId) {
        deleteDocuments("folderId", folderId);
    }

    /**
     * Deletes documents from the vector store that match the specified metadata key and value.
     *
     * @param key metadata key to filter documents
     * @param value metadata value to match
     */
    private void deleteDocuments(String key, String value) {
        logger.info("Deleting documents with {}: {}", key, value);

        try {
            List<Document> documents = vectorStore.similaritySearch(
                    SearchRequest.builder().filterExpression("'" + key + "' == '" + value + "'").build()
            );

            if (!documents.isEmpty()) {
                vectorStore.delete(documents.stream().map(Document::getId).collect(Collectors.toList()));
                logger.info("Deleted {} document(s) with {}: {}", documents.size(), key, value);
            } else {
                logger.info("No documents found with {}: {}", key, value);
            }
        } catch (RuntimeException e) {
            logger.error("Error deleting documents with {}: {}", key, value, e);
        }
    }
}
