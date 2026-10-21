package com.pantea.receiptgate.index;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LogisticProbeTest {

    /** Unit vectors clustered around two directions, like CLIP vectors of two kinds of image. */
    private static float[] around(float[] centre, Random rnd) {
        float[] v = new float[centre.length];
        double norm = 0;
        for (int i = 0; i < v.length; i++) {
            v[i] = centre[i] + (float) (rnd.nextGaussian() * 0.05);
            norm += v[i] * v[i];
        }
        for (int i = 0; i < v.length; i++) {
            v[i] /= (float) Math.sqrt(norm);
        }
        return v;
    }

    @Test
    void separatesTwoClustersEvenWhenUnbalanced() {
        Random rnd = new Random(42);
        int d = 64;
        float[] a = new float[d];
        float[] b = new float[d];
        a[0] = 1;
        b[1] = 1;
        List<float[]> xs = new ArrayList<>();
        List<Boolean> ys = new ArrayList<>();
        for (int i = 0; i < 5; i++) {   // few receipts...
            xs.add(around(a, rnd));
            ys.add(true);
        }
        for (int i = 0; i < 40; i++) {  // ...many non-receipts: class balancing must stop them drowning the receipts
            xs.add(around(b, rnd));
            ys.add(false);
        }
        LogisticProbe probe = LogisticProbe.train(xs, ys, 5e-4, 2000, 0.5);

        assertThat(probe.probability(around(a, rnd))).isGreaterThan(0.8);
        assertThat(probe.probability(around(b, rnd))).isLessThan(0.2);
    }

    @Test
    void refusesOneSidedData() {
        assertThatThrownBy(() -> LogisticProbe.train(List.of(new float[]{1, 0}), List.of(true), 1e-3, 10, 0.5))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
