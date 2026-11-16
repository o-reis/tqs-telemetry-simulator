package pt.ua.deti.tqs.p11.simulator.contract;

import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

public final class RandomUuids implements Supplier<UUID> {

  public RandomUuids(RandomGenerator random) {}

  @Override
  public UUID get() {
    return new UUID(0, 0);
  }
}
