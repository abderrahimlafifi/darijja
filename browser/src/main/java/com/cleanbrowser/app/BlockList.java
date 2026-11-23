package com.cleanbrowser.app;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.Locale;

/**
 * A set of blocked domains. A domain blocks itself and all of its subdomains:
 * "doubleclick.net" also blocks "ad.doubleclick.net".
 *
 * Understands the common list formats:
 *   hosts files       0.0.0.0 ads.example.com
 *   plain domains     ads.example.com
 *   adblock / DNS     ||ads.example.com^   and exceptions  @@||good.example.com^
 *
 * Pure Java (no Android classes) so it can be unit tested on the JVM.
 */
public final class BlockList {

    private final HashSet<String> blocked;
    private final HashSet<String> allowed = new HashSet<>();

    public BlockList() {
        this(1024);
    }

    public BlockList(int expectedSize) {
        blocked = new HashSet<>(Math.max(16, expectedSize * 4 / 3));
    }

    public int size() {
        return blocked.size();
    }

    public void add(String domain) {
        String d = cleanDomain(domain);
        if (d != null) blocked.add(d);
    }

    public void addException(String domain) {
        String d = cleanDomain(domain);
        if (d != null) allowed.add(d);
    }

    public void addAll(BlockList other) {
        blocked.addAll(other.blocked);
        allowed.addAll(other.allowed);
    }

