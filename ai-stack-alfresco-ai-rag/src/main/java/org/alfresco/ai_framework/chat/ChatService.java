package org.alfresco.ai_framework.chat;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

/**
 * Service responsible for handling chat interactions with the AI system.
 * Configures chat advisors, such as QuestionAnswerAdvisor and SafeGuardAdvisor,
 * to enrich responses and ensure safe interactions.
 */
@Slf4j
@Service
public class ChatService {

    @Value("${chat.service.debug.stream.enabled:false}")
    private boolean debugStreamEnabled;

    private static final int DEFAULT_TOP_K = 5;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    /**
     * Constructs the ChatService with a pre-configured ChatClient and VectorStore.
     *
     * @param chatClientBuilder Builder for creating a ChatClient instance.
     * @param vectorStore       Vector store for performing document searches.
     */
    public ChatService(ChatClient.Builder chatClientBuilder, VectorStore vectorStore) {
        this.chatClient = chatClientBuilder.build();
        this.vectorStore = vectorStore;
        log.debug("ChatService initialized with ChatClient and VectorStore.");
    }

    /**
     * Processes a chat query by interacting with the AI through configured advisors.
     * Uses a QuestionAnswerAdvisor for document retrieval.
     *
     * @param query The user input to process.
     * @return The AI-generated ChatResponse, containing the answer and metadata.
     */
    public ChatResponse chat(String query) {
        log.info("Processing chat query: {}", query);

        // Configuring advisors to enhance the response quality
        ChatResponse response = chatClient.prompt()
                .user(query)
                .advisors(new QuestionAnswerAdvisor(vectorStore, SearchRequest.builder().topK(DEFAULT_TOP_K).build()))
                .call()
                .chatResponse();

        log.info("Received response from AI");
        return response;
    }

    /**
     * Processes a chat query and returns a reactive stream of AI-generated responses.
     * Uses a QuestionAnswerAdvisor for document retrieval to provide contextual answers.
     *
     * @param query The user input to process.
     * @return A Flux stream of ChatResponse objects containing answers and metadata.
     */
    public Flux<String> streamChat(String query) {
        log.info("Processing chat query: {}", query);
        var prompt = chatClient.prompt()
                .user(query)
                .advisors(new QuestionAnswerAdvisor(
                        vectorStore,
                        SearchRequest.builder().topK(DEFAULT_TOP_K).build()
                )).stream();
        if( Boolean.TRUE.equals(debugStreamEnabled) ) {
            return prompt.chatResponse()
                    .doOnNext(chatResponse -> {
                        log.info("Model: {}", chatResponse.getMetadata().getModel());
                        log.info("Response chunk: {}", chatResponse.getResult().getOutput().getText());
                    })
                    .map(chatResponse -> chatResponse.getResult().getOutput().getText());
        } else {
            return prompt.content();
        }
    }

}
