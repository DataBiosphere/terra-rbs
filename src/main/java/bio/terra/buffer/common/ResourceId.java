package bio.terra.buffer.common;

import bio.terra.stairway.FlightMap;
import com.google.auto.value.AutoValue;
import java.util.UUID;

/** The unique identifier for every resource. */
@AutoValue
public abstract class ResourceId {
  private static final String RESOURCE_ID_MAP_KEY = "ResourceId";

  public abstract UUID id();

  public static ResourceId create(UUID id) {
    return new AutoValue_ResourceId(id);
  }

  @Override
  public String toString() {
    return id().toString();
  }

  /** Retrieve and construct a ResourceId form {@link FlightMap}. */
  public static ResourceId retrieve(FlightMap map) {
    // TODO(PF-316): Store UUID in flight map PF-316 after fixed
    return ResourceId.create(UUID.fromString(map.get(RESOURCE_ID_MAP_KEY, String.class)));
  }

  /** Stores ResourceId value in {@link FlightMap}. */
  public void store(FlightMap map) {
    map.put(RESOURCE_ID_MAP_KEY, id().toString());
  }
}
