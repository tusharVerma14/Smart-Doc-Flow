package com.smartdocflow.worker.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
@RequiredArgsConstructor
public class RedisConfig {

    public static final String JOB_QUEUE = "document-jobs";

//    @Bean
//    public RedisMessageListenerContainer redisContainer(RedisConnectionFactory connectionFactory,
//                                                        MessageListenerAdapter listenerAdapter) {
//        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
//        container.setConnectionFactory(connectionFactory);
//        container.addMessageListener(listenerAdapter, new PatternTopic(JOB_QUEUE));
//        return container;
//    }
//
//    @Bean
//    public MessageListenerAdapter listenerAdapter(JobQueueConsumer consumer) {
//        return new MessageListenerAdapter(consumer, "onMessage");
//    }
//
//    @Bean
//    public ObjectMapper objectMapper() {
//        ObjectMapper mapper = new ObjectMapper();
//        mapper.registerModule(new JavaTimeModule());
//        return mapper;
//    }
//
//    @Bean
//    public WebClient.Builder webClientBuilder() {
//        return WebClient.builder();
//    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
