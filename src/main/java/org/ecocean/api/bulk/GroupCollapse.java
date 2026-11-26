package org.ecocean.api.bulk;

// groups bulk-import rows that share an Encounter.groupKey into one encounter
public final class GroupCollapse {
    public static final String GROUP_KEY = "Encounter.groupKey";

    private GroupCollapse() {}

    // ASCII-only case fold: A-Z -> a-z on UTF-16 units; every other character is unchanged
    public static String foldKey(String trimmedKey) {
        StringBuilder sb = new StringBuilder(trimmedKey.length());
        for (int i = 0; i < trimmedKey.length(); i++) {
            char c = trimmedKey.charAt(i);
            sb.append((c >= 'A' && c <= 'Z') ? (char)(c + 32) : c);
        }
        return sb.toString();
    }
}
