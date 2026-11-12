package com.devdocs.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

	@Bean
	public TokenTextSplitter tokenTextSplitter() {
		return TokenTextSplitter.builder()
				.withChunkSize(500)
				.withMinChunkSizeChars(150)
				.withMinChunkLengthToEmbed(20)
				.withMaxNumChunks(5000)
				.withKeepSeparator(true)
				.build();
	}

	@Bean
	public ChatClient chatClient(ChatClient.Builder chatClientBuilder) {
		return chatClientBuilder.build();
	}
}
