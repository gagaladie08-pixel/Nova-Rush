package com.novarush.app.separation;

/**
 * FFT complexe à radix mixte (2, 3, 4, 5, puis générique), algorithme de
 * Stockham (décimation en fréquence, auto-tri). Taille quelconque ; les
 * modèles UVR utilisent par exemple n_fft = 7680 = 2^9 · 3 · 5.
 */
final class Fft {
    final int n;
    private final int[] radices;
    private final float[] twRe;
    private final float[] twIm;
    private final float[] workRe;
    private final float[] workIm;

    Fft(int n) {
        if (n < 1) throw new IllegalArgumentException("n");
        this.n = n;
        int[] tmp = new int[32];
        int count = 0;
        int m = n;
        while (m % 4 == 0) { tmp[count++] = 4; m /= 4; }
        while (m % 2 == 0) { tmp[count++] = 2; m /= 2; }
        for (int p = 3; m > 1; p += 2) {
            while (m % p == 0) { tmp[count++] = p; m /= p; }
            if ((long) p * p > m && m > 1) { tmp[count++] = m; m = 1; }
        }
        radices = new int[count];
        System.arraycopy(tmp, 0, radices, 0, count);
        twRe = new float[n];
        twIm = new float[n];
        for (int i = 0; i < n; i++) {
            double a = -2 * Math.PI * i / n;
            twRe[i] = (float) Math.cos(a);
            twIm[i] = (float) Math.sin(a);
        }
        workRe = new float[n];
        workIm = new float[n];
    }

    /** Transformée directe en place : X_k = Σ x_j e^{-2πi jk/n}. Non thread-safe. */
    void forward(float[] re, float[] im) {
        float[] xr = re, xi = im, yr = workRe, yi = workIm;
        int len = n;
        int s = 1;
        for (int r : radices) {
            stage(len, s, r, xr, xi, yr, yi);
            len /= r;
            s *= r;
            float[] t = xr; xr = yr; yr = t;
            t = xi; xi = yi; yi = t;
        }
        if (xr != re) {
            System.arraycopy(xr, 0, re, 0, n);
            System.arraycopy(xi, 0, im, 0, n);
        }
    }

    /** Transformée inverse en place, normalisée (1/n). */
    void inverse(float[] re, float[] im) {
        for (int i = 0; i < n; i++) im[i] = -im[i];
        forward(re, im);
        float k = 1f / n;
        for (int i = 0; i < n; i++) {
            re[i] *= k;
            im[i] = -im[i] * k;
        }
    }

    /**
     * Une étape de Stockham : longueur courante len, pas s, radix r.
     * y[q + s(r·p + j)] = (Σ_k x[q + s(p + k·m)] · ω_r^{jk}) · ω_len^{j·p}
     */
    private void stage(int len, int s, int r, float[] xr, float[] xi, float[] yr, float[] yi) {
        final int m = len / r;
        final int twStep = n / len; // ω_len^t = ω_n^{t·twStep}
        for (int p = 0; p < m; p++) {
            for (int q = 0; q < s; q++) {
                final int in0 = q + s * p;
                final int inStride = s * m;
                final int out0 = q + s * r * p;
                switch (r) {
                    case 2: {
                        float ar = xr[in0], ai = xi[in0];
                        float br = xr[in0 + inStride], bi = xi[in0 + inStride];
                        yr[out0] = ar + br;
                        yi[out0] = ai + bi;
                        float dr = ar - br, di = ai - bi;
                        int t = (p * twStep) % n;
                        yr[out0 + s] = dr * twRe[t] - di * twIm[t];
                        yi[out0 + s] = dr * twIm[t] + di * twRe[t];
                        break;
                    }
                    case 4: {
                        float a0r = xr[in0], a0i = xi[in0];
                        float a1r = xr[in0 + inStride], a1i = xi[in0 + inStride];
                        float a2r = xr[in0 + 2 * inStride], a2i = xi[in0 + 2 * inStride];
                        float a3r = xr[in0 + 3 * inStride], a3i = xi[in0 + 3 * inStride];
                        float t0r = a0r + a2r, t0i = a0i + a2i;
                        float t1r = a0r - a2r, t1i = a0i - a2i;
                        float t2r = a1r + a3r, t2i = a1i + a3i;
                        float t3r = a1r - a3r, t3i = a1i - a3i;
                        // X0 = t0 + t2 ; X1 = t1 - i·t3 ; X2 = t0 - t2 ; X3 = t1 + i·t3
                        float x0r = t0r + t2r, x0i = t0i + t2i;
                        float x1r = t1r + t3i, x1i = t1i - t3r;
                        float x2r = t0r - t2r, x2i = t0i - t2i;
                        float x3r = t1r - t3i, x3i = t1i + t3r;
                        yr[out0] = x0r;
                        yi[out0] = x0i;
                        int t1 = (p * twStep) % n;
                        int t2 = (2 * p * twStep) % n;
                        int t3 = (3 * p * twStep) % n;
                        yr[out0 + s] = x1r * twRe[t1] - x1i * twIm[t1];
                        yi[out0 + s] = x1r * twIm[t1] + x1i * twRe[t1];
                        yr[out0 + 2 * s] = x2r * twRe[t2] - x2i * twIm[t2];
                        yi[out0 + 2 * s] = x2r * twIm[t2] + x2i * twRe[t2];
                        yr[out0 + 3 * s] = x3r * twRe[t3] - x3i * twIm[t3];
                        yi[out0 + 3 * s] = x3r * twIm[t3] + x3i * twRe[t3];
                        break;
                    }
                    default: {
                        // DFT générique de taille r (r = 3, 5, ... : peu d'étapes).
                        final int rStep = n / r; // ω_r^u = ω_n^{u·rStep}
                        for (int j = 0; j < r; j++) {
                            float sr = 0, si = 0;
                            for (int k = 0; k < r; k++) {
                                int u = ((j * k) % r) * rStep;
                                float vr = xr[in0 + k * inStride], vi = xi[in0 + k * inStride];
                                sr += vr * twRe[u] - vi * twIm[u];
                                si += vr * twIm[u] + vi * twRe[u];
                            }
                            int t = (int) (((long) j * p * twStep) % n);
                            yr[out0 + j * s] = sr * twRe[t] - si * twIm[t];
                            yi[out0 + j * s] = sr * twIm[t] + si * twRe[t];
                        }
                    }
                }
            }
        }
    }
}
