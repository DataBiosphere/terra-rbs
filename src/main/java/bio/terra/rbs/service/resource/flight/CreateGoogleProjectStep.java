package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.cloudres.google.cloudresourcemanager.CloudResourceManagerCow;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.GoogleProjectUid;
import bio.terra.stairway.*;
import com.google.api.services.cloudresourcemanager.model.Operation;
import com.google.api.services.cloudresourcemanager.model.Project;
import com.google.api.services.cloudresourcemanager.model.ResourceId;
import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Creates the basic GCP project. */
public class CreateGoogleProjectStep implements Step {
  private final Logger logger = LoggerFactory.getLogger(CreateGoogleProjectStep.class);
  private final CloudResourceManagerCow rmCow;
  private final GcpProjectConfig gcpProjectConfig;

  public CreateGoogleProjectStep(CloudResourceManagerCow rmCow, GcpProjectConfig gcpProjectConfig) {
    this.rmCow = rmCow;
    this.gcpProjectConfig = gcpProjectConfig;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    System.out.println("!!!!!!!!!!!~~~~~~rmCow222222222");
    System.out.println(rmCow);

    FlightMap workingMap = flightContext.getWorkingMap();
    String projectId = randomProjectId();
    workingMap.put(GOOGLE_PROJECT_ID, projectId);
    workingMap.put(
        CLOUD_RESOURCE_UID,
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId)));
    Project project =
        new Project()
            .setProjectId(projectId)
            .setParent(
                new ResourceId().setType("folder").setId(gcpProjectConfig.getParentFolderId()));
    try {
      Operation operation = rmCow.projects().create(project).execute();
      OperationCow<Operation> operationCow = rmCow.operations().operationCow(operation);
      OperationUtils.pollUntilComplete(operationCow, Duration.ofSeconds(5), Duration.ofSeconds(30));
    } catch (IOException | InterruptedException e) {
      logger.error("Error when creating GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    try {
      rmCow.projects().delete(workingMap.get(GOOGLE_PROJECT_ID, String.class)).execute();
    } catch (IOException e) {
      logger.error("Error when deleting GCP project", e);
      return new StepResult(StepStatus.STEP_RESULT_FAILURE_RETRY, e);
    }
    return StepResult.getStepResultSuccess();
  }

  public static String randomProjectId() {
    // TODO: Replace with name schema once that is finalized.
    return "p" + UUID.randomUUID().toString().substring(0, 29);
  }
}
