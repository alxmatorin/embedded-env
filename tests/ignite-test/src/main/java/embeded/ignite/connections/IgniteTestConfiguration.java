package embeded.ignite.connections;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IgniteTestProperties.class)
class IgniteTestConfiguration {
}
