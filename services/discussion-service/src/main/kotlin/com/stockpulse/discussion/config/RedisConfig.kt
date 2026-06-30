package com.stockpulse.discussion.config

import com.stockpulse.discussion.chat.ChatRelayListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.PatternTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter

@Configuration
class RedisConfig {

    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(connectionFactory)

    @Bean
    fun chatListenerAdapter(listener: ChatRelayListener): MessageListenerAdapter =
        MessageListenerAdapter(listener, "onMessage")

    @Bean
    fun redisMessageListenerContainer(
        connectionFactory: RedisConnectionFactory,
        chatListenerAdapter: MessageListenerAdapter
    ): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        container.addMessageListener(chatListenerAdapter, PatternTopic("chat:*"))
        return container
    }
}
