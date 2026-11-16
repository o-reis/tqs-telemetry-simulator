package pt.ua.deti.tqs.p11.simulator.contract;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

/**
 * Random (version 4) UUIDs drawn from a given generator instead of {@link UUID#randomUUID()}. With
 * the simulation's seeded generator, message ids repeat when a run is repeated.
 */
public final class RandomUuids implements Supplier<UUID> {

  private static final long VERSION_BITS = 0xF000L;
  private static final long VERSION_4 = 0x4000L;
  private static final long VARIANT_BITS = 0xC000_0000_0000_0000L;
  private static final long VARIANT_IETF = 0x8000_0000_0000_0000L;

  private final RandomGenerator random;

  public RandomUuids(RandomGenerator random) {
    this.random = random;
  }

  @Override
  public UUID get() {
    // RFC 9562: 4 version bits and 2 variant bits are fixed, the other 122 bits are random.
    long most = (random.nextLong() & ~VERSION_BITS) | VERSION_4;
    long least = (random.nextLong() & ~VARIANT_BITS) | VARIANT_IETF;
    return new UUID(most, least);
  }
}
