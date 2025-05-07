package org.alfresco.ai_framework;

import org.apache.commons.lang.StringUtils;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Utility class for common operations related to document processing
 * such as metadata assignment, file transformation, and file handling.
 */
public class Utils {
    /**
     * Default value used when metadata is unavailable.
     */
    public static final String UNKNOWN_METADATA = "unknown";

    /**
     * Adds metadata to each document.
     *
     * @param documents list of documents to update
     * @param documentId optional document ID to set
     * @param folderId optional folder ID to set
     * @param fileName optional folder ID to set
     */
    public static void addMetadata(List<Document> documents, String documentId, String folderId, String fileName) {
        documents.forEach(doc -> {
            if(StringUtils.isNotBlank(documentId)) doc.getMetadata().put("documentId", documentId);
            if(StringUtils.isNotBlank(folderId)) doc.getMetadata().put("folderId", folderId);
            if(StringUtils.isNotBlank(fileName)) doc.getMetadata().put("fileName", fileName);
        });
    }

    /**
     * Reads and transforms a document from the provided file resource.
     *
     * @param file
     * @return list of transformed documents
     */
    public static List<Document> transformDocument(Resource file) {
        List<Document> documentText = new TikaDocumentReader(file).get();
        return TokenTextSplitter.builder().build().apply(documentText);
    }

    /**
     * Creates a Resource from the MultipartFile.
     *
     * @param file
     * @return Resource representing the file input stream
     * @throws IOException if reading the file input stream fails
     */
    public static Resource createFileResource(MultipartFile file) throws IOException {
        return new InputStreamResource(file.getInputStream()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        };
    }
}
