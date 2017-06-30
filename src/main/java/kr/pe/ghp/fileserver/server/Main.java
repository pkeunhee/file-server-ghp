package kr.pe.ghp.fileserver.server;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * @author geunhui park
 */
public class Main {
	public static void main(String[] args) throws Exception {
		AbstractApplicationContext springContext = null;
		try {
			springContext = new AnnotationConfigApplicationContext(ServerConfig.class);
			springContext.registerShutdownHook();

			HttpStaticFileServer server = springContext.getBean(HttpStaticFileServer.class);
			server.start();
		} finally {
			springContext.close();
		}
	}
}
