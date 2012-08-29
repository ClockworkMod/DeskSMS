package com.koushikdutta.tabletsms;

public class NumberHelper {
    static public String strip(String number) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < number.length(); i++) {
            char c = number.charAt(i);
            if (c >= '0' && c <= '9')
                b.append(c);
        }
        if (b.length() == 0)
            return "";
        Long i = Long.parseLong(b.toString());
        return i.toString();
    }
    
    static public boolean areSimilar(String n1, String n2) {
        if (Helper.isJavaScriptNullOrEmpty(n1) || Helper.isJavaScriptNullOrEmpty(n2))
            return false;
        if (n1.equals(n2))
            return true;
        String cn1 = strip(n1);
        String cn2 = strip(n2);
        if (cn1.length() < 7 || cn2.length() < 7)
            return false;
        if (cn1.contains(cn2) || cn2.contains(cn1))
            return true;
        return false;
    }
}
