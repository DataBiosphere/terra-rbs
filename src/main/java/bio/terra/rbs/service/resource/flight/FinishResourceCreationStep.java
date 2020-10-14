package bio.terra.rbs.service.resource.flight;

import bio.terra.rbs.db.*;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.Step;
import bio.terra.stairway.StepResult;

/**
 * The step after resource is successfully created, it updates resource entity to set
 * CloudResourceUid and state to READY.
 */
public class FinishResourceCreationStep implements Step {
  private final RbsDao rbsDao;

  public FinishResourceCreationStep(RbsDao rbsDao) {
    this.rbsDao = rbsDao;
  }

  @Override
  public StepResult doStep(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();

    rbsDao.updateResourceAsReady(
        ResourceId.retrieve(flightContext.getWorkingMap()),
        workingMap.get(FlightMapKeys.CLOUD_RESOURCE_UID, CloudResourceUid.class));
    return StepResult.getStepResultSuccess();
  }

  @Override
  public StepResult undoStep(FlightContext flightContext) {
    // Nothing need to do. Unto a resource creation flight needs to delete the resource from Cloud
    // and delete entity
    // from db. Both steps are handled in previous steps undo method.
    return StepResult.getStepResultSuccess();
  }
}
