package com.learnmap.evidence;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Locale;

/**
 * 自动判分：去掉空白、统一全角半角后比较；两边都是数（整数、小数、分数）时按数值等价判定，
 * 例如 1/2 和 0.5 都算对。表达式等价判定（符号计算）后续接入。
 */
public final class AnswerGrader {

    private AnswerGrader() {
    }

    public static boolean matches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        String e = normalize(expected);
        String a = normalize(actual);
        if (e.equals(a)) {
            return true;
        }
        BigDecimal ne = number(e);
        BigDecimal na = number(a);
        return ne != null && na != null && ne.subtract(na).abs().compareTo(new BigDecimal("1e-9")) < 0;
    }

    static String normalize(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '　') {
                continue;
            }
            if (c >= '！' && c <= '～') {
                c = (char) (c - 0xFEE0);
            }
            if (!Character.isWhitespace(c)) {
                sb.append(c);
            }
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }

    static BigDecimal number(String s) {
        try {
            int slash = s.indexOf('/');
            if (slash > 0) {
                BigDecimal num = new BigDecimal(s.substring(0, slash));
                BigDecimal den = new BigDecimal(s.substring(slash + 1));
                return den.signum() == 0 ? null : num.divide(den, MathContext.DECIMAL64);
            }
            return new BigDecimal(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
