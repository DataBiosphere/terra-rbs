package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.CLOUD_RESOURCE_UID;
import static bio.terra.rbs.service.resource.FlightMapKeys.GOOGLE_PROJECT_ID;

import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.generated.model.GcpProjectConfig;
import bio.terra.rbs.generated.model.GoogleProjectUid;
import bio.terra.rbs.service.resource.projectid.GcpProjectIDGenerator;
import bio.terra.stairway.*;

/** Generates Project Id and put it in working map. */
public class GenerateProjectIdStep implements Step {
  private final GcpProjectConfig gcpProjectConfig;
  private final GcpProjectIDGenerator projectIDGenerator;

  public GenerateProjectIdStep(
      GcpProjectConfig gcpProjectConfig, GcpProjectIDGenerator projectIDGenerator) {
    this.gcpProjectConfig = gcpProjectConfig;
    this.projectIDGenerator = projectIDGenerator;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    String projectId = projectIDGenerator.generateID(gcpProjectConfig.getProjectIDGenerator());
    flightContext.getWorkingMap().put(GOOGLE_PROJECT_ID, projectId);
    workingMap.put(
        CLOUD_RESOURCE_UID,
        new CloudResourceUid().googleProjectUid(new GoogleProjectUid().projectId(projectId)));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    return StepResult.getStepResultSuccess();
  }
}
