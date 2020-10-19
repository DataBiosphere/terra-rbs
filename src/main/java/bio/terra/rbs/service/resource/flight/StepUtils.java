package bio.terra.rbs.service.resource.flight;

import static bio.terra.rbs.service.resource.FlightMapKeys.RESOURCE_READY;

import bio.terra.cloudres.google.api.services.common.OperationCow;
import bio.terra.cloudres.google.api.services.common.OperationUtils;
import bio.terra.rbs.common.ResourceId;
import bio.terra.rbs.db.RbsDao;
import bio.terra.rbs.generated.model.CloudResourceUid;
import bio.terra.rbs.service.resource.FlightMapKeys;
import bio.terra.stairway.FlightContext;
import bio.terra.stairway.FlightMap;
import bio.terra.stairway.exception.RetryException;
import java.io.IOException;
import java.time.Duration;

/** Utilities used in Stairway steps. */
public class StepUtils {
  /**
   * Poll until the Google Service API operation has completed. Throws any error or timeouts as a
   * {@link RetryException}.
   */
  public static void pollUntilSuccess(
      OperationCow<?> operation, Duration pollingInterval, Duration timeout)
      throws RetryException, IOException, InterruptedException {
    operation = OperationUtils.pollUntilComplete(operation, pollingInterval, timeout);
    if (operation.getOperationAdapter().getError() != null) {
      throw new RetryException(
          String.format(
              "Error polling operation. name [%s] message [%s]",
              operation.getOperationAdapter().getName(),
              operation.getOperationAdapter().getError().getMessage()));
    }
  }

  /** Update resource state to READY and update working map's RESOURCE_READY boolean value. */
  public static void markResourceReady(RbsDao rbsDao, FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    rbsDao.updateResourceAsReady(
        ResourceId.retrieve(flightContext.getWorkingMap()),
        workingMap.get(FlightMapKeys.CLOUD_RESOURCE_UID, CloudResourceUid.class));
    workingMap.put(RESOURCE_READY, true);
  }
  /** Check resource is already marked as READY. This can prevent a READY resource got rollback. */
  public static boolean isResourceReady(FlightContext flightContext) {
    FlightMap workingMap = flightContext.getWorkingMap();
    return workingMap.get(RESOURCE_READY, Boolean.class) != null
        && workingMap.get(RESOURCE_READY, Boolean.class);
  }

  /** Converts project id to name. */
  public static String projectIdToName(String projectId) {
    return "projects/" + projectId;
  }
}
