package com.oep.gateway.config;

import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollDatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.RoundRobinDnsAddressResolverGroup;
import org.springframework.cloud.gateway.config.HttpClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Client-side load balancing over DNS. A service name that has several A records (docker compose --scale, ECS Cloud Map
 * with several tasks) resolves to ALL of them; the default JDK resolver always takes the first one, so extra replicas would
 * sit idle. This resolver rotates through the records on every new connection and re-queries DNS every few seconds, so
 * replicas that appear or disappear (autoscaling, deployments) are picked up without restarting the gateway.
 */
@Configuration
public class RoundRobinDnsConfig {

    @Bean
    HttpClientCustomizer roundRobinDnsCustomizer() {
        return httpClient -> httpClient.resolver(new RoundRobinDnsAddressResolverGroup(
                new DnsNameResolverBuilder()
                        // must match the event loop the HTTP client runs on (epoll on Linux, NIO elsewhere)
                        .channelType(Epoll.isAvailable() ? EpollDatagramChannel.class : NioDatagramChannel.class)
                        .ttl(1, 10)           // seconds: min/max cache time, whatever the record's own TTL says
                        .negativeTtl(1)
                        .queryTimeoutMillis(2000)));
    }
}
