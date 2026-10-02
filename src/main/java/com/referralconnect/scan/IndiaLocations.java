package com.referralconnect.scan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Recognises Indian locations in free-text job-board location strings and names their city. */
public final class IndiaLocations {

    private IndiaLocations() {
    }

    public static final String REMOTE_INDIA = "Remote (India)";
    public static final String OTHER_INDIA = "Other India";

    /** Canonical city → the spellings job boards use for it. Order is the display order in filters. */
    private static final Map<String, Pattern> CITIES = new LinkedHashMap<>();

    static {
        CITIES.put("Bengaluru", p("bengaluru|bangalore"));
        CITIES.put("Hyderabad", p("hyderabad|secunderabad"));
        CITIES.put("Pune", p("\\bpune\\b"));
        CITIES.put("Mumbai", p("mumbai|bombay|thane"));
        CITIES.put("Delhi NCR", p("\\bdelhi\\b|gurgaon|gurugram|noida|ghaziabad|faridabad|\\bncr\\b"));
        CITIES.put("Chennai", p("chennai|madras"));
        CITIES.put("Kolkata", p("kolkata|calcutta"));
        CITIES.put("Ahmedabad", p("ahmedabad|gandhinagar"));
    }

    private static final Pattern INDIA = p("\\bindia\\b|\\bbharat\\b|jaipur|kochi|cochin|thiruvananthapuram"
            + "|trivandrum|coimbatore|indore|chandigarh|mohali|nagpur|vadodara|surat|lucknow|bhubaneswar"
            + "|visakhapatnam|mysore|mysuru|mangalore|mangaluru|\\bgoa\\b");

    private static final Pattern REMOTE = p("remote|work from home|\\bwfh\\b|anywhere");

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /** Every filter choice offered to users, in display order. */
    public static List<String> cityChoices() {
        List<String> all = new ArrayList<>(CITIES.keySet());
        all.add(REMOTE_INDIA);
        all.add(OTHER_INDIA);
        return all;
    }

    public static boolean isIndia(String location) {
        if (location == null || location.isBlank()) {
            return false;
        }
        String l = location.toLowerCase(Locale.ROOT);
        if (INDIA.matcher(l).find()) {
            return true;
        }
        for (Pattern city : CITIES.values()) {
            if (city.matcher(l).find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Canonical cities mentioned in an Indian location, comma separated
     * (e.g. "Bengaluru, Hyderabad"); "Remote (India)" or "Other India" when no listed city appears.
     */
    public static String cities(String location) {
        String l = location == null ? "" : location.toLowerCase(Locale.ROOT);
        List<String> found = new ArrayList<>();
        for (Map.Entry<String, Pattern> e : CITIES.entrySet()) {
            if (e.getValue().matcher(l).find()) {
                found.add(e.getKey());
            }
        }
        if (found.isEmpty()) {
            found.add(REMOTE.matcher(l).find() ? REMOTE_INDIA : OTHER_INDIA);
        } else if (REMOTE.matcher(l).find()) {
            found.add(REMOTE_INDIA);
        }
        return String.join(", ", found);
    }
}
