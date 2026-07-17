package com.example.myspringai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
public class CommonConfiguration {

    @Bean
    public ChatMemory chatMemory() {
        // 直接返回 InMemoryChatMemory 实例
        return new ChatMemory() {
            private final Map<String, List<Message>> conversations = new ConcurrentHashMap<>();

            @Override
            public void add(String conversationId, List<Message> messages) {
                conversations.computeIfAbsent(conversationId, k -> new ArrayList<>())
                        .addAll(messages);
            }

            @Override
            public List<Message> get(String conversationId) {
                // 返回该 conversationId 的所有消息，如果没有则返回空列表
                return conversations.getOrDefault(conversationId, new ArrayList<>());
            }

            @Override
            public void clear(String conversationId) {
                // 清除该 conversationId 的所有消息
                conversations.remove(conversationId);
            }
        };
    }

    @Bean
    public ChatClient gameChatClient(OpenAiChatModel model, ChatMemory chatMemory) {
        return ChatClient.builder(model)
                .defaultSystem("你是小黑")
                .defaultAdvisors(
                        new SimpleLoggerAdvisor(),
                        // build 方法需要传入 chatMemory
                        MessageChatMemoryAdvisor.builder(chatMemory)
                                .build()  // 在这里传入，而不是用 .chatMemory()
                )
                .build();
    }
}
