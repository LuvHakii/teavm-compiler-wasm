/*
 *  Copyright 2017 Alexey Andreev.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
// Modified copy of TeaVM's TFormatter. Differences from upstream: %n, %e/%E and %g/%G are supported,
// %% and %n no longer consume an argument, a missing argument raises MissingFormatArgumentException,
// floating point is rounded HALF_UP from the shortest decimal digits like the JDK (not by DecimalFormat),
// %d accepts BigInteger and Long.MIN_VALUE, and no java.text locale data is used (US separators).
package org.teavm.classlib.java.util;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.DuplicateFormatFlagsException;
import java.util.FormatFlagsConversionMismatchException;
import java.util.IllegalFormatConversionException;
import java.util.Locale;
import java.util.MissingFormatArgumentException;
import java.util.UnknownFormatConversionException;
import org.teavm.classlib.impl.IntegerUtil;

public final class TFormatter implements Closeable, Flushable {
    private Locale locale;
    private Appendable out;
    private IOException ioException;

    public TFormatter() {
        this(Locale.getDefault());
    }

    public TFormatter(Appendable a) {
        this(a, Locale.getDefault());
    }

    public TFormatter(Locale l) {
        this(new StringBuilder(), l);
    }

    public TFormatter(Appendable a, Locale l) {
        out = a;
        locale = l;
    }

    public TFormatter(PrintStream ps) {
        this(new OutputStreamWriter(ps));
    }

    public TFormatter(OutputStream os) {
        this(new OutputStreamWriter(os));
    }

    public TFormatter(OutputStream os, String csn) throws UnsupportedEncodingException {
        this(new OutputStreamWriter(os, csn));
    }

    public TFormatter(OutputStream os, String csn, Locale l) throws UnsupportedEncodingException {
        this(new OutputStreamWriter(os, csn), l);
    }

    public Locale locale() {
        requireOpen();
        return locale;
    }

    public Appendable out() {
        requireOpen();
        return out;
    }

    private void requireOpen() {
        if (out == null) {
            throw new TFormatterClosedException();
        }
    }

    @Override
    public String toString() {
        requireOpen();
        return out.toString();
    }

    @Override
    public void flush() {
        requireOpen();
        if (out instanceof Flushable) {
            try {
                ((Flushable) out).flush();
            } catch (IOException e) {
                ioException = e;
            }
        }
    }

    @Override
    public void close() {
        requireOpen();
        try {
            if (out instanceof Closeable) {
                ((Closeable) out).close();
            }
        } catch (IOException e) {
            ioException = e;
        } finally {
            out = null;
        }
    }

    public IOException ioException() {
        return ioException;
    }

    public TFormatter format(String format, Object... args) {
        return format(locale, format, args);
    }

    public TFormatter format(Locale l, String format, Object... args) {
        requireOpen();
        try {
            if (args == null) {
                args = new Object[1];
            }
            new FormatWriter(this, out, format, args).write();
        } catch (IOException e) {
            ioException = e;
        }
        return this;
    }

    static class FormatWriter {
        private static final String FORMAT_FLAGS = "--#+ 0,(<";
        private static final int MASK_FOR_GENERAL_FORMAT =
                TFormattableFlags.LEFT_JUSTIFY | TFormattableFlags.ALTERNATE
                        | TFormattableFlags.UPPERCASE | TFormattableFlags.PREVIOUS_ARGUMENT;
        private static final int MASK_FOR_CHAR_FORMAT =
                TFormattableFlags.LEFT_JUSTIFY | TFormattableFlags.UPPERCASE | TFormattableFlags.PREVIOUS_ARGUMENT;
        private static final int MASK_FOR_INT_DECIMAL_FORMAT = MASK_FOR_GENERAL_FORMAT ^ TFormattableFlags.ALTERNATE
                | TFormattableFlags.LEADING_SPACE | TFormattableFlags.ZERO_PADDED
                | TFormattableFlags.PARENTHESIZED_NEGATIVE | TFormattableFlags.SIGNED
                | TFormattableFlags.GROUPING_SEPARATOR;
        private static final int MASK_FOR_INT_RADIX_FORMAT = MASK_FOR_GENERAL_FORMAT
                | TFormattableFlags.ZERO_PADDED | TFormattableFlags.PARENTHESIZED_NEGATIVE;
        private TFormatter formatter;
        Appendable out;
        String format;
        Object[] args;
        int index;
        int formatSpecifierStart;
        int defaultArgumentIndex;
        int argumentIndex;
        int previousArgumentIndex;
        int width;
        int precision;
        int flags;

        FormatWriter(TFormatter formatter, Appendable out, String format, Object[] args) {
            this.formatter = formatter;
            this.out = out;
            this.format = format;
            this.args = args;
        }

        void write() throws IOException {
            while (true) {
                int next = format.indexOf('%', index);
                if (next < 0) {
                    out.append(format.substring(index));
                    break;
                }
                out.append(format.substring(index, next));
                index = next + 1;

                formatSpecifierStart = index;
                char specifier = parseFormatSpecifier();
                if (specifier == '%') {
                    formatGivenString(false, "%");
                } else if (specifier == 'n') {
                    out.append('\n');
                } else {
                    configureFormat();
                    formatValue(specifier);
                }
            }
        }

        private Object arg() {
            if (argumentIndex >= args.length) {
                throw new MissingFormatArgumentException("%" + format.substring(formatSpecifierStart, index));
            }
            return args[argumentIndex];
        }

        private void formatValue(char specifier) throws IOException {
            switch (specifier) {
                case 'b':
                    formatBoolean(specifier, false);
                    break;
                case 'B':
                    formatBoolean(specifier, true);
                    break;
                case 'h':
                    formatHex(specifier, false);
                    break;
                case 'H':
                    formatHex(specifier, true);
                    break;
                case 's':
                    formatString(specifier, false);
                    break;
                case 'S':
                    formatString(specifier, true);
                    break;

                case 'c':
                    formatChar(specifier, false);
                    break;
                case 'C':
                    formatChar(specifier, true);
                    break;

                case 'd':
                    formatDecimalInt(specifier, false);
                    break;
                case 'D':
                    formatDecimalInt(specifier, true);
                    break;

                case 'o':
                    formatRadixInt(specifier, 3, false);
                    break;
                case 'O':
                    formatRadixInt(specifier, 3, true);
                    break;

                case 'x':
                    formatRadixInt(specifier, 4, false);
                    break;
                case 'X':
                    formatRadixInt(specifier, 4, true);
                    break;

                case 'f':
                    formatFloating(specifier, 'f', false);
                    break;
                case 'e':
                    formatFloating(specifier, 'e', false);
                    break;
                case 'E':
                    formatFloating(specifier, 'e', true);
                    break;
                case 'g':
                    formatFloating(specifier, 'g', false);
                    break;
                case 'G':
                    formatFloating(specifier, 'g', true);
                    break;
                default:
                    throw new UnknownFormatConversionException(String.valueOf(specifier));
            }
        }

        private void formatFloating(char specifier, char kind, boolean upperCase) throws IOException {
            verifyFlags(specifier, kind == 'f' ? MASK_FOR_INT_DECIMAL_FORMAT
                    : MASK_FOR_INT_DECIMAL_FORMAT & ~TFormattableFlags.GROUPING_SEPARATOR);
            verifyFloatFlags();

            Object arg = arg();
            BigDecimal value;
            boolean negative;
            if (arg instanceof Double || arg instanceof Float) {
                double d = ((Number) arg).doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    formatNonFinite(d, upperCase);
                    return;
                }
                negative = d < 0 || d == 0 && 1 / d < 0;
                value = new BigDecimal(Double.toString(Math.abs(d)));
            } else if (arg instanceof BigDecimal) {
                negative = ((BigDecimal) arg).signum() < 0;
                value = ((BigDecimal) arg).abs();
            } else if (arg == null) {
                formatGivenString(upperCase, "null");
                return;
            } else {
                throw new IllegalFormatConversionException(specifier, arg.getClass());
            }

            if (precision == -1) {
                precision = 6;
            }
            String body;
            switch (kind) {
                case 'f':
                    body = fixed(value, precision);
                    if ((flags & TFormattableFlags.GROUPING_SEPARATOR) != 0) {
                        body = group(body);
                    }
                    break;
                case 'e':
                    body = scientific(value, precision);
                    break;
                default:
                    body = general(value, precision);
                    break;
            }
            emitSigned(negative, body, upperCase);
        }

        private void formatNonFinite(double d, boolean upperCase) throws IOException {
            String str;
            if (Double.isNaN(d)) {
                str = "NaN";
            } else if (d < 0) {
                str = (flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0 ? "(Infinity)" : "-Infinity";
            } else if ((flags & TFormattableFlags.SIGNED) != 0) {
                str = "+Infinity";
            } else {
                str = (flags & TFormattableFlags.LEADING_SPACE) != 0 ? " Infinity" : "Infinity";
            }
            precision = -1;
            formatGivenString(upperCase, str);
        }

        private void emitSigned(boolean negative, String body, boolean upperCase) throws IOException {
            StringBuilder sb = new StringBuilder();
            String suffix = "";
            if (negative) {
                if ((flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                    sb.append('(');
                    suffix = ")";
                } else {
                    sb.append('-');
                }
            } else if ((flags & TFormattableFlags.SIGNED) != 0) {
                sb.append('+');
            } else if ((flags & TFormattableFlags.LEADING_SPACE) != 0) {
                sb.append(' ');
            }
            if ((flags & TFormattableFlags.ZERO_PADDED) != 0) {
                for (int i = sb.length() + body.length() + suffix.length(); i < width; ++i) {
                    sb.append('0');
                }
            }
            precision = -1; // prevent formatGivenString from trimming
            formatGivenString(upperCase, sb.append(body).append(suffix).toString());
        }

        private static String fixed(BigDecimal value, int digits) {
            return value.setScale(digits, RoundingMode.HALF_UP).toPlainString();
        }

        // Decimal exponent of the value once rounded to digits + 1 significant digits (BigDecimal.round does
        // not honour HALF_UP on ties in TeaVM, setScale does).
        private static int exponentOf(BigDecimal value, int digits) {
            if (value.signum() == 0) {
                return 0;
            }
            int exponent = value.precision() - value.scale() - 1;
            if (value.movePointLeft(exponent).setScale(digits, RoundingMode.HALF_UP).compareTo(BigDecimal.TEN) >= 0) {
                ++exponent;
            }
            return exponent;
        }

        private static String scientific(BigDecimal value, int digits) {
            int exponent = exponentOf(value, digits);
            StringBuilder sb = new StringBuilder(
                    value.movePointLeft(exponent).setScale(digits, RoundingMode.HALF_UP).toPlainString());
            sb.append('e').append(exponent < 0 ? '-' : '+');
            if (Math.abs(exponent) < 10) {
                sb.append('0');
            }
            return sb.append(Math.abs(exponent)).toString();
        }

        private static String general(BigDecimal value, int digits) {
            if (digits == 0) {
                digits = 1;
            }
            int exponent = exponentOf(value, digits - 1);
            if (value.signum() == 0 || exponent >= -4 && exponent < digits) {
                return fixed(value, Math.max(0, digits - exponent - 1));
            }
            return scientific(value, digits - 1);
        }

        private static String group(String number) {
            int dot = number.indexOf('.');
            String integer = dot < 0 ? number : number.substring(0, dot);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < integer.length(); ++i) {
                if (i > 0 && (integer.length() - i) % 3 == 0) {
                    sb.append(',');
                }
                sb.append(integer.charAt(i));
            }
            return sb.append(number, integer.length(), number.length()).toString();
        }

        private void verifyFloatFlags() {
            if ((flags & TFormattableFlags.SIGNED) != 0 && (flags & TFormattableFlags.LEADING_SPACE) != 0) {
                throw new TIllegalFormatFlagsException("+ ");
            }
            if ((flags & TFormattableFlags.ZERO_PADDED) != 0 && (flags & TFormattableFlags.LEFT_JUSTIFY) != 0) {
                throw new TIllegalFormatFlagsException("0-");
            }
            if ((flags & (TFormattableFlags.LEFT_JUSTIFY | TFormattableFlags.ZERO_PADDED)) != 0 && width < 0) {
                throw new TMissingFormatWidthException(format.substring(formatSpecifierStart, index));
            }
        }

        private void formatBoolean(char specifier, boolean upperCase) throws IOException {
            verifyFlagsForGeneralFormat(specifier);
            Object arg = arg();
            String s = Boolean.toString(arg instanceof Boolean ? (Boolean) arg : arg != null);
            formatGivenString(upperCase, s);
        }

        private void formatHex(char specifier, boolean upperCase) throws IOException {
            verifyFlagsForGeneralFormat(specifier);
            Object arg = arg();
            String s = arg != null ? Integer.toHexString(arg.hashCode()) : "null";
            formatGivenString(upperCase, s);
        }

        private void formatString(char specifier, boolean upperCase) throws IOException {
            verifyFlagsForGeneralFormat(specifier);
            Object arg = arg();
            if (arg instanceof TFormattable) {
                int flagsToPass = flags & 7;
                if (upperCase) {
                    flagsToPass |= TFormattableFlags.UPPERCASE;
                }
                ((TFormattable) arg).formatTo(formatter, flagsToPass, width, precision);
            } else {
                formatGivenString(upperCase, String.valueOf(arg));
            }
        }

        private void formatChar(char specifier, boolean upperCase) throws IOException {
            verifyFlags(specifier, MASK_FOR_CHAR_FORMAT);
            Object arg = arg();

            if (precision >= 0) {
                throw new TIllegalFormatPrecisionException(precision);
            }

            int c;
            if (arg instanceof Character) {
                c = (Character) arg;
            } else if (arg instanceof Byte) {
                c = (char) (byte) arg;
            } else if (arg instanceof Short) {
                c = (char) (short) arg;
            } else if (arg instanceof Integer) {
                c = (int) arg;
                if (!Character.isValidCodePoint(c)) {
                    throw new TIllegalFormatCodePointException(c);
                }
            } else if (arg == null) {
                formatGivenString(upperCase, "null");
                return;
            } else {
                throw new IllegalFormatConversionException(specifier, arg.getClass());
            }

            formatGivenString(upperCase, new String(Character.toChars(c)));
        }

        private void formatDecimalInt(char specifier, boolean upperCase) throws IOException {
            verifyFlags(specifier, MASK_FOR_INT_DECIMAL_FORMAT);
            verifyIntFlags();

            String str;
            Object arg = arg();
            boolean negative;
            if (arg instanceof Long || arg instanceof Integer || arg instanceof Byte || arg instanceof Short) {
                long value = ((Number) arg).longValue();
                negative = value < 0;
                str = negative ? Long.toString(value).substring(1) : Long.toString(value);
            } else if (arg instanceof BigInteger) {
                negative = ((BigInteger) arg).signum() < 0;
                str = ((BigInteger) arg).abs().toString();
            } else {
                throw new IllegalFormatConversionException(specifier, arg != null ? arg.getClass() : null);
            }

            int additionalSymbols = 0;
            StringBuilder sb = new StringBuilder();
            if (negative) {
                if ((flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                    sb.append('(');
                    additionalSymbols += 2;
                } else {
                    sb.append('-');
                    additionalSymbols++;
                }
            } else {
                if ((flags & TFormattableFlags.SIGNED) != 0) {
                    sb.append('+');
                    additionalSymbols++;
                } else if ((flags & TFormattableFlags.LEADING_SPACE) != 0) {
                    sb.append(' ');
                    additionalSymbols++;
                }
            }

            StringBuilder valueSb = new StringBuilder();
            valueSb.append((flags & TFormattableFlags.GROUPING_SEPARATOR) != 0 ? group(str) : str);

            if ((flags & TFormattableFlags.ZERO_PADDED) != 0) {
                int actual = valueSb.length() + additionalSymbols;
                for (int i = actual; i < width; ++i) {
                    sb.append(Character.forDigit(0, 10));
                }
            }
            sb.append(valueSb);

            if (negative && (flags & TFormattableFlags.PARENTHESIZED_NEGATIVE) != 0) {
                sb.append(')');
            }

            formatGivenString(upperCase, sb.toString());
        }

        private void formatRadixInt(char specifier, int radixLog2, boolean upperCase) throws IOException {
            verifyFlags(specifier, MASK_FOR_INT_RADIX_FORMAT);
            verifyIntFlags();

            String str;
            Object arg = arg();
            if (arg instanceof Long) {
                str = IntegerUtil.toUnsignedLogRadixString((Long) arg, radixLog2);
            } else if (arg instanceof Integer) {
                str = IntegerUtil.toUnsignedLogRadixString((Integer) arg, radixLog2);
            } else if (arg instanceof Short) {
                str = IntegerUtil.toUnsignedLogRadixString((Short) arg & 0xFFFF, radixLog2);
            } else if (arg instanceof Byte) {
                str = IntegerUtil.toUnsignedLogRadixString((Byte) arg & 0xFF, radixLog2);
            } else {
                throw new IllegalFormatConversionException(specifier, arg != null ? arg.getClass() : null);
            }

            StringBuilder sb = new StringBuilder();
            if ((flags & TFormattableFlags.ALTERNATE) != 0) {
                String prefix = radixLog2 == 4 ? "0x" : "0";
                str = prefix + str;
            }

            if ((flags & TFormattableFlags.ZERO_PADDED) != 0) {
                for (int i = str.length(); i < width; ++i) {
                    sb.append(Character.forDigit(0, 10));
                }
            }
            sb.append(str);

            formatGivenString(upperCase, sb.toString());
        }

        private void verifyIntFlags() {
            if ((flags & TFormattableFlags.SIGNED) != 0 && (flags & TFormattableFlags.LEADING_SPACE) != 0) {
                throw new TIllegalFormatFlagsException("+ ");
            }
            if ((flags & TFormattableFlags.ZERO_PADDED) != 0 && (flags & TFormattableFlags.LEFT_JUSTIFY) != 0) {
                throw new TIllegalFormatFlagsException("0-");
            }
            if (precision >= 0) {
                throw new TIllegalFormatPrecisionException(precision);
            }
            if ((flags & (TFormattableFlags.LEFT_JUSTIFY | TFormattableFlags.ZERO_PADDED)) != 0 && width < 0) {
                throw new TMissingFormatWidthException(format.substring(formatSpecifierStart, index));
            }
        }

        private void formatGivenString(boolean upperCase, String str) throws IOException {
            if (precision > 0 && precision < str.length()) {
                str = str.substring(0, precision);
            }

            if (upperCase) {
                str = str.toUpperCase();
            }

            if ((flags & TFormattableFlags.LEFT_JUSTIFY) != 0) {
                out.append(str);
                mayBeAppendSpaces(str);
            } else {
                mayBeAppendSpaces(str);
                out.append(str);
            }
        }

        private void verifyFlagsForGeneralFormat(char conversion) {
            verifyFlags(conversion, MASK_FOR_GENERAL_FORMAT);
        }

        private void verifyFlags(char conversion, int mask) {
            if ((flags | mask) != mask) {
                throw new FormatFlagsConversionMismatchException(flagsToString(flags & ~mask), conversion);
            }
        }

        private String flagsToString(int flags) {
            int flagIndex = Integer.numberOfTrailingZeros(flags);
            return String.valueOf(FORMAT_FLAGS.charAt(flagIndex));
        }

        private void mayBeAppendSpaces(String str) throws IOException {
            if (width > str.length()) {
                int diff = width - str.length();
                StringBuilder sb = new StringBuilder(diff);
                for (int i = 0; i < diff; ++i) {
                    sb.append(' ');
                }
                out.append(sb);
            }
        }

        private void configureFormat() {
            if ((flags & TFormattableFlags.PREVIOUS_ARGUMENT) != 0) {
                argumentIndex = Math.max(0, previousArgumentIndex);
            }

            if (argumentIndex == -1) {
                argumentIndex = defaultArgumentIndex++;
            }
            previousArgumentIndex = argumentIndex;
        }

        private char parseFormatSpecifier() {
            flags = 0;
            argumentIndex = -1;
            width = -1;
            precision = -1;

            char c = format.charAt(index);

            if (c != '0' && isDigit(c)) {
                int n = readInt();
                if (index < format.length() && format.charAt(index) == '$') {
                    index++;
                    argumentIndex = n - 1;
                } else {
                    width = n;
                }
            }
            parseFlags();

            if (width < 0 && index < format.length() && isDigit(format.charAt(index))) {
                width = readInt();
            }

            if (index < format.length() && format.charAt(index) == '.') {
                index++;
                if (index >= format.length() || !isDigit(format.charAt(index))) {
                    throw new UnknownFormatConversionException(String.valueOf(format.charAt(index - 1)));
                }
                precision = readInt();
            }

            if (index >= format.length()) {
                throw new UnknownFormatConversionException(String.valueOf(format.charAt(format.length() - 1)));
            }
            return format.charAt(index++);
        }

        private void parseFlags() {
            while (index < format.length()) {
                char c = format.charAt(index);
                int flag;
                switch (c) {
                    case '-':
                        flag = TFormattableFlags.LEFT_JUSTIFY;
                        break;
                    case '#':
                        flag = TFormattableFlags.ALTERNATE;
                        break;
                    case '+':
                        flag = TFormattableFlags.SIGNED;
                        break;
                    case ' ':
                        flag = TFormattableFlags.LEADING_SPACE;
                        break;
                    case '0':
                        flag = TFormattableFlags.ZERO_PADDED;
                        break;
                    case ',':
                        flag = TFormattableFlags.GROUPING_SEPARATOR;
                        break;
                    case '(':
                        flag = TFormattableFlags.PARENTHESIZED_NEGATIVE;
                        break;
                    case '<':
                        flag = TFormattableFlags.PREVIOUS_ARGUMENT;
                        break;
                    default:
                        return;
                }
                if ((flags & flag) != 0) {
                    throw new DuplicateFormatFlagsException(String.valueOf(c));
                }
                flags |= flag;
                index++;
            }
        }

        private int readInt() {
            int result = 0;
            while (index < format.length() && isDigit(format.charAt(index))) {
                result = result * 10 + (format.charAt(index++) - '0');
            }
            return result;
        }

        private static boolean isDigit(char c) {
            return c >= '0' && c <= '9';
        }
    }
}
