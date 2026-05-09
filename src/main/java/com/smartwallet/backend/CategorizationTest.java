
package com.smartwallet.backend;

import java.util.regex.Pattern;

public class CategorizationTest {
    public static void main(String[] args) {
        testMatches("cadeau", "cadeau");
        testMatches("un cadeau", "cadeau");
        testMatches("cadeaux", "cadeau");
        testMatches("Cadeau", "cadeau");
        testMatches("kdo", "kdo");
        testMatches("taxi", "taxi");
    }

    private static void testMatches(String text, String keyword) {
        boolean result = matches(text, keyword);
        System.out.println("Testing '" + text + "' with keyword '" + keyword + "' -> Result: " + result);
    }

    private static boolean matches(String text, String... keywords) {
        if (text == null || text.isEmpty()) return false;
        String lowerText = text.toLowerCase().trim();

        for (String k : keywords) {
            String lowerK = k.toLowerCase().trim();
            
            boolean matched = false;
            if (lowerK.length() <= 4) {
                if (lowerText.matches(".*\\b" + Pattern.quote(lowerK) + "\\b.*")) {
                    if (!lowerText.equals("un") && !lowerText.equals("le")) {
                        matched = true;
                    }
                }
            } else {
                if (lowerText.contains(lowerK)) matched = true;
                else if (diceCoefficient(lowerText, lowerK) > 0.80) matched = true;
            }

            if (matched) {
                return true;
            }
        }
        return false;
    }

    private static double diceCoefficient(String s1, String s2) {
        if (s1.equals(s2)) return 1.0;
        if (s1.length() < 2 || s2.length() < 2) return 0;
        java.util.Set<String> bigrams1 = new java.util.HashSet<>();
        for (int i = 0; i < s1.length() - 1; i++) bigrams1.add(s1.substring(i, i + 2));
        java.util.Set<String> bigrams2 = new java.util.HashSet<>();
        for (int i = 0; i < s2.length() - 1; i++) bigrams2.add(s2.substring(i, i + 2));
        int intersection = 0;
        for (String b : bigrams1) if (bigrams2.contains(b)) intersection++;
        return (2.0 * intersection) / (bigrams1.size() + bigrams2.size());
    }
}
