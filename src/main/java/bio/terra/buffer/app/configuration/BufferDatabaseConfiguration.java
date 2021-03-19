package bio.terra.buffer.app.configuration;

import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_DB_DATA_SOURCE;
import static bio.terra.buffer.app.configuration.BeanNames.BUFFER_JDBC_TEMPLATE;

import bio.terra.common.db.DataSourceInitializer;
import javax.sql.DataSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@Configuration
@EnableConfigurationProperties(value = BufferDatabaseProperties.class)
@EnableTransactionManagement
public class BufferDatabaseConfiguration {
  private final BufferDatabaseProperties databaseProperties;

  public BufferDatabaseConfiguration(BufferDatabaseProperties databaseProperties) {
    this.databaseProperties = databaseProperties;
  }

  @Bean(BUFFER_DB_DATA_SOURCE)
  public DataSource getBufferDbDataSource() {
    System.out.println("~~~~~~~~~~~~getBufferDbDataSource");
    System.out.println(databaseProperties);
    return DataSourceInitializer.initializeDataSource(databaseProperties);
  }

  @Bean(BUFFER_JDBC_TEMPLATE)
  public NamedParameterJdbcTemplate getBufferDnNamedParameterJdbcTemplate() {
    System.out.println("~~~~~~~~~~~~getNamedParameterJdbcTemplate");
    System.out.println("~~~~~~~~~~~~getNamedParameterJdbcTemplate");
    System.out.println("~~~~~~~~~~~~getNamedParameterJdbcTemplate");
    System.out.println("~~~~~~~~~~~~getNamedParameterJdbcTemplate");
    return new NamedParameterJdbcTemplate(getBufferDbDataSource());
  }

  // This bean plus the @EnableTransactionManagement annotation above enables the use of the
  // @Transaction annotation to control the transaction properties of the data source.
  @Bean("transactionManager")
  public PlatformTransactionManager getTransactionManager() {
    return new DataSourceTransactionManager(getBufferDbDataSource());
  }
}
