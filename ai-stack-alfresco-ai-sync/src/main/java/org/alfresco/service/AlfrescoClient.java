package org.alfresco.service;

import lombok.extern.slf4j.Slf4j;
import org.alfresco.ai.AIClient;
import org.alfresco.core.handler.NodesApi;
import org.alfresco.core.handler.TagsApi;
import org.alfresco.core.model.*;
import org.alfresco.core.model.Node;
import org.alfresco.model.AIStackException;
import org.alfresco.model.NodeSearchResult;
import org.alfresco.search.handler.SearchApi;
import org.alfresco.search.model.*;
import org.alfresco.search.model.Pagination;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.apache.commons.lang.StringUtils;
import pl.beone.ai.models.response.TagAnalysisResponse;

import java.io.IOException;
import java.io.InputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Service responsible for synchronizing documents between Alfresco and an AI service.
 * Handles initial document synchronization and subsequent event processing.
 */
@Slf4j
@Service
public class AlfrescoClient {

    private static final String PATH_QUERY_TEMPLATE =
            "ANCESTOR:\"workspace://SpacesStore/%s\" AND TYPE:\"cm:content\" AND cm:modified:[%s TO *]";
    private static final String FIELD_MODIFIED = "cm:modified";

    private static final String ROOT_PATH = "Company Home";

    @Value("${alfresco.ai.sync.maxItems}")
    private int maxItems;

    @Value("${alfresco.ai.sync.aspect}")
    private String syncAspect;

    @Value("${alfresco.ai.sync.aspect.published}")
    private String propPublished;

    @Value("${alfresco.ai.sync.aspect.updated}")
    private String propUpdated;

    @Value("${content.service.url}")
    private String baseUrl;

    @Value("${content.service.security.basicAuth.username}")
    private String username;

    @Value("${content.service.security.basicAuth.password}")
    private String password;

    @Value("${alfresco.ai.customizations.initial.knowledge:Company Home|Knowledge Base}")
    public String initialKnowledgeFolder;
    @Value("${alfresco.ai.customizations.initial.knowledge:Company Home|Knowledge Pipeline}")
    public String initialKnowledgePipelineFolder;
    @Value("${alfresco.ai.customizations.initial.knowledge:Company Home|Knowledge Pipeline|Start}")
    public String initialKnowledgePipelineStartFolder;
    @Value("${alfresco.ai.customizations.initial.knowledge:Company Home|Knowledge Pipeline|Retry}")
    public String initialKnowledgePipelineRetryFolder;
    @Value("${alfresco.ai.customizations.initial.knowledge.list.defaults:true}")
    private Boolean initialKnowledgeFoldersDefaults;
    @Value("${alfresco.ai.customizations.initial.knowledge.list.string:}")
    private String initialKnowledgeFoldersListString;
    public List<String> initialKnowledgeFoldersList;
    @Value("${alfresco.ai.customizations.pipeline.aspect:cm:generalclassifiable}")
    private String pipelineAspect;
    @Value("${alfresco.ai.customizations.pipeline.defaults.tag:taggedByAI}")
    private String aiPipelineDefaultTag;

    @Autowired
    private SearchApi searchApi;

    @Autowired
    private NodesApi nodesApi;

    @Autowired
    private AIClient aiClient;

    @Autowired
    private Utils utils;

