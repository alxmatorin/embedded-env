package embeded.ignite;

import java.util.List;

import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.cluster.ClusterState;
import org.apache.ignite.configuration.ClientConnectorConfiguration;
import org.apache.ignite.configuration.DataRegionConfiguration;
import org.apache.ignite.configuration.DataStorageConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.apache.ignite.spi.communication.tcp.TcpCommunicationSpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IgniteProperties.class)
class IgniteServerConfiguration {

    @Bean(destroyMethod = "close")
    Ignite ignite(IgniteProperties properties) {
        IgniteConfiguration configuration = nodeConfiguration(properties);
        Ignite ignite = Ignition.start(configuration);
        activatePersistentCluster(ignite, properties);
        return ignite;
    }

    private IgniteConfiguration nodeConfiguration(IgniteProperties properties) {
        return new IgniteConfiguration()
                .setIgniteInstanceName("embedded-ignite")
                .setConsistentId("embedded-ignite")
                .setLocalHost(properties.host())
                .setWorkDirectory(properties.workDirectory().toAbsolutePath().toString())
                .setGridLogger(new Slf4jLogger())
                .setMetricsLogFrequency(0)
                .setConnectorConfiguration(null)
                .setClientConnectorConfiguration(clientConnector(properties))
                .setDiscoverySpi(discovery(properties))
                .setCommunicationSpi(communication(properties))
                .setDataStorageConfiguration(dataStorage(properties));
    }

    private ClientConnectorConfiguration clientConnector(IgniteProperties properties) {
        return new ClientConnectorConfiguration()
                .setHost(properties.host())
                .setPort(properties.port())
                .setPortRange(0);
    }

    private TcpDiscoverySpi discovery(IgniteProperties properties) {
        return new TcpDiscoverySpi()
                .setLocalAddress("127.0.0.1")
                .setLocalPort(properties.discoveryPort())
                .setLocalPortRange(0)
                .setIpFinder(new TcpDiscoveryVmIpFinder()
                        .setAddresses(List.of("127.0.0.1:" + properties.discoveryPort())));
    }

    private TcpCommunicationSpi communication(IgniteProperties properties) {
        return new TcpCommunicationSpi()
                .setLocalAddress("127.0.0.1")
                .setLocalPort(properties.communicationPort())
                .setLocalPortRange(0);
    }

    private DataStorageConfiguration dataStorage(IgniteProperties properties) {
        return new DataStorageConfiguration()
                .setDefaultDataRegionConfiguration(new DataRegionConfiguration()
                        .setInitialSize(32L * 1024 * 1024)
                        .setMaxSize(128L * 1024 * 1024)
                        .setPersistenceEnabled(properties.persistenceEnabled()));
    }

    private void activatePersistentCluster(Ignite ignite, IgniteProperties properties) {
        if (properties.persistenceEnabled()) {
            ignite.cluster().state(ClusterState.ACTIVE);
        }
    }
}
