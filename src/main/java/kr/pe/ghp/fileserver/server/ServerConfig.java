package kr.pe.ghp.fileserver.server;

import java.net.InetSocketAddress;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;

@Configuration
@ComponentScan("kr.pe.ghp")
@PropertySource("classpath:server.properties")
public class ServerConfig {
	@Value("${boss.thread.count}")
	private int bossThreadCount;

	@Value("${worker.thread.count}")
	private int workerThreadCount;

	@Value("${tcp.port}")
	private int tcpPort;

	@Bean(name = "bossThreadCount")
	public int getBossThreadCount() {
		return bossThreadCount;
	}

	@Bean(name = "workerThreadCount")
	public int getWorkerThreadCount() {
		return workerThreadCount;
	}

	@Bean(name = "port")
	public int getTcpPort() {
		return tcpPort;
	}

	@Bean(name = "tcpSocketAddress")
	public InetSocketAddress tcpPort() {
		return new InetSocketAddress(tcpPort);
	}

	@Bean
	public static PropertySourcesPlaceholderConfigurer propertyPlaceholderConfigurer() {
		return new PropertySourcesPlaceholderConfigurer();
	}
}