package com.example.myspringai.config;

import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InMemoryChatHistoryRepository implements ChatMemoryRepository {
    @Override
    public List<String> findConversationIds() {
        return List.of();
    }

    @Override
    public List<Message> findByConversationId(String s) {
        return List.of();
    }

    @Override
    public void saveAll(String s, List<Message> list) {

    }

    @Override
    public void deleteByConversationId(String s) {

    }
}