    @Autowired
    private TagsApi tagsApi;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * Initializes the knowledge folder structure in the Alfresco repository.
     * This includes:
     * <ul>
     *   <li>Creating and indexing the Knowledge Base and Pipeline root folders</li>
     *   <li>Applying sync and pipeline-specific aspects to respective folders</li>
     *   <li>Creating default domain-specific subfolders (e.g. "Notatka", "Umowa") if configured</li>
     * </ul>
     * This method is typically invoked once during system startup to ensure all required
     * knowledge spaces exist and are properly prepared for ingestion and classification.
     */
    public void setupInitialKnowledgeFolders() {
        // Knowledge Base
        ensureFolderCreation(initialKnowledgeFolder);
        waitForFolderToBeIndexed(initialKnowledgeFolder,
                Duration.ofSeconds(20), Duration.ofSeconds(1));
        setupFolderRule(initialKnowledgeFolder, syncAspect);
        // Knowledge Pipeline
        ensureFolderCreation(initialKnowledgePipelineFolder);
        waitForFolderToBeIndexed(initialKnowledgePipelineFolder,
                Duration.ofSeconds(20), Duration.ofSeconds(1));
        setupFolderRule(initialKnowledgePipelineFolder, pipelineAspect);
        // Start
        ensureFolderCreation(initialKnowledgePipelineStartFolder);
        waitForFolderToBeIndexed(initialKnowledgePipelineStartFolder,
                Duration.ofSeconds(20), Duration.ofSeconds(1));
        // Retry
        ensureFolderCreation(initialKnowledgePipelineRetryFolder);
        waitForFolderToBeIndexed(initialKnowledgePipelineRetryFolder,
                Duration.ofSeconds(20), Duration.ofSeconds(1));
        initialKnowledgeFoldersList = new ArrayList<>();
        if (initialKnowledgeFoldersListString != null && !initialKnowledgeFoldersListString.isBlank()) {
            initialKnowledgeFoldersList = Arrays.stream(initialKnowledgeFoldersListString.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } else if (Boolean.TRUE.equals(initialKnowledgeFoldersDefaults)) {
            String basePath = initialKnowledgeFolder + "|";
            initialKnowledgeFoldersList = List.of(
                    basePath + "Notatka",
                    basePath + "Dokumentacja",
                    basePath + "Umowa",
                    basePath + "Aneks",
                    basePath + "Wniosek",
                    basePath + "Oferta",
                    basePath + "Zamówienie",
                    basePath + "Raport",
                    basePath + "Regulamin"
            );
            log.info("Using default knowledge folder list: {}", initialKnowledgeFoldersList);
        } else {
            log.warn("No knowledge folder list defined and defaults disabled.");
        }
        if(!initialKnowledgeFoldersList.isEmpty()) {
            initialKnowledgeFoldersList.forEach(folder -> {
                ensureFolderCreation(folder);
                waitForFolderToBeIndexed(folder,
                        Duration.ofSeconds(20), Duration.ofSeconds(1));
            });
        }
    }

    /**
     * Checks whether a folder exists at the given path.
     *
     * @param path the full logical path to the folder, using pipe-separated segments (e.g., "Company Home|FolderA|SubfolderB")
     * @return a {@link NodeSearchResult} indicating whether the folder exists, and containing its node ID if it does
     */
    public NodeSearchResult getNodeId(String path) {
        if (!path.startsWith(ROOT_PATH)) {
            throw new AIStackException("[Checking Node Id] Path must start with " + ROOT_PATH + " but was: " + path);
        }
        if (path == null || path.isBlank()) return new NodeSearchResult();
        List<String> segments = new ArrayList<>(List.of(path.split("\\|")));
        segments.remove(0);
        String parentId = "-root-";
        for (String segment : segments){
            NodeChildAssociationPagingList children = listFolderContent(parentId, null);
            Optional<NodeChildAssociation> existing =
                    children.getEntries().stream()
                            .map(NodeChildAssociationEntry::getEntry)
                            .filter(n -> segment.equals(n.getName()))
                            .findFirst();
            if (existing.isPresent()) {
                parentId = existing.get().getId();
            }
            else return new NodeSearchResult();
        }
        log.info("Node in given path: (" + path + ") exists.");
        return new NodeSearchResult(true, parentId);
    }

    /**
     * Recursively creates all missing folders along a logical folder path in the Alfresco repository.
     * <p>
     * The method starts at the root folder ("Company Home") and traverses each segment
     * in the path separated by {@code |}. If a segment (subfolder) is missing, it will be created.
     * Already existing folders are reused.
     * <p>
     * Example path: {@code "Company Home|Projects|Demo"}
     *
     * @param path the full logical folder path, starting with {@code Company Home}
     * @throws AIStackException if the path is invalid or folder creation fails
     */
    public void createFolder(String path) {
        if (!path.startsWith(ROOT_PATH)) {
            throw new AIStackException("[Creating folder recursively] Path must start with " + ROOT_PATH + " but was: " + path);
        }

        NodeSearchResult rootResult = getNodeId(ROOT_PATH);
        if (!rootResult.isExists()) {
            throw new AIStackException("[Creating folder] ROOT_PATH not found: " + ROOT_PATH);
        }
        String parentId = rootResult.getNodeId();
        String[] segments = path.substring(ROOT_PATH.length()).split("\\|");
        StringBuilder currentPath = new StringBuilder(ROOT_PATH);

        for (String segment : segments) {

            if (segment == null || segment.isBlank()) {
                continue;
            }
            currentPath.append("|").append(segment);

            // Check if folder already exists
            NodeSearchResult nodeSearchResult = getNodeId(currentPath.toString());
            if(nodeSearchResult.isExists()) {
                parentId = nodeSearchResult.getNodeId();
                continue;
            }

            // Folder does not exist, create it
            String folderName = utils.decodeQNameSegment(segment);

            NodeBodyCreate folder = new NodeBodyCreate()
                    .name(folderName)
                    .nodeType("cm:folder");
            try {
                NodeEntry result = nodesApi
                        .createNode(
                                parentId,       // Parent node ID
                                folder,         // Node body
                                true,           // autoRename
                                null,           // majorVersion
                                null,           // versioningEnabled
                                null,           // include
                                null            // fields
                        ).getBody();

                if (result == null) {
                    throw new AIStackException("[Creating folder] The response was null for path: " + currentPath);
                }

                String newId = result.getEntry().getId();
                log.info("Created folder ID: {} for path: {}", newId, currentPath);
                parentId = newId;
            } catch (Exception e) {
                throw new AIStackException("[Creating folder] Failed to create folder at path: " + currentPath, e);
            }
        }
    }

    /**
     * Ensures that the folder structure for the given Lucene (QName) path exists.
     * <p>
     * If the folder does not exist, it will be created recursively from /app:company_home.
     *
     * @param path the full Lucene (QName) path to the folder,
     *             e.g. /app:company_home/cm:Knowledge_x0020_Base/cm:RAG
     */
    public void ensureFolderCreation(String path) {
        if (!getNodeId(path).isExists()) createFolder(path);
    }

    /**
     * Applies a folder rule by invoking a Web Script for the specified logical folder path.
     * <p>
     * This rule typically attaches a designated aspect to the folder to enable synchronization or classification logic.
     * The rule is created by calling an Alfresco Web Script endpoint with the folder ID and provided aspect ID.
     * </p>
     *
     * @param path Logical folder path (e.g. {@code Company Home|Knowledge Base})
     * @param aspectId ID of the aspect to associate with the folder (e.g. {@code cm:generalclassifiable})
     * @throws AIStackException if the folder does not exist or rule setup fails
     */
    public void setupFolderRule(String path, String aspectId) {
        NodeSearchResult result = getNodeId(path);
        if (!result.isExists()) {
            throw new AIStackException("[Setting up AI Sync folder rule] Target folder does not exist: " + path);
        }
        String folderNodeId = result.getNodeId();
        invokeSetupRuleWebscript(folderNodeId, aspectId);
    }

    /**
     * Invokes a Web Script in Alfresco to apply a synchronization rule on a folder.
     *
     * @param nodeId ID of the target folder node
     * @param aspectId ID of the aspect to apply via the Web Script; falls back to default if null or blank
     */
    public void invokeSetupRuleWebscript(String nodeId, String aspectId) {
        if(StringUtils.isBlank(aspectId)) aspectId = syncAspect;
        String webScriptPath = "/alfresco/service/api/ai/setupFolderRule?nodeId=" + nodeId + "&aspectId=" + aspectId;
        String fullUrl = baseUrl + webScriptPath;

        HttpHeaders headers = new HttpHeaders();
        headers.setBasicAuth(username, password);

        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                fullUrl,
                HttpMethod.POST,
                entity,
                String.class
        );

        if (response.getStatusCode().is2xxSuccessful()) {
            log.info("Web Script executed: " + response.getBody());
        } else {
            throw new RuntimeException("Failed to execute Web Script: " + response.getStatusCode());
        }
    }

