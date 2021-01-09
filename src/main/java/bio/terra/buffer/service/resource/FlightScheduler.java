package bio.terra.buffer.service.resource;

import static bio.terra.buffer.common.MetricsHelper.recordResourceStateCount;

import bio.terra.buffer.app.configuration.PrimaryConfiguration;
import bio.terra.buffer.common.*;
import bio.terra.buffer.db.*;
import bio.terra.buffer.service.stairway.StairwayComponent;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Periodically checks database state and submit Stairway flights to create/delete resource if
 * needed.
 */
@Component
public class FlightScheduler {
  private final Logger logger = LoggerFactory.getLogger(FlightScheduler.class);

  /** Only need as many threads as we have scheduled tasks. */
  private final ScheduledExecutorService executor = new ScheduledThreadPoolExecutor(1);

  private final FlightManager flightManager;
  private final PrimaryConfiguration primaryConfiguration;
  private final StairwayComponent stairwayComponent;
  private final BufferDao bufferDao;

  @Autowired
  public FlightScheduler(
      FlightManager flightManager,
      PrimaryConfiguration primaryConfiguration,
      StairwayComponent stairwayComponent,
      BufferDao bufferDao) {
    this.flightManager = flightManager;
    this.primaryConfiguration = primaryConfiguration;
    this.stairwayComponent = stairwayComponent;
    this.bufferDao = bufferDao;
  }

  /**
   * Initialize the FlightScheduler, kicking off its tasks.
   *
   * <p>The StairwayComponent must be ready before calling this function.
   */
  public void initialize() {
    Preconditions.checkState(
        stairwayComponent.getStatus().equals(StairwayComponent.Status.OK),
        "Stairway must be ready before FlightScheduler can be initialized.");
    if (primaryConfiguration.isSchedulerEnabled()) {
      logger.info("Buffer scheduling enabled.");
    } else {
      // Do nothing if scheduling is disabled.
      logger.info("Buffer scheduling disabled.");
      return;
    }
    // The scheduled task will not execute concurrently with itself even if it takes a long time.
    // See javadoc on ScheduledExecutorService#scheduleAtFixedRate.
    executor.scheduleAtFixedRate(
        new LogThrowables(this::scheduleFlights),
        /* initialDelay= */ 0,
        /* period= */ primaryConfiguration.getFlightSubmissionPeriod().toMillis(),
        TimeUnit.MILLISECONDS);
  }

  /**
   * Try to schedule flights to create and delete resources until resource count matches each pool
   * state or reach to configuration limit.
   */
  private void scheduleFlights() {
    logger.info("Beginning scheduling flights.");
    List<PoolAndResourceStates> poolAndResourceStatesList =
        bufferDao.retrievePoolAndResourceStates();
    for (PoolAndResourceStates poolAndResources : poolAndResourceStatesList) {
      recordResourceStateCount(poolAndResources);
      if (poolAndResources.pool().status().equals(PoolStatus.ACTIVE)) {
        int size = poolAndResources.pool().size();
        int readyAndCreatingCount =
            poolAndResources.resourceStates().count(ResourceState.CREATING)
                + poolAndResources.resourceStates().count(ResourceState.READY);
        logger.info(
            "Pool id: {}, size:{}, readyAndCreatingCount: {}.",
            poolAndResources.pool().id(),
            size,
            readyAndCreatingCount);
        if (size > readyAndCreatingCount) {
          scheduleCreationFlights(poolAndResources.pool(), size - readyAndCreatingCount);
        } else if (poolAndResources.resourceStates().count(ResourceState.READY) > size) {
          // Only deletion READY resource, we hope future schedule runs will deletion resources
          // just turns to READY from CREATING.
          scheduleDeletionFlights(
              poolAndResources.pool(),
              poolAndResources.resourceStates().count(ResourceState.READY) - size);
        }
      } else {
        // Only deletion READY resource, we hope future schedule runs will deletion resources
        // just turns to READY from CREATING.
        scheduleDeletionFlights(
            poolAndResources.pool(), poolAndResources.resourceStates().count(ResourceState.READY));
      }
    }
  }

  /** Schedules up to {@code number} of resources creation flight for a pool. */
  private void scheduleCreationFlights(Pool pool, int number) {
    int flightToSchedule = Math.min(primaryConfiguration.getResourceCreationPerPoolLimit(), number);
    logger.info(
        "Beginning resource creation flights for pool: {}, target submission number: {} .",
        pool.id(),
        flightToSchedule);

    int successSubmitNum = 0;
    while (flightToSchedule-- > 0) {
      if (flightManager.submitCreationFlight(pool).isPresent()) {
        ++successSubmitNum;
      }
    }
    logger.info(
        "Successfully submitted {} number of resource creation flights for pool: {} .",
        successSubmitNum,
        pool.id());
  }

  /** Schedules up to {@code number} of resources creation flight for a pool. */
  private void scheduleDeletionFlights(Pool pool, int number) {
    int flightToSchedule = Math.min(primaryConfiguration.getResourceDeletionPerPoolLimit(), number);
    logger.info(
        "Beginning resource deletion flights for pool: {}, target submission number: {} .",
        pool.id(),
        flightToSchedule);

    List<Resource> resources =
        bufferDao.retrieveResourcesRandomly(pool.id(), ResourceState.READY, flightToSchedule);
    int successSubmitNum = 0;
    for (Resource resource : resources) {
      boolean submissionSuccessful =
          flightManager.submitDeletionFlight(resource, pool.resourceType()).isPresent();
      if (submissionSuccessful) {
        ++successSubmitNum;
      }
    }
    logger.info(
        "Successfully submitted {} number of resource deletion flights for pool: {} .",
        successSubmitNum,
        pool.id());
  }

  public void shutdown() {
    // Don't schedule  anything new during shutdown.
    executor.shutdown();
  }

  /**
   * Wraps a runnable to log any thrown errors to allow the runnable to still be run with a {@link
   * ScheduledExecutorService}.
   *
   * <p>ScheduledExecutorService scheduled tasks that throw errors stop executing.
   */
  private class LogThrowables implements Runnable {
    private final Runnable task;

    private LogThrowables(Runnable task) {
      this.task = task;
    }

    @Override
    public void run() {
      try {
        task.run();
      } catch (Throwable t) {
        logger.error(
            "Caught exception in FlightScheduler ScheduledExecutorService. StackTrace:\n"
                + t.getStackTrace(),
            t);
      }
    }
  }
}
