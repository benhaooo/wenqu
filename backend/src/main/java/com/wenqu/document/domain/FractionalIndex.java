package com.wenqu.document.domain;

/**
 * 分数索引：为同级节点生成可按字节序比较的排序键，在任意两个键之间都能生成新键，拖拽排序只需修改被移动的节点。
 *
 * <p>算法移植自 rocicorp/fractional-indexing（CC0）。键由「整数部分」和「小数部分」组成：整数部分首字符编码其长度，
 * 使连续追加到末尾时键长增长缓慢；小数部分用于在两个相邻整数之间插入。
 */
public final class FractionalIndex {

    private static final String DIGITS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final char ZERO = DIGITS.charAt(0);
    private static final char MAX = DIGITS.charAt(DIGITS.length() - 1);
    private static final String SMALLEST_INTEGER = "A" + String.valueOf(ZERO).repeat(26);

    private FractionalIndex() {}

    /**
     * 生成介于 before 与 after 之间的键。
     *
     * @param before 前一个键，null 表示没有（插到最前）
     * @param after 后一个键，null 表示没有（插到最后）
     */
    public static String between(String before, String after) {
        if (before != null) {
            validate(before);
        }
        if (after != null) {
            validate(after);
        }
        if (before != null && after != null && before.compareTo(after) >= 0) {
            throw new IllegalArgumentException(before + " >= " + after);
        }
        if (before == null) {
            if (after == null) {
                return "a" + ZERO;
            }
            String integer = integerPart(after);
            String fraction = after.substring(integer.length());
            if (integer.equals(SMALLEST_INTEGER)) {
                return integer + midpoint("", fraction);
            }
            if (integer.compareTo(after) < 0) {
                return integer;
            }
            String decremented = decrementInteger(integer);
            if (decremented == null) {
                throw new IllegalStateException("cannot decrement " + after);
            }
            return decremented;
        }
        String integerBefore = integerPart(before);
        String fractionBefore = before.substring(integerBefore.length());
        if (after == null) {
            String incremented = incrementInteger(integerBefore);
            return incremented == null ? integerBefore + midpoint(fractionBefore, null) : incremented;
        }
        String integerAfter = integerPart(after);
        String fractionAfter = after.substring(integerAfter.length());
        if (integerBefore.equals(integerAfter)) {
            return integerBefore + midpoint(fractionBefore, fractionAfter);
        }
        String incremented = incrementInteger(integerBefore);
        if (incremented == null) {
            throw new IllegalStateException("cannot increment " + before);
        }
        if (incremented.compareTo(after) < 0) {
            return incremented;
        }
        return integerBefore + midpoint(fractionBefore, null);
    }

    /** 小数部分的中点，after 为 null 表示上界为 1。 */
    private static String midpoint(String before, String after) {
        if (after != null) {
            int n = 0;
            while (n < after.length() && charAt(before, n) == after.charAt(n)) {
                n++;
            }
            if (n > 0) {
                return after.substring(0, n)
                        + midpoint(before.substring(Math.min(n, before.length())), after.substring(n));
            }
        }
        int digitBefore = before.isEmpty() ? 0 : DIGITS.indexOf(before.charAt(0));
        int digitAfter = after == null ? DIGITS.length() : DIGITS.indexOf(after.charAt(0));
        if (digitAfter - digitBefore > 1) {
            return String.valueOf(DIGITS.charAt(Math.round(0.5f * (digitBefore + digitAfter))));
        }
        if (after != null && after.length() > 1) {
            return after.substring(0, 1);
        }
        return DIGITS.charAt(digitBefore) + midpoint(before.isEmpty() ? "" : before.substring(1), null);
    }

    private static char charAt(String value, int index) {
        return index < value.length() ? value.charAt(index) : ZERO;
    }

    private static int integerLength(char head) {
        if (head >= 'a' && head <= 'z') {
            return head - 'a' + 2;
        }
        if (head >= 'A' && head <= 'Z') {
            return 'Z' - head + 2;
        }
        throw new IllegalArgumentException("invalid order key head: " + head);
    }

    private static String integerPart(String key) {
        int length = integerLength(key.charAt(0));
        if (length > key.length()) {
            throw new IllegalArgumentException("invalid order key: " + key);
        }
        return key.substring(0, length);
    }

    private static void validate(String key) {
        if (key.isEmpty() || key.equals(SMALLEST_INTEGER)) {
            throw new IllegalArgumentException("invalid order key: " + key);
        }
        String fraction = key.substring(integerPart(key).length());
        if (!fraction.isEmpty() && fraction.charAt(fraction.length() - 1) == ZERO) {
            throw new IllegalArgumentException("invalid order key: " + key);
        }
    }

    private static String incrementInteger(String integer) {
        char head = integer.charAt(0);
        StringBuilder digits = new StringBuilder(integer.substring(1));
        boolean carry = true;
        for (int i = digits.length() - 1; carry && i >= 0; i--) {
            int d = DIGITS.indexOf(digits.charAt(i)) + 1;
            if (d == DIGITS.length()) {
                digits.setCharAt(i, ZERO);
            } else {
                digits.setCharAt(i, DIGITS.charAt(d));
                carry = false;
            }
        }
        if (!carry) {
            return head + digits.toString();
        }
        if (head == 'Z') {
            return "a" + ZERO;
        }
        if (head == 'z') {
            return null;
        }
        char next = (char) (head + 1);
        if (next > 'a') {
            digits.append(ZERO);
        } else {
            digits.deleteCharAt(digits.length() - 1);
        }
        return next + digits.toString();
    }

    private static String decrementInteger(String integer) {
        char head = integer.charAt(0);
        StringBuilder digits = new StringBuilder(integer.substring(1));
        boolean borrow = true;
        for (int i = digits.length() - 1; borrow && i >= 0; i--) {
            int d = DIGITS.indexOf(digits.charAt(i)) - 1;
            if (d == -1) {
                digits.setCharAt(i, MAX);
            } else {
                digits.setCharAt(i, DIGITS.charAt(d));
                borrow = false;
            }
        }
        if (!borrow) {
            return head + digits.toString();
        }
        if (head == 'a') {
            return "Z" + MAX;
        }
        if (head == 'A') {
            return null;
        }
        char previous = (char) (head - 1);
        if (previous < 'Z') {
            digits.append(MAX);
        } else {
            digits.deleteCharAt(digits.length() - 1);
        }
        return previous + digits.toString();
    }
}
