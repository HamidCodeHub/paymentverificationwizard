package com.pantea.receiptgate.index;

import java.util.List;

/**
 * Logistic regression on CLIP vectors: "how receipt-like does this image look?".
 *
 * <p>Plain batch gradient descent with an L2 penalty and class balancing (receipts are weighted up to the
 * size of the non-receipt set), matching the Python probe in {@code bench/probe.py}. With ~150 vectors of
 * 768 dimensions a full training run takes about a second, so it simply retrains on every reindex.
 */
public final class LogisticProbe {

    private final double[] weights;
    private final double bias;

    private LogisticProbe(double[] weights, double bias) {
        this.weights = weights;
        this.bias = bias;
    }

    public static LogisticProbe train(List<float[]> vectors, List<Boolean> isReceipt,
                                      double l2, int iterations, double learningRate) {
        int n = vectors.size();
        if (n == 0 || n != isReceipt.size()) {
            throw new IllegalArgumentException("Need one label per vector");
        }
        long positives = isReceipt.stream().filter(b -> b).count();
        if (positives == 0 || positives == n) {
            throw new IllegalArgumentException("Need both receipts and non-receipts to train");
        }
        int d = vectors.get(0).length;
        double positiveWeight = (double) (n - positives) / positives;

        double[] w = new double[d];
        double b = 0;
        double[] residual = new double[n];
        double[] gradient = new double[d];

        for (int it = 0; it < iterations; it++) {
            double residualSum = 0;
            for (int i = 0; i < n; i++) {
                float[] x = vectors.get(i);
                boolean y = isReceipt.get(i);
                double p = sigmoid(b + dot(w, x));
                residual[i] = (p - (y ? 1 : 0)) * (y ? positiveWeight : 1.0);
                residualSum += residual[i];
            }
            java.util.Arrays.fill(gradient, 0);
            for (int i = 0; i < n; i++) {
                float[] x = vectors.get(i);
                double r = residual[i];
                for (int j = 0; j < d; j++) {
                    gradient[j] += r * x[j];
                }
            }
            for (int j = 0; j < d; j++) {
                w[j] -= learningRate * (gradient[j] / n + l2 * w[j]);
            }
            b -= learningRate * residualSum / n;
        }
        return new LogisticProbe(w, b);
    }

    /** Probability 0..1 that the vector belongs to a receipt. */
    public double probability(float[] vector) {
        return sigmoid(bias + dot(weights, vector));
    }

    private static double dot(double[] w, float[] x) {
        double s = 0;
        for (int j = 0; j < w.length; j++) {
            s += w[j] * x[j];
        }
        return s;
    }

    private static double sigmoid(double z) {
        if (z >= 0) {
            return 1 / (1 + Math.exp(-z));
        }
        double e = Math.exp(z);
        return e / (1 + e);
    }
}
