package kr.pe.ghp.fileserver.util;

import javax.annotation.PostConstruct;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

/**
 * @author geunhui park
 */
@Component
public class SpringContextHolder implements ApplicationContextAware {
	private static SpringContextHolder staticContext;
	private ApplicationContext applicationContext;

	@PostConstruct
	public void registerInstance() {
		staticContext = this;
	}

	public static <T> T getBean(Class<T> clazz) {
		return staticContext.applicationContext.getBean(clazz);
	}

	public static Object getBean(String name) {
		return staticContext.applicationContext.getBean(name);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}
}
