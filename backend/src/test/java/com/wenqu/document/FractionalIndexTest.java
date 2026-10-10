package com.wenqu.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wenqu.document.domain.FractionalIndex;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class FractionalIndexTest {

    @Test
    void generatesKeysMatchingTheReferenceImplementation() {
        assertThat(FractionalIndex.between(null, null)).isEqualTo("a0");
        assertThat(FractionalIndex.between(null, "a0")).isEqualTo("Zz");
        assertThat(FractionalIndex.between("a0", null)).isEqualTo("a1");
        assertThat(FractionalIndex.between("a0", "a1")).isEqualTo("a0V");
        assertThat(FractionalIndex.between("a1", "a2")).isEqualTo("a1V");
        assertThat(FractionalIndex.between("a0V", "a1")).isEqualTo("a0l");
        assertThat(FractionalIndex.between("Zz", "a0")).isEqualTo("ZzV");
        assertThat(FractionalIndex.between("az", null)).isEqualTo("b00");
        assertThat(FractionalIndex.between(null, "b00")).isEqualTo("az");
        assertThat(FractionalIndex.between("a0", "a0V")).isEqualTo("a0G");
        assertThat(FractionalIndex.between("b125", "b129")).isEqualTo("b127");
        assertThat(FractionalIndex.between("Zy", "Zz")).isEqualTo("ZyV");
    }

    @Test
    void appendingManyKeysKeepsThemShortAndOrdered() {
        String last = null;
        for (int i = 0; i < 10_000; i++) {
            String next = FractionalIndex.between(last, null);
            if (last != null) {
                assertThat(next).isGreaterThan(last);
            }
            last = next;
        }
        assertThat(last).hasSizeLessThan(6);
    }

    @Test
    void randomInsertionsKeepStrictOrder() {
        Random random = new Random(42);
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < 2_000; i++) {
            int index = random.nextInt(keys.size() + 1);
            String before = index == 0 ? null : keys.get(index - 1);
            String after = index == keys.size() ? null : keys.get(index);
            String key = FractionalIndex.between(before, after);
            if (before != null) {
                assertThat(key).isGreaterThan(before);
            }
            if (after != null) {
                assertThat(key).isLessThan(after);
            }
            keys.add(index, key);
        }
        assertThat(keys).isSorted();
    }

    @Test
    void rejectsOutOfOrderBounds() {
        assertThatThrownBy(() -> FractionalIndex.between("a1", "a0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FractionalIndex.between("a1", "a1")).isInstanceOf(IllegalArgumentException.class);
    }
}
