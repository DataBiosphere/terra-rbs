package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.isNetworkMonitoringEnabled;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.NETWORK_NAME;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Network;
import com.google.api.services.compute.model.Subnetwork;
import com.google.api.services.compute.model.SubnetworkLogConfig;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates Subnetworks for project
 *
 * <p>This class implements the creation of a non-default VPC network within a GCP project, per the
 * CIS benchmark guidelines for Google Cloud Platform ("3.1 Ensure the default network does not
 * exist in a project"). The default network automatically maintains a subnetwork in each active GCP
 * region; the goal of this code is to recreate a setup that is very close to how the default VPC
 * network with auto-subnets operates, but with hard-coded regions and IP ranges.
 *
 * <p>As of Q4 2020, Terra doesn't have a strong driver for regional resources outside of the
 * Broad's default zone (us-central1), so this list of subnets is not carefully curated or
 * automatically extended as new GCP regions are added.
 *
 * <p>If the list of subnets managed here grows (to support new cloud regions) or shrinks (to reduce
 * the space of preallocated IPs), a manual backfill process would be required to update the set of
 * subnetworks in existing Terra workspaces.
 */
public class CreateSubnetsStep implements Step {
  /**
   * All current Google Compute Engine regions with the default Ip ranges listed (and manually
   * copied) in: https://cloud.google.com/vpc/docs/vpc#ip-ranges. The subnet ranges were expanded
   * from the default 4,096 IP addresses (/20) to 65,536 IP addresses (/16) per region.
   *
   * <p>One can expand the IP ranges later. FYI there is NOT an equivalent `shrink-ip-range`, only
   * https://cloud.google.com/sdk/gcloud/reference/compute/networks/subnets/expand-ip-range
   *
   * <p>/16 currently gives each region its own unique two-octal subnet.
   *
   * <p>NOTE: As of 2021-06-30 the IP ranges here have drifted from the list in gcp-dm-templates.
   * The two repositories contain a different set of regions, and the regions have been assigned
   * different subnets.
   * https://github.com/broadinstitute/gcp-dm-templates/blob/bf2ec422869d108670ea34f93db3e19d5d07b017/templates/network.py
   */
  @VisibleForTesting
  public static final Map<String, String> REGION_TO_IP_RANGE =
      ImmutableMap.<String, String>builder()
          .put("asia-east1", "10.140.0.0/16")
          .put("asia-east2", "10.170.0.0/16")
          .put("asia-northeast1", "10.146.0.0/16")
          .put("asia-northeast2", "10.174.0.0/16")
          .put("asia-northeast3", "10.178.0.0/16")
          .put("asia-south1", "10.160.0.0/16")
          .put("asia-southeast1", "10.148.0.0/16")
          .put("asia-southeast2", "10.184.0.0/16")
          .put("australia-southeast1", "10.152.0.0/16")
          .put("europe-north1", "10.166.0.0/16")
          .put("europe-west1", "10.132.0.0/16")
          .put("europe-west2", "10.154.0.0/16")
          .put("europe-west3", "10.156.0.0/16")
          .put("europe-west4", "10.164.0.0/16")
          .put("europe-west6", "10.172.0.0/16")
          .put("northamerica-northeast1", "10.162.0.0/16")
          .put("southamerica-east1", "10.158.0.0/16")
          .put("us-central1", "10.128.0.0/16")
          .put("us-east1", "10.142.0.0/16")
          .put("us-east4", "10.150.0.0/16")
          .put("us-west1", "10.138.0.0/16")
          .put("us-west2", "10.168.0.0/16")
          .put("us-west3", "10.180.0.0/16")
          .put("us-west4", "10.182.0.0/16")
          .build();

  /**
   * If flow logs are enabled, we want to adjust the default config in two ways:
   *
   * <ul>
   *   <li>Increase the sampling ratio (defaults to 0.5) so we sample all traffic.
   *   <li>Reduce the aggregation interval to 30 seconds (default is 5secs) to save on storage
   * </ul>
   */
  @VisibleForTesting
  public static final SubnetworkLogConfig LOG_CONFIG =
      new SubnetworkLogConfig()
          .setAggregationInterval("INTERVAL_30_SEC")
          .setEnable(true)
          .setFlowSampling((float) 1.0)
          .setMetadata("INCLUDE_ALL_METADATA");

  private final Logger logger = LoggerFactory.getLogger(CreateSubnetsStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateSubnetsStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    boolean networkMonitoringEnabled = isNetworkMonitoringEnabled(gcpProjectConfig);
    List<OperationCow<?>> operationsToPoll = new ArrayList<>();
    try {
      Network network =
          getResource(() -> computeCow.networks().get(projectId, NETWORK_NAME).execute(), 404)
              .get();
      for (Map.Entry<String, String> entry : REGION_TO_IP_RANGE.entrySet()) {
        String region = entry.getKey();
        Subnetwork subnetwork =
            new Subnetwork()
                .setName(SUBNETWORK_NAME)
                .setRegion(region)
                .setNetwork(network.getSelfLink())
                .setIpCidrRange(entry.getValue())
                .setEnableFlowLogs(networkMonitoringEnabled)
                .setPrivateIpGoogleAccess(networkMonitoringEnabled);
        if (networkMonitoringEnabled) {
          subnetwork.setLogConfig(LOG_CONFIG);
        }

        createResourceAndIgnoreConflict(
                () -> computeCow.subnetworks().insert(projectId, region, subnetwork).execute())
            .ifPresent(
                insertOperation ->
                    operationsToPoll.add(
                        computeCow
                            .regionalOperations()
                            .operationCow(projectId, region, insertOperation)));
      }

      // Kick off all the operations first then poll all operations
      for (OperationCow<?> operation : operationsToPoll) {
        pollUntilSuccess(operation, Duration.ofSeconds(5), Duration.ofMinutes(5));
      }
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating subnets", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP at CreateProjectStep.
    // doStep methods already checks subnets exists or not. So no need to delete subnet.
    return StepResult.getStepResultSuccess();
  }
}
