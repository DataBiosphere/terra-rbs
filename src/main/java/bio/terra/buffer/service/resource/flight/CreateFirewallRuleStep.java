package bio.terra.buffer.service.resource.flight;

import static bio.terra.buffer.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;
import static bio.terra.buffer.service.resource.flight.GoogleProjectConfigUtils.keepDefaultNetwork;
import static bio.terra.buffer.service.resource.flight.GoogleUtils.*;

import bio.terra.buffer.generated.model.GcpProjectConfig;
import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.compute.CloudComputeCow;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;
import bio.terra.stairway.StepStatus;
import bio.terra.stairway.exception.RetryException;
import com.google.api.services.compute.model.Firewall;
import com.google.api.services.compute.model.Network;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates firewall rules for the GCP project. */
public class CreateFirewallRuleStep implements Step {
  /** Names for firewall rules on the high-security network (called 'network'). */
  @VisibleForTesting
  public static final String ALLOW_INTERNAL_RULE_NAME_FOR_NETWORK = "allow-internal";

  @VisibleForTesting public static final String LEONARDO_SSL_RULE_NAME_FOR_NETWORK = "leonardo-ssl";

  /**
   * Names for firewall rules on the default network (called 'default'). Rule names must be unique
   * within a project, so prefix these rule names with 'default-vpc'.
   */
  @VisibleForTesting
  public static final String ALLOW_INTERNAL_RULE_NAME_FOR_DEFAULT =
      DEFAULT_NETWORK_NAME + "-vpc-" + ALLOW_INTERNAL_RULE_NAME_FOR_NETWORK;

  @VisibleForTesting
  public static final String LEONARDO_SSL_RULE_NAME_FOR_DEFAULT =
      DEFAULT_NETWORK_NAME + "-vpc-" + LEONARDO_SSL_RULE_NAME_FOR_NETWORK;

  private final Logger logger = LoggerFactory.getLogger(CreateFirewallRuleStep.class);
  private final CloudComputeCow computeCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateFirewallRuleStep(CloudComputeCow computeCow, GcpProjectConfig gcpProjectConfig) {
    this.computeCow = computeCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) throws RetryException {
    String projectId = flightContext.getWorkingMap().get(GOOGLE_PROJECT_ID, String.class);
    try {
      // Keep track of operations to poll for completion.
      List<OperationCow<?>> operationsToPoll = new ArrayList<>();

      // Network is already created and checked in previous step so here won't be empty.
      // If we got NPE, that means something went wrong with GCP, fine to just throw NPE here.
      Network highSecurityNetwork =
          getResource(() -> computeCow.networks().get(projectId, NETWORK_NAME).execute(), 404)
              .get();
      Firewall allowInternalRuleForNetwork =
          buildAllowInternalFirewallRule(highSecurityNetwork, ALLOW_INTERNAL_RULE_NAME_FOR_NETWORK);
      Firewall leonardoSslRuleForNetwork =
          buildLeonardoSslFirewallRule(highSecurityNetwork, LEONARDO_SSL_RULE_NAME_FOR_NETWORK);
      addFirewallRule(projectId, allowInternalRuleForNetwork).ifPresent(operationsToPoll::add);
      addFirewallRule(projectId, leonardoSslRuleForNetwork).ifPresent(operationsToPoll::add);

      // TODO(PF-538): revisit whether we still need this flag after NF allows specifying a network
      // If the default network was not deleted, then create identical firewall rules for it.
      if (keepDefaultNetwork(gcpProjectConfig)) {
        Network defaultNetwork =
            getResource(
                    () -> computeCow.networks().get(projectId, DEFAULT_NETWORK_NAME).execute(), 404)
                .get();
        Firewall allowInternalRuleForDefault =
            buildAllowInternalFirewallRule(defaultNetwork, ALLOW_INTERNAL_RULE_NAME_FOR_DEFAULT);
        Firewall leonardoSslRuleForDefault =
            buildLeonardoSslFirewallRule(defaultNetwork, LEONARDO_SSL_RULE_NAME_FOR_DEFAULT);
        addFirewallRule(projectId, allowInternalRuleForDefault).ifPresent(operationsToPoll::add);
        addFirewallRule(projectId, leonardoSslRuleForDefault).ifPresent(operationsToPoll::add);
      }

      for (OperationCow<?> operation : operationsToPoll) {
        pollUntilSuccess(operation, Duration.ofSeconds(3), Duration.ofMinutes(5));
      }
    } catch (IOException | InterruptedException e) {
      logger.info("Error when creating firewall rule", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Flight undo will just need to delete the project on GCP.
    return StepResult.getStepResultSuccess();
  }

  /**
   * Helper method to add a firewall rule to the project. Ignores conflicts if the rule already
   * exists.
   *
   * @param projectId project where the network lives
   * @param rule firewall rule object to add to the project
   * @return pointer to the operation to poll for completion
   */
  private Optional<OperationCow<?>> addFirewallRule(String projectId, Firewall rule)
      throws IOException {
    return createResourceAndIgnoreConflict(
            () -> computeCow.firewalls().insert(projectId, rule).execute())
        .map(
            insertOperation ->
                computeCow.globalOperations().operationCow(projectId, insertOperation));
  }

  /**
   * Helper method to build a firewall rule that allows internal traffic on the network. See <a
   * href="https://cloud.google.com/vpc/docs/firewalls#more_rules_default_vpc">default-allow-internal</a>.
   *
   * @param network the network to add the firewall rule to
   * @param ruleName name of the firewall rule (unique within a project)
   * @return firewall rule object
   */
  @VisibleForTesting
  public static Firewall buildAllowInternalFirewallRule(Network network, String ruleName) {
    return new Firewall()
        .setNetwork(network.getSelfLink())
        .setName(ruleName)
        .setDescription("Allow internal traffic on the network.")
        .setDirection("INGRESS")
        .setSourceRanges(ImmutableList.of("10.128.0.0/9"))
        .setPriority(65534)
        .setAllowed(
            ImmutableList.of(
                new Firewall.Allowed().setIPProtocol("icmp"),
                new Firewall.Allowed().setIPProtocol("tcp").setPorts(ImmutableList.of("0-65535")),
                new Firewall.Allowed().setIPProtocol("udp").setPorts(ImmutableList.of("0-65535"))));
  }

  /**
   * Helper method to build a firewall rule that allows SSL traffic from Leonardo-managed VMs on the
   * network.
   *
   * @param network the network to add the firewall rule to
   * @param ruleName name of the firewall rule (unique within a project)
   * @return firewall rule object
   */
  @VisibleForTesting
  public static Firewall buildLeonardoSslFirewallRule(Network network, String ruleName) {
    return new Firewall()
        .setNetwork(network.getSelfLink())
        .setName(ruleName)
        .setDescription("Allow SSL traffic from Leonardo-managed VMs.")
        .setDirection("INGRESS")
        .setSourceRanges(ImmutableList.of("0.0.0.0/0"))
        .setTargetTags(ImmutableList.of("leonardo"))
        .setPriority(65534)
        .setAllowed(
            ImmutableList.of(
                new Firewall.Allowed().setIPProtocol("tcp").setPorts(ImmutableList.of("443"))));
  }
}
