package bio.terra.buffer.common;

/**
 * The state of the {@link Pool}.
 *
 * <p>This is persisted as a string in the database, so the names of the enum values should not be
 * changed.
 */
public enum PoolStatus {
  /** Active pool, able to handout resources. */
  ACTIVE,
  /**
   * Deactivated pool, all resources are deactivated or being deactivated, not able to handout
   * resources.
   */
  DEACTIVATED,
}
