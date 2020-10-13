package bio.terra.rbs.db;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.google.auto.value.AutoValue;
import java.util.UUID;

/** Wraps the id in db pool table. */
@AutoValue
@JsonSerialize(as = ResourceId.class)
@JsonDeserialize(builder = AutoValue_ResourceId.Builder.class)
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.WRAPPER_ARRAY)
public abstract class ResourceId {
  public abstract UUID id();

  public static ResourceId create(UUID id) {
    return new AutoValue_ResourceId.Builder().id(id).build();
  }

  @Override
  public String toString() {
    return id().toString();
  }

  /** Builder for {@link ResourceId}. */
  @AutoValue.Builder
  @JsonPOJOBuilder(withPrefix = "")
  public abstract static class Builder {
    public abstract Builder id(UUID value);

    public abstract ResourceId build();
  }
}
