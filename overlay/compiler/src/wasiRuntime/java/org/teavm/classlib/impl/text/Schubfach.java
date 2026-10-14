/*
 * Copyright 2018-2020 Raffaello Giulietti
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.teavm.classlib.impl.text;

import static org.teavm.classlib.impl.text.SchubfachMath.*;

final class Schubfach {
    private static final long C_MIN = 1L << 52;
    private static final int Q_MIN = -1074;

    private Schubfach() {
    }

    static void analyze(double value, DoubleAnalyzer.Result result) {
        long bits = Double.doubleToRawLongBits(value);
        result.sign = bits < 0;
        long fraction = bits & (C_MIN - 1);
        int exponent = (int) (bits >>> 52) & 0x7ff;
        if (exponent != 0) {
            int mq = 1075 - exponent;
            long c = C_MIN | fraction;
            if (mq > 0 && mq < 53) {
                long f = c >> mq;
                if (f << mq == c) {
                    finish(f, 0, result);
                    return;
                }
            }
            convert(-mq, c, 0, result);
        } else if (fraction != 0) {
            if (fraction < 3) convert(Q_MIN, 10 * fraction, -1, result);
            else convert(Q_MIN, fraction, 0, result);
        } else {
            result.mantissa = 0;
            result.exponent = 0;
        }
    }

    private static void convert(int q, long c, int dk, DoubleAnalyzer.Result result) {
        int out = (int) c & 1;
        long cb = c << 2;
        long cbr = cb + 2;
        long cbl;
        int k;
        if (c != C_MIN || q == Q_MIN) {
            cbl = cb - 2;
            k = flog10pow2(q);
        } else {
            cbl = cb - 1;
            k = flog10threeQuartersPow2(q);
        }
        int h = q + flog2pow10(-k) + 2;
        long g1 = g1(k);
        long g0 = g0(k);
        long vb = rop(g1, g0, cb << h);
        long vbl = rop(g1, g0, cbl << h);
        long vbr = rop(g1, g0, cbr << h);
        long s = vb >> 2;
        if (s >= 100) {
            long sp10 = 10 * multiplyHigh(s, 115_292_150_460_684_698L << 4);
            long tp10 = sp10 + 10;
            boolean upin = vbl + out <= sp10 << 2;
            boolean wpin = (tp10 << 2) + out <= vbr;
            if (upin != wpin) {
                finish(upin ? sp10 : tp10, k, result);
                return;
            }
        }
        long t = s + 1;
        boolean uin = vbl + out <= s << 2;
        boolean win = (t << 2) + out <= vbr;
        if (uin != win) {
            finish(uin ? s : t, k + dk, result);
            return;
        }
        long cmp = vb - (s + t << 1);
        finish(cmp < 0 || cmp == 0 && (s & 1) == 0 ? s : t, k + dk, result);
    }

    private static long rop(long g1, long g0, long cp) {
        long x1 = multiplyHigh(g0, cp);
        long y0 = g1 * cp;
        long y1 = multiplyHigh(g1, cp);
        long z = (y0 >>> 1) + x1;
        long vbp = y1 + (z >>> 63);
        return vbp | ((z & Long.MAX_VALUE) + Long.MAX_VALUE) >>> 63;
    }

    private static long multiplyHigh(long x, long y) {
        long mask = 0xffffffffL;
        long lo = (x & mask) * (y & mask);
        long middle = (x >>> 32) * (y & mask) + (lo >>> 32);
        long carry = middle >>> 32;
        middle = (middle & mask) + (x & mask) * (y >>> 32);
        return (x >>> 32) * (y >>> 32) + carry + (middle >>> 32) - ((x >> 63) & y) - ((y >> 63) & x);
    }

    private static void finish(long f, int exponent, DoubleAnalyzer.Result result) {
        while (f < DoubleAnalyzer.DOUBLE_MAX_POS) {
            f *= 10;
            --exponent;
        }
        result.mantissa = f;
        result.exponent = exponent + DoubleAnalyzer.DECIMAL_PRECISION - 1;
    }
}
