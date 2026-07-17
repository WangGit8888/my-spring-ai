package com.example.myspringai.controller;

import com.example.myspringai.tools.GunTools;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

import static org.springframework.ai.chat.memory.ChatMemory.CONVERSATION_ID;

@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class GameController {


    private final ChatClient gameChatClient;
    private final GunTools gunTools;

    @RequestMapping(value = "/chat",produces = "text/html;charset=utf-8")
    public Flux<String> chat(String prompt,String chatId) {
        return gameChatClient
                .prompt()
                .user(prompt)
                .advisors(a->a.param(CONVERSATION_ID, chatId))
                .tools(gunTools)
                .stream().content();
    }
}