    /**
     * True when the host or one of its parent domains is on the list.
     * The most specific entry wins, so an exception for "cdn.example.com"
     * beats a block of "example.com".
     */
    public boolean isBlocked(String host) {
        if (host == null || host.isEmpty()) return false;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.endsWith(".")) h = h.substring(0, h.length() - 1);
        if (h.startsWith("[") || isIpAddress(h)) return blocked.contains(h);
        String part = h;
        while (true) {
            if (allowed.contains(part)) return false;
            if (blocked.contains(part)) return true;
            int dot = part.indexOf('.');
            if (dot < 0) return false;
            part = part.substring(dot + 1);
            // Never match a bare top level domain such as "com".
            if (part.indexOf('.') < 0) return false;
        }
    }

    /** Reads a whole list. Returns the number of entries added. */
    public int parse(BufferedReader reader) throws IOException {
        int added = 0;
        boolean[] exception = new boolean[1];
        String line;
        while ((line = reader.readLine()) != null) {
            String d = parseLine(line, exception);
            if (d == null) continue;
            if (exception[0]) {
                allowed.add(d);
            } else if (blocked.add(d)) {
                added++;
            }
        }
        return added;
    }

    /** Writes the list in a compact one-entry-per-line form that {@link #parse} reads back. */
    public void write(Writer out) throws IOException {
        for (String d : blocked) {
            out.write(d);
            out.write('\n');
        }
        for (String d : allowed) {
            out.write("@@||");
            out.write(d);
            out.write("^\n");
        }
    }

    /**
     * Extracts the domain from one list line, or returns null if the line is a
     * comment, a cosmetic rule or something this simple matcher cannot use.
     * exception[0] is set to true for "@@" allow rules.
     */
    public static String parseLine(String raw, boolean[] exception) {
        exception[0] = false;
        if (raw == null) return null;
        String line = raw.trim();
        if (line.isEmpty()) return null;
        char c = line.charAt(0);
        if (c == '#' || c == '!' || c == '[' || c == '/') return null;
        // Cosmetic (element hiding) rules are not domain rules.
        if (line.contains("##") || line.contains("#@#") || line.contains("#?#") || line.contains("#$#")) return null;

        if (line.startsWith("@@")) {
            exception[0] = true;
            line = line.substring(2);
        }

        if (line.startsWith("||")) {
            String body = line.substring(2);
            int dollar = body.indexOf('$');
            if (dollar >= 0) {
                String options = body.substring(dollar + 1);
                // Only keep rules that apply everywhere. Rules limited to some clients,
                // rewrites or "badfilter" would block the wrong things here.
                if (!options.equals("important") && !options.equals("all") && !options.equals("third-party")
                        && !options.equals("3p") && !options.equals("important,third-party")) {
                    return null;
                }
                body = body.substring(0, dollar);
            }
            int end = body.length();
            for (int i = 0; i < body.length(); i++) {
                char ch = body.charAt(i);
                if (ch == '^' || ch == '/' || ch == '|') {
                    end = i;
                    break;
                }
            }
            // Anything after the domain other than a separator means a URL rule, skip it.
            String rest = body.substring(end);
            if (!rest.isEmpty() && !rest.equals("^") && !rest.equals("^|") && !rest.equals("|")) return null;
            return cleanDomain(body.substring(0, end));
        }

        if (exception[0]) {
            // "@@" without "||" is a URL pattern, not a domain.
            exception[0] = false;
            return null;
        }

        // Hosts file or plain domain line. Drop trailing comments.
        int hash = line.indexOf('#');
        if (hash >= 0) line = line.substring(0, hash).trim();
        if (line.isEmpty()) return null;
        String[] parts = line.split("\\s+");
        String candidate;
        if (parts.length >= 2 && isIpAddress(parts[0])) {
            candidate = parts[1];
        } else if (parts.length == 1) {
            candidate = parts[0];
        } else {
            return null;
        }
        return cleanDomain(candidate);
    }

    /** Lower case, strips "*." and dots at the ends, and rejects anything that is not a host name. */
    public static String cleanDomain(String raw) {
        if (raw == null) return null;
        String d = raw.trim().toLowerCase(Locale.ROOT);
        if (d.startsWith("*.")) d = d.substring(2);
        while (d.startsWith(".")) d = d.substring(1);
        while (d.endsWith(".")) d = d.substring(0, d.length() - 1);
        if (d.isEmpty() || d.length() > 253 || d.indexOf('.') < 0) return null;
        for (int i = 0; i < d.length(); i++) {
            char ch = d.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '-' || ch == '.' || ch == '_';
            if (!ok) return null;
        }
        if (d.contains("..")) return null;
        switch (d) {
            case "localhost.localdomain":
            case "local":
            case "broadcasthost":
            case "0.0.0.0":
            case "127.0.0.1":
                return null;
            default:
                return d;
        }
    }

    static boolean isIpAddress(String s) {
        if (s.isEmpty()) return false;
        if (s.indexOf(':') >= 0) return true; // IPv6 such as ::1 or ::
        int dots = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '.') {
                dots++;
            } else if (ch < '0' || ch > '9') {
                return false;
            }
        }
        return dots == 3;
    }

    private static final java.util.Set<String> SECOND_LEVEL = new HashSet<>(java.util.Arrays.asList(
            "com", "co", "org", "net", "gov", "gob", "edu", "ac", "or", "ne", "go", "mil", "nom", "ltd", "plc",
            "sch", "info", "biz"));

    /**
     * Rough registrable domain: the last two labels, or the last three for country
     * second level domains such as example.co.uk or site.com.ma.
     */
    public static String siteOf(String host) {
        if (host == null) return "";
        String h = host.toLowerCase(Locale.ROOT);
        if (h.startsWith("www.")) h = h.substring(4);
        if (isIpAddress(h)) return h;
        String[] labels = h.split("\\.");
        int n = labels.length;
        if (n <= 2) return h;
        int keep = (labels[n - 1].length() == 2 && SECOND_LEVEL.contains(labels[n - 2])) ? 3 : 2;
        StringBuilder sb = new StringBuilder();
        for (int i = n - keep; i < n; i++) {
            if (sb.length() > 0) sb.append('.');
            sb.append(labels[i]);
        }
        return sb.toString();
    }

    public static boolean sameSite(String a, String b) {
        if (a == null || b == null) return false;
        return siteOf(a).equals(siteOf(b));
    }
}