    /**
     * Waits until a folder at the specified path is indexed and searchable via Alfresco Search API.
     * <p>
     * This method repeatedly checks for the folder's existence using the search service, until either:
     * <ul>
     *     <li>The folder becomes searchable</li>
     *     <li>The timeout is reached</li>
     * </ul>
     *
     * @param path   the full pipe-separated path to the folder (e.g., "Company Home|FolderA|SubfolderB")
     * @param timeout      maximum duration to wait for indexing to complete
     * @param pollInterval how often to retry checking for indexing
     * @throws AIStackException if indexing doesn't complete within the timeout or the thread is interrupted
     */
    public void waitForFolderToBeIndexed(String path, Duration timeout, Duration pollInterval) {
        Instant deadline = Instant.now().plus(timeout);

        while (Instant.now().isBefore(deadline)) {
            NodeSearchResult result = getNodeId(path);
            if (result.isExists()) {
                log.info("[Polling folder indexing] Folder indexed and searchable at path: {}", path);
                return;
            }

            try {
                // TODO: change busy-waiting
                Thread.sleep(pollInterval.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AIStackException("Thread interrupted while waiting for indexing", e);
            }
        }
        throw new AIStackException("Timeout while waiting for folder indexing: " + path);
    }

    /**
     * Retrieves a list of folder IDs that are marked for synchronization.
     *
     * @param aspect the qualified name of the aspect to filter folders by (e.g., {@code cm:generalclassifiable});
     *               if {@code null} or blank, the default sync aspect is applied
     * @return a list of folder node IDs matching the specified aspect
     */
    public List<String> getSyncFolders(String aspect) {
        if(StringUtils.isBlank(aspect)) aspect = syncAspect;
        SearchRequest request = new SearchRequest()
                .query(new RequestQuery()
                        .language(RequestQuery.LanguageEnum.AFTS)
                        .query("ASPECT:\"" + aspect + "\" AND TYPE:\"cm:folder\""));
        return searchApi.search(request).getBody().getList().getEntries().stream()
                .map(ResultSetRowEntry::getEntry)
                .map(ResultNode::getId)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves a mapping of tag names to folder node IDs for folders that possess the specified aspect.
     *
     * @param aspect the qualified name of the aspect used to identify taggable folders (e.g., {@code cm:generalclassifiable});
     *               if {@code null} or blank, the default synchronization aspect is applied
     * @return a map where keys are folder names and values are their corresponding node IDs
     */
    public Map<String, String> getDocTags(String aspect) {
        if(StringUtils.isBlank(aspect)) aspect = syncAspect;
        SearchRequest request = new SearchRequest()
                .query(new RequestQuery()
                        .language(RequestQuery.LanguageEnum.AFTS)
                        .query("ASPECT:\"" + aspect + "\" AND TYPE:\"cm:folder\""));
        return searchApi.search(request).getBody().getList().getEntries().stream()
                .map(ResultSetRowEntry::getEntry)
                .filter(entry -> !entry.getName().matches("^\\d+$")) // Skip fully numeric names
                .collect(Collectors.toMap(
                        ResultNode::getName,        // Folder node name is Key - unique
                        ResultNode::getId,          // ID is as value
                        (first, second) -> first    // Choose first one
                ));
    }

    /**
     * Retrieves a list of folders that need to be synchronized based on latest document updated.
     *
     * @return List of folders that need synchronization
     */
    public List<AlfrescoSyncFolder> getFoldersToSync() {
        RequestInclude include = new RequestInclude();
        include.add("properties");

        SearchRequest baseRequest = new SearchRequest()
                .query(new RequestQuery()
                        .language(RequestQuery.LanguageEnum.AFTS)
                        .query("ASPECT:\"" + syncAspect + "\" AND TYPE:\"cm:folder\""))
                .include(include);

        List<ResultSetRowEntry> folders = searchApi.search(baseRequest).getBody().getList().getEntries();
        List<AlfrescoSyncFolder> syncFolders = new ArrayList<>();

        RequestSortDefinition sort = new RequestSortDefinition();
        sort.add(new RequestSortDefinitionInner()
                .type(RequestSortDefinitionInner.TypeEnum.FIELD)
                .field("cm:modified")
                .ascending(false));

        for (ResultSetRowEntry folder : folders) {
            SearchRequest folderRequest = new SearchRequest()
                    .query(new RequestQuery()
                            .language(RequestQuery.LanguageEnum.AFTS)
                            .query("ANCESTOR:\"workspace://SpacesStore/" + folder.getEntry().getId() + "\" AND TYPE:\"cm:content\""))
                    .sort(sort)
                    .paging(new RequestPagination().maxItems(1))
                    .include(include);

            List<ResultSetRowEntry> documents = searchApi.search(folderRequest).getBody().getList().getEntries();

            if (!documents.isEmpty()) {
                OffsetDateTime published = getDateTime(folder, propPublished);
                OffsetDateTime updated = getDateTime(folder, propUpdated);
                OffsetDateTime modified = documents.get(0).getEntry().getModifiedAt();

                if (updated != null && updated.isBefore(modified)) {
                    syncFolders.add(new AlfrescoSyncFolder(folder.getEntry().getId(), published, updated, modified));
                }
            }
        }
        return syncFolders;
    }

    /**
     * Extracts a date-time value from the folder entry properties.
     *
     * @param entry   Folder entry to extract the date-time from
     * @param property Property key for the date-time value
     * @return OffsetDateTime value if present, otherwise null
     */
    private static OffsetDateTime getDateTime(ResultSetRowEntry entry, String property) {
        return Optional.ofNullable(entry.getEntry().getProperties())
                .map(props -> (String) ((Map<?, ?>) props).get(property))
                .map(AlfrescoClient::parseDateTime)
                .orElse(null);
    }

    /**
     * Parses a string date-time representation to an OffsetDateTime object.
     *
     * @param dateTimeString The string representation of the date-time
     * @return Parsed OffsetDateTime object
     */
    private static OffsetDateTime parseDateTime(String dateTimeString) {
        return OffsetDateTime.parse(dateTimeString, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX"));
    }

    /**
     * Synchronizes documents in the given folder, processing them in batches.
     *
     * @param processedCount Atomic integer to keep track of processed documents
     * @param folder         The folder to synchronize
     */
    public void synchronizeDocuments(AtomicInteger processedCount, AlfrescoSyncFolder folder) {
        RequestSortDefinition sortDefinition = createSortDefinition();
        boolean hasMoreItems;

        do {
            ResultSetPaging results = fetchAndProcessBatch(sortDefinition, processedCount, folder);
            hasMoreItems = Optional.ofNullable(results)
                    .map(ResultSetPaging::getList)
                    .map(ResultSetPagingList::getPagination)
                    .map(Pagination::isHasMoreItems)
                    .orElse(false);

            log.debug("Batch processing complete. More items available: {}", hasMoreItems);
        } while (hasMoreItems);
    }

    /**
     * Fetches a batch of documents to process and handles their processing.
     *
     * @param sortDefinition Sort definition to apply during the fetch
     * @param processedCount Atomic integer to track processed documents
     * @param folder         Folder to synchronize
     * @return ResultSetPaging containing the fetched documents
     */
    private ResultSetPaging fetchAndProcessBatch(RequestSortDefinition sortDefinition, AtomicInteger processedCount, AlfrescoSyncFolder folder) {
        log.debug("Fetching batch of documents (max: {})", maxItems);

        ResponseEntity<ResultSetPaging> searchResponse = executeSearch(sortDefinition, folder);
        List<ResultSetRowEntry> entries = searchResponse.getBody().getList().getEntries();

        processDocumentBatch(entries, folder, processedCount);

        return searchResponse.getBody();
    }

    /**
     * Processes a batch of documents in parallel.
     *
     * @param entries        Documents to process
     * @param folder         Folder to synchronize
     * @param processedCount Counter for processed documents
     */
    private void processDocumentBatch(List<ResultSetRowEntry> entries, AlfrescoSyncFolder folder, AtomicInteger processedCount) {
        entries.parallelStream().forEach(entry -> {
            String uuid = entry.getEntry().getId();
            String name = entry.getEntry().getName();

            try {
                processDocument(uuid, folder.id(), name);
                processedCount.incrementAndGet();
                log.debug("Processed document: {} ({})", name, uuid);
            } catch (Exception e) {
                log.error("Failed to process document: {} ({})", name, uuid, e);
            }
        });
    }

    /**
     * Processes a single document by fetching its content and uploading it to the AI service.
     *
     * @param uuid Document identifier
     * @param syncFolderId Synchronization folder id
     * @param documentName Document name
     * @throws IOException If processing fails
     */
    public void processDocument(String uuid, String syncFolderId, String documentName) throws IOException {
        try (InputStream content = nodesApi.getNodeContent(uuid, true, null, null)
                .getBody()
                .getInputStream()) {

            String response = aiClient.uploadDocument(uuid, syncFolderId, documentName, content);
            log.debug("Document uploaded: {} - Response: {}", documentName, response);
        }
    }

    /**
     * Sends a document to the AI service for tagging, using provided candidate tags.
     *
     * @param uuid the document identifier
     * @param documentName the document name
     * @param candidateTags list of candidate tags to evaluate
     * @return the tagging result from the AI service
     * @throws IOException if content retrieval or tagging fails
     */
    public TagAnalysisResponse tagDocument(String uuid, String documentName, List<String> candidateTags) throws IOException {
        try (InputStream content = nodesApi.getNodeContent(uuid, true, null, null)
                .getBody()
                .getInputStream()) {
            TagAnalysisResponse response = aiClient.tagDocument(uuid, documentName, content, candidateTags);
            log.debug("Document tagged: {} - Response: {}", documentName, response);
            return  response;
        }
    }

    /**
     * Applies a list of tags to a document node.
     * Adds the default AI tag if it is not already present.
     *
     * @param nodeId the ID of the node to tag
     * @param documentTags the list of tags to apply
     */
    public void tagDocument(String nodeId, List<String> documentTags) {
        documentTags.forEach(tagName -> {
            tagDocument(nodeId, tagName);
        });
        if(!StringUtils.isNotBlank(aiPipelineDefaultTag)) {
            tagDocument(nodeId, aiPipelineDefaultTag);
        }
    }

    /**
     * Applies a single tag to the specified node.
     *
     * @param nodeId the ID of the node
     * @param tagName the tag to apply
     */
    public void tagDocument(String nodeId, String tagName) {
        TagBody tagBody = new TagBody();
        tagBody.setTag(tagName);
        Tag tag = Objects.requireNonNull(tagsApi.createTagForNode(nodeId, tagBody, null).getBody()).getEntry();
        log.debug("Created Tag {} for document {}", tag, nodeId);
    }

    /**
     * Updates a node's permissions to reflect whether it is publicly accessible.
     * Removes existing GROUP_EVERYONE entries before conditionally re-adding with 'Consumer' role.
     *
     * @param nodeId the ID of the node
     * @param isPubliclyAvailable whether the document should be publicly readable
     */
    public void classifyDocument(String nodeId, boolean isPubliclyAvailable) {
        NodeEntry entry = nodesApi.getNode(nodeId, List.of("permissions"), null, null).getBody();
        PermissionsInfo current = entry.getEntry().getPermissions();
        PermissionsBody body = new PermissionsBody();
        body.setIsInheritanceEnabled(false);

        List<PermissionElement> newPermissionSet = new ArrayList<>();
        // Remove group everyone permissions but propagate old
        if(current.getLocallySet() != null) {
            current.getLocallySet().forEach(permissionElement -> {
                if(!"GROUP_EVERYONE".equals(permissionElement.getName())) {
                    newPermissionSet.add(permissionElement);
                }
            });
        }
        body.setLocallySet(newPermissionSet);
        if(isPubliclyAvailable) {
            body.addLocallySetItem(new PermissionElement()
                    .authorityId("GROUP_EVERYONE")
                    .name("Consumer")
                    .accessStatus(PermissionElement.AccessStatusEnum.ALLOWED)
            );
        }

        NodeBodyUpdate update = new NodeBodyUpdate().permissions(body);
        NodeEntry result = nodesApi.updateNode(nodeId, update, List.of("permissions"), null).getBody();
        log.debug("Classify document result: {} Publicly available: {} NodeId: {}", result, isPubliclyAvailable, nodeId);
    }

    /**
     * Retrieves the list of document nodes (files) directly contained in the specified folder.
     * <p>
     * This method queries the given folder node and returns only its immediate children that are files,
     * excluding any subfolders or nested structures.
     *
     * @param parentId the node ID of the folder whose document children should be retrieved
     * @return a list of {@link NodeChildAssociation} objects representing document nodes within the folder
     */
    public List<NodeChildAssociation> listFolderDocuments(String parentId) {
        NodeChildAssociationPagingList children = listFolderContent(parentId, null, false, true);
        return children.getEntries().stream()
                .map(NodeChildAssociationEntry::getEntry)
                .collect(Collectors.toList());
    }

    /**
     * Retrieves the child folders of a specified node, optionally scoped by a relative path.
     *
     * @param rootNodeId the ID of the root node to query
     * @param relativeFolderPath an optional relative path from the root node
     * @return a {@link NodeChildAssociationPagingList} containing only folder-type child nodes
     */
    private NodeChildAssociationPagingList listFolderContent(String rootNodeId, String relativeFolderPath) {
        return listFolderContent(rootNodeId, relativeFolderPath, true, false);
    }

    /**
     *  Lists the children of a given folder node, optionally filtered to include only folders or only files.
     *
     * @param rootNodeId the id of the folder node that is the root. If relativeFolderPath is null, then content in this folder will be listed. Besides node ID the aliases -my-, -root- and -shared- are also supported.
     * @param relativeFolderPath path relative rootNodeId, if this is not null, then the content of this folder will be listed
     * @param isFolder if {@code true}, only returns folders; overrides {@code isFile} if both are true
     * @param isFile if {@code true}, only returns files; ignored if {@code isFolder} is {@code true}
     * @return a list of child folder node objects contained in the folder, or null if not found
     *
     * @see <a href="https://support.hyland.com/r/Alfresco/Alfresco-Content-Services/23.4/Alfresco-Content-Services/Develop/Out-of-Process-Extension-Points/REST-API-Java-Wrapper/Managing-Folders-and-Files/Filtering-Contents-of-a-Folder">Alfresco Developer Docs</a>
     */
    private NodeChildAssociationPagingList listFolderContent(String rootNodeId, String relativeFolderPath, boolean isFolder, boolean isFile) {
        Integer skipCount = 0;
        Integer maxItems = 100;
        List<String> include = null;
        List<String> fields = null;
        List<String> orderBy = null;
        String where = isFolder ? "(isFolder=true)" : null;
        where = isFile ? "(isFile=true)" : where;
        Boolean includeSource = false;

        log.debug("Listing folder {} {} with filter {}", rootNodeId, relativeFolderPath, where);
        NodeChildAssociationPagingList result = nodesApi.listNodeChildren(rootNodeId, skipCount, maxItems, orderBy, where, include,
                relativeFolderPath, includeSource, fields).getBody().getList();
        for (NodeChildAssociationEntry childNodeAssoc: result.getEntries()) {
            log.debug("Found folder node [name=" + childNodeAssoc.getEntry().getName() + "]");
        }

        return result;
    }

    /**
     * Ensures that a date-based folder hierarchy (year/month/day) exists under the specified parent folder.
     *
     * @param mainFolderId the node ID of the parent folder under which the date-based hierarchy should be created
     * @return the node ID of the innermost (day-level) folder corresponding to today’s date
     */
    public String ensureCurrentDateInnerFolders(String mainFolderId) {
        LocalDate today = LocalDate.now();
        String yearName  = String.format("%04d", today.getYear());
        String monthName = String.format("%02d", today.getMonthValue());
        String dayName   = String.format("%02d", today.getDayOfMonth());

        String parentId = mainFolderId;
        for (String segment : Arrays.asList(yearName, monthName, dayName)) {
            NodeChildAssociationPagingList children = listFolderContent(parentId, null);
            Optional<NodeChildAssociation> existing =
                    children.getEntries().stream()
                            .map(NodeChildAssociationEntry::getEntry)
                            .filter(n -> segment.equals(n.getName()))
                            .findFirst();

            if (existing.isPresent()) {
                parentId = existing.get().getId();
            } else {
                NodeBodyCreate folder = new NodeBodyCreate()
                        .name(segment)
                        .nodeType("cm:folder");
                try {
                    NodeEntry result = nodesApi
                            .createNode(
                                    parentId,       // Parent node ID
                                    folder,         // Node body
                                    true,           // autoRename
                                    null,           // majorVersion
                                    null,           // versioningEnabled
                                    null,           // include
                                    null            // fields
                            ).getBody();

                    if (result == null) {
                        throw new AIStackException("[Creating folder] The response was null for NodeBodyCreate: " + folder);
                    }

                    String newId = result.getEntry().getId();
                    log.info("Created folder '{}' under parent {}", segment, parentId);
                    parentId = newId;
                } catch (Exception e) {
                    throw new AIStackException("[Creating folder] Failed to create folder. NodeBodyCreate: " + folder, e);
                }
            }
        }
        return parentId;
    }

    /**
     * Moves a document to a dated subfolder structure under the target folder and renames it.
     *
     * @param nodeId the ID of the document to move
     * @param mainFolderPathId the ID of the root folder under which date folders will be created
     * @param timestampedName the new name for the document, with timestamp embedded
     */
    public void moveDocument(String nodeId, String mainFolderPathId, String timestampedName) {
        String destinationFolderId = ensureCurrentDateInnerFolders(mainFolderPathId);
        Node node = Objects.requireNonNull(nodesApi.getNode(nodeId, List.of("name"),
                null, null).getBody()).getEntry();
        String originalNodeName = node.getName();
        NodeBodyMove nodeBodyMove = new NodeBodyMove()
                .name(timestampedName)
                .targetParentId(destinationFolderId);
        nodesApi.moveNode(nodeId, nodeBodyMove, null, null);
        log.debug("[Moving document] Moved node= {} to destination= {}.",
                nodeId, destinationFolderId);
        updateNodeTitle(nodeId, originalNodeName);
    }

    /**
     * Updates the cm:description property of a node.
     *
     * @param nodeId the ID of the node to update
     * @param finalNodeDescription the new description value
     */
    public void updateNodeDescription(String nodeId, String finalNodeDescription) {
        NodeBodyUpdate body = new NodeBodyUpdate()
                .properties(Map.of("cm:description", finalNodeDescription));
        nodesApi.updateNode(nodeId, body, null, null);
        log.debug("[Updating description] Updated description on node= {} to= {}.",
                nodeId, finalNodeDescription);
    }

    /**
     * Appends a timestamp to the given filename, replacing any existing timestamp pattern if present.
     * <p>
     * The timestamp follows the format {@code _AI_TS_yyyyMMddHHmmss_AI_TS_} and is inserted before the file extension.
     * </p>
     *
     * @param baseName the original filename
     * @return the filename with a fresh timestamp inserted or updated
     */
    public String appendMarkTimestamp(String baseName) {
        baseName = baseName.replaceAll("_AI_TS_\\d{14}_AI_TS_", "");
        int idx = baseName.lastIndexOf('.');
        String name = (idx>0 ? baseName.substring(0, idx) : baseName);
        String ext  = (idx>0 ? baseName.substring(idx)     : "");
        String ts = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        return name + "_AI_TS_" + ts + "_AI_TS_" + ext;
    }

    /**
     * Updates the {@code cm:title} property of the specified node by deriving it from the provided filename.
     * <p>
     * If the filename contains an extension, only the base name (excluding extension) is used as the new title.
     * </p>
     *
     * @param nodeId the unique identifier of the node to be updated
     * @param nodeNameWithExt the name of the node including its extension
     */
    public void updateNodeTitle(String nodeId, String nodeNameWithExt) {
        int idx = nodeNameWithExt.lastIndexOf('.');
        String finalNodeTitle = (idx>0 ? nodeNameWithExt.substring(0, idx) : nodeNameWithExt);
        NodeBodyUpdate body = new NodeBodyUpdate()
                .properties(Map.of("cm:title", finalNodeTitle));
        nodesApi.updateNode(nodeId, body, null, null);
        log.debug("[Updating title] Updated title on node= {} to= {}.",
                nodeId, finalNodeTitle);
    }

    /**
     * Creates the sort definition used for sorting document queries.
     *
     * @return A RequestSortDefinition configured for sorting by modification date
     */
    private RequestSortDefinition createSortDefinition() {
        RequestSortDefinition sortDefinition = new RequestSortDefinition();
        sortDefinition.add(new RequestSortDefinitionInner()
                .type(RequestSortDefinitionInner.TypeEnum.FIELD)
                .field(FIELD_MODIFIED)
                .ascending(true));
        return sortDefinition;
    }

    /**
     * Executes a search query to fetch documents for synchronization.
     *
     * @param sortDefinition Sort definition to apply during the search
     * @param folder         Folder to search within
     * @return ResponseEntity containing the search results
     */
    private ResponseEntity<ResultSetPaging> executeSearch(RequestSortDefinition sortDefinition, AlfrescoSyncFolder folder) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSX");

        SearchRequest request = new SearchRequest()
                .query(new RequestQuery()
                        .language(RequestQuery.LanguageEnum.AFTS)
                        .query(String.format(PATH_QUERY_TEMPLATE, folder.id(), folder.updatedDate().format(formatter))))
                .sort(sortDefinition)
                .paging(new RequestPagination().maxItems(maxItems).skipCount(0));

        return searchApi.search(request);
    }

    /**
     * Updates the modification time of a folder. Optionally updates the published time.
     *
     * @param folder   The folder to update
     * @param published If true, the published time is also updated
     */
    public void updateTime(String folder, boolean published) {
        String currentTime = Instant.now()
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, Object> properties = new HashMap<>(Map.of(propUpdated, currentTime));
        if (published) {
            properties.put(propPublished, currentTime);
        }

        nodesApi.updateNode(folder, new NodeBodyUpdate().properties(properties), null, null);
    }

}
