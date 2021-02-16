package bio.terra.buffer.app.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties
@ConfigurationProperties(prefix = "buffer.stairway")
public class StairwayConfiguration {
  private String projectId;
  private boolean forceCleanStart;
  private boolean migrateUpgrade;
  private int maxParallelFlights;
  private Duration quietDownTimeout;
  private Duration terminateTimeout;

  public String getProjectId() {
    return projectId;
  }

  public boolean isForceCleanStart() {
    return forceCleanStart;
  }

  public boolean isMigrateUpgrade() {
    return migrateUpgrade;
  }

  public int getMaxParallelFlights() {
    return maxParallelFlights;
  }

  public Duration getQuietDownTimeout() {
    return quietDownTimeout;
  }

  public Duration getTerminateTimeout() {
    return terminateTimeout;
  }

  public void getProjectId(String projectId) {
    this.projectId = projectId;
  }

  public void setForceCleanStart(boolean forceCleanStart) {
    this.forceCleanStart = forceCleanStart;
  }

  public void setMigrateUpgrade(boolean migrateUpgrade) {
    this.migrateUpgrade = migrateUpgrade;
  }

  public void setMaxParallelFlights(int maxParallelFlights) {
    this.maxParallelFlights = maxParallelFlights;
  }

  public void setQuietDownTimeout(Duration quietDownTimeout) {
    this.quietDownTimeout = quietDownTimeout;
  }

  public void setTerminateTimeout(Duration terminateTimeout) {
    this.terminateTimeout = terminateTimeout;
  }
}
