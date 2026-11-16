package pt.ua.deti.tqs.p11.simulator.contract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class RandomUuidsTest {

  private static List<UUID> draw(long seed, int count) {
    RandomUuids ids = new RandomUuids(new Random(seed));
    return Stream.generate(ids).limit(count).toList();
  }

  @Test
  void ids_areVersion4WithTheStandardVariant() {
    // The contract's messageId is a UUID; version 4 is the random kind.
    assertThat(draw(1, 100))
        .allSatisfy(id -> {
          assertThat(id.version()).isEqualTo(4);
          assertThat(id.variant()).isEqualTo(2);
        });
  }

  @Test
  void ids_areUnique() {
    assertThat(draw(1, 1000)).doesNotHaveDuplicates();
  }

  @Test
  void sameSeed_givesTheSameIds_differentSeed_givesOthers() {
    assertThat(draw(42, 10)).isEqualTo(draw(42, 10)).isNotEqualTo(draw(43, 10));
  }
}
