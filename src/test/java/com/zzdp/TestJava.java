package com.zzdp;

import org.junit.Test;

/**
 * @Author
 * @Description
 * @dATE 2023/7/4 15:14
 **/
public class TestJava {


    @Test
    public void testJava() {
        System.out.println("hello");
    }

    //1.统计回文数的个数，11,4343,5523,121,113311，回文：正反的值都相同，

    public String test1(int n) {

        if (n <= 0){
            return "请输入大于0的整数";
        }

        String num = String.valueOf(n);

        int length = num.length();

//        数字长度小于4的情况
        if (length < 4) {
            char[] chars = num.toCharArray();
            if (length == 3) {
                if (Character.toString(chars[0]).equals(Character.toString(chars[2]))) {
                    return "这串数字为回文数";
                } else
                    return "这串数字不为回文数";
            }
            if (length == 2) {
                if (Character.toString(chars[0]).equals(Character.toString(chars[1]))) {
                    return "这串数字为回文数";
                } else
                    return "这串数字不为回文数";
            }
            if (length == 1) {
                return "这个数字为回文数";
            }
        }


//        偶数情况
        if (length % 2 == 0) {
            String substring = num.substring(length / 2);

            char[] chars1 = substring.toCharArray();
            char[] chars2 = new char[chars1.length];
            for (int i = chars1.length; i > 0; i--) {
                chars2[chars1.length - i] = chars1[i - 1];
            }

            String s2 = new String(chars2);
            String s1 = num.substring(0, length / 2);

            if (s2.equals(s1)) {
                return "这串数字为回文数";
            } else {
                return "这串数字不为回文数";
            }
        } else {
//            奇数情况
            String substring = num.substring(length / 2 + 1);

            char[] chars1 = substring.toCharArray();
            char[] chars2 = new char[chars1.length];
            for (int i = chars1.length; i > 0; i--) {
                chars2[chars1.length - i] = chars1[i - 1];
            }

            String s2 = new String(chars2);
            String s1 = num.substring(0, length / 2);

            if (s2.equals(s1)) {
                return "这串数字为回文数";
            } else {
                return "这串数字不为回文数";
            }
        }

    }

    @Test
    public void testNumber() {
        System.out.println(test1(-44));
    }


}
