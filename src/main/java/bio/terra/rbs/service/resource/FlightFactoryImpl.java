package bio.terra.rbs.service.resource;

import bio.terra.rbs.db.ResourceType;
import bio.terra.rbs.service.resource.flight.GoogleProjectCreationFlight;
import bio.terra.rbs.service.resource.flight.GoogleProjectDeletionFlight;
import bio.terra.stairway.Flight;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FlightFactoryImpl implements FlightFactory {
  Map<ResourceType, Class<? extends Flight>> CREATION_FLIGHT_MAP =
      ImmutableMap.of(ResourceType.GOOGLE_PROJECT, GoogleProjectCreationFlight.class);
  Map<ResourceType, Class<? extends Flight>> DELETION_FLIGHT_MAP =
      ImmutableMap.of(ResourceType.GOOGLE_PROJECT, GoogleProjectDeletionFlight.class);

  @Override
  public Class<? extends Flight> getCreationFlightClass(ResourceType type) {
    if (!CREATION_FLIGHT_MAP.containsKey(type)) {
      throw new UnsupportedOperationException(
          String.format("Creation for ResourceType: %s is not supported", type.toString()));
    }
    return CREATION_FLIGHT_MAP.get(type);
  }

  @Override
  public Class<? extends Flight> getDeletionFlightClass(ResourceType type) {
    if (!CREATION_FLIGHT_MAP.containsKey(type)) {
      throw new UnsupportedOperationException(
          String.format("Deletion for ResourceType: %s is not supported", type.toString()));
    }
    return DELETION_FLIGHT_MAP.get(type);
  }
}
