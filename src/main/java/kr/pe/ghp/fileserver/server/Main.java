package kr.pe.ghp.fileserver.server;

import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @author geunhui park
 */
public class Main {
	public static void main(String[] args) throws Exception {
		AbstractApplicationContext springContext = null;
		try {
			springContext = new ClassPathXmlApplicationContext("/applicationContext.xml");
			springContext.registerShutdownHook();

			HttpStaticFileServer server = springContext.getBean(HttpStaticFileServer.class);
			server.start();
		} finally {
			springContext.close();
		}
	}
}
