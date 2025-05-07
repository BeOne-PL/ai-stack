package org.alfresco.ai_framework.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import reactor.core.publisher.Flux;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for handling chat requests. Exposes a single endpoint for
 * processing queries and returning AI-driven responses along with relevant
 * document metadata.
 */
@Slf4j
@RestController
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Endpoint to handle chat requests. Accepts a query as input, processes it through the
     * ChatService, and returns a structured response containing the answer and any retrieved
     * document metadata.
     *
     * @param query The chat query string from the user.
     * @return ChatResponseDTO containing the AI's answer and metadata of retrieved documents.
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponseDTO> chat(@RequestBody String query) {
        long start = System.currentTimeMillis();
        ChatResponse response = chatService.chat(query);

        if (response == null || response.getResult() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ChatResponseDTO("Failed to retrieve response from chat service.", Collections.emptyList()));
        }

        // Extract answer content and associated document metadata
        String answer = response.getResult().getOutput().getText();
        List<Map<String, Object>> documentMetadata = extractDocumentMetadata(response);

        log.info("[Response /chat] Response from AI: {}, User query: {}. Interaction time: {}.",
                answer, query, start - System.currentTimeMillis());
        return ResponseEntity.ok(new ChatResponseDTO(answer, documentMetadata));
    }

    /**
     * Endpoint to handle streaming chat responses. Accepts a query string and
     * returns a reactive stream of partial responses from the AI.
     *
     * @param query The chat query string from the user.
     * @return A Flux stream of strings representing incremental AI responses.
     */
    @PostMapping("/chat/stream")
    public Flux<String> streamChat(@RequestBody String query) {
        log.info("[Request /streamingChat] User query: {}.", query);
        return chatService.streamChat(query);
    }

    /**
     * Extracts metadata from documents retrieved as context in the chat response.
     *
     * @param response The ChatResponse object containing result and metadata.
     * @return A list of metadata maps for each context document.
     */
    private List<Map<String, Object>> extractDocumentMetadata(ChatResponse response) {
        List<Document> contextDocuments = response.getMetadata().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        return contextDocuments.stream()
                .map(Document ::getMetadata)
                .collect(Collectors.toList());
    }

}
