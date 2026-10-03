package com.referralconnect.scan;

import com.referralconnect.model.Requirements;
import com.referralconnect.model.Requirements.Basis;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads eligibility out of a job posting: the minimum years of experience, the degree, the
 * graduating batch and the skills it mentions.
 *
 * <p>Years come from the posting's required qualifications when its description is available
 * ("Minimum 3 year(s) of experience is required", "5-7 years of experience", "2+ yrs"). Preferred
 * or nice-to-have sections are ignored, and so is the advanced-degree alternative many postings
 * offer ("… or 1 year of experience with a Master's degree"). When the posting states nothing,
 * the job level in the title gives an estimate ("Senior" ≈ 4+, "Staff" ≈ 8+, "Intern" = students),
 * which is always labelled as an estimate.
 */
public final class RequirementsExtractor {

    private RequirementsExtractor() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static final String YEARS = "(?:years?|yrs?|year\\(s\\))";
    private static final String NUM = "(\\d{1,2}(?:\\.\\d)?)";
    private static final String WORD_NUM = "(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen)";

    /** "3-5 years", "3 to 5 yrs", "3+ - 5 years". */
    private static final Pattern RANGE = p(NUM + "\\s*\\+?\\s*(?:[-–—‑‒−]|to)\\s*" + NUM + "\\s*\\+?\\s*" + YEARS);
    /** "5+ years", "5 years+", "5 or more years", "5 years or more / and above". */
    private static final Pattern PLUS = p(NUM + "\\s*(?:\\+|plus)\\s*" + YEARS + "|" + NUM + "\\s*" + YEARS
            + "\\s*(?:\\+|plus\\b|or more\\b|and above\\b|or above\\b)|" + NUM + "\\s*or more\\s*" + YEARS);
    /** "minimum 3 years", "at least 2 yrs", "over 4 years". */
    private static final Pattern AT_LEAST = p("(?:minimum(?: of)?|min\\.?|at\\s*least|more than|over|upwards of)\\s*"
            + NUM + "\\s*\\+?\\s*" + YEARS);
    /** "3 years of experience", "4 yrs experience". */
    private static final Pattern PLAIN = p("(?<![\\d.])" + NUM + "\\s*\\+?\\s*" + YEARS + "(?![a-z])");
    /** "two (2) years", "five years". */
    private static final Pattern WORDS = p("\\b" + WORD_NUM + "\\s*(?:\\(\\d{1,2}\\)\\s*)?\\+?\\s*" + YEARS + "(?![a-z])");

    private static final Map<String, Integer> WORD_VALUES = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12),
            Map.entry("fifteen", 15));

    /** The years in a clause must be about experience, not company history or schooling. */
    private static final Pattern EXPERIENCE_CONTEXT = p("experience|\\bexp\\b|expertise|industry|professional"
            + "|\\bwork|hands[- ]on|track record|background|develop|programming|coding|engineering|building"
            + "|designing|relevant|proven|\\bin (?:the )?(?:field|role|domain)|\\bof (?:java|python|sql|software|data)");
    /** Company history and the like: never about the candidate. */
    private static final Pattern BOILERPLATE = p("founded|anniversary|we have been|we've been|our (?:company|firm"
            + "|history|legacy|clients)|trusted by|years? (?:of|in) business|years? old|of age\\b|since \\d{4}"
            + "|over the (?:next|past|last)");
    /** Schooling, contracts and benefits; skipped unless the clause also talks about experience. */
    private static final Pattern NOT_EXPERIENCE = p("education|schooling|warranty|contract (?:of|for)|tenure of"
            + "|\\bbond\\b|parental|\\bleave\\b|insurance|retire|vesting|equity|clients|customers|serving");

    /** A clause offering the advanced-degree route ("… or a Master's degree and 1 year"). */
    private static final Pattern ADVANCED_DEGREE = p("master'?s|\\bm\\.?\\s?tech\\b|\\bm\\.\\s?s\\.?\\b|\\bms degree"
            + "|\\bmca\\b|\\bph\\.?\\s?d\\b|doctorate|advanced degree|graduate degree|\\bmba\\b");
    private static final Pattern OR = p("\\bor\\b");
    private static final Pattern BACHELOR = p("bachelor|\\bb\\.?\\s?tech\\b|\\bb\\.\\s?e\\.?\\b|\\bbe\\s*/\\s*b\\.?\\s?tech"
            + "|\\bbs\\b|\\bb\\.\\s?s\\.?\\b|\\bbsc\\b|\\bb\\.sc\\b|\\bbca\\b|undergraduate|\\bbachelors\\b|graduation in"
            + "|any graduate|graduate in");
    private static final Pattern MASTER = p("master'?s|\\bm\\.?\\s?tech\\b|\\bm\\.\\s?s\\.?\\b|\\bms (?:degree|in)\\b"
            + "|\\bmca\\b|\\bm\\.\\s?e\\.?\\b|\\bmsc\\b|\\bm\\.sc\\b");
    private static final Pattern PHD = p("\\bph\\.?\\s?d\\b|doctorate|doctoral");
    private static final Pattern DEGREE_FIELD = p("computer science|computer engineering|\\bcse\\b|\\bcs\\b"
            + "|information technology|(?-i:\\bIT\\b)|electronics|\\bece\\b|\\beee\\b|mathematics|\\bmaths?\\b|statistics"
            + "|data science|engineering|related (?:technical |quantitative )?(?:field|discipline|area)|stem"
            + "|quantitative");
    private static final Pattern EQUIVALENT = p("or equivalent (?:practical |work |industry )?experience");

    private static final Pattern FRESHER = p("\\bfreshers?\\b|fresh graduates?|\\bnew grad(?:uate)?s?\\b"
            + "|recent (?:college )?graduates?|entry[- ]level|no (?:prior )?(?:work )?experience (?:is )?required"
            + "|\\b0\\s*" + YEARS + "\\b|final[- ]year students?|graduating (?:students?|in 20\\d\\d)");
    private static final Pattern BATCH = p("\\b(20[2-3]\\d)\\s*(?:/\\s*(20[2-3]\\d)\\s*)?(?:batch|graduat\\w*|grads?"
            + "|pass[- ]?outs?|passing[- ]out|passouts?)\\b|graduating in\\s*(20[2-3]\\d)|class of\\s*(20[2-3]\\d)");

    /** A line that starts a posting's optional wish list ("Preferred Qualifications:", "Nice to have"). */
    private static final Pattern PREFERRED = p("(?:preferred|desired|desirable|nice[- ]to[- ]have|good[- ]to[- ]have"
            + "|bonus(?: points)?|additional (?:preferred )?qualifications|it would be great|it'?s a plus|plus points"
            + "|what would make you stand out|ideally|optional)\\b");
    /** A heading that ends the wish list again ("Minimum qualifications", "Requirements", "About us"). */
    private static final Pattern REQUIRED_HEADING = p("(?:minimum|required|basic|must|mandatory|key|essential)"
            + "(?: \\w+)? (?:qualifications|skills|requirements|experience)|qualifications\\b|requirements\\b"
            + "|eligibility|who you are|what you(?:'ll)? (?:need|bring)|what we(?:'re| are) looking for|about\\b"
            + "|responsibilities|your role|the role|job description");
    /** Section titles written inline, mid-paragraph; they get a line of their own before parsing. */
    private static final Pattern INLINE_HEADING = p("(?<=\\S)\\s+(?=(?:preferred|desired|minimum|required|basic"
            + "|additional) (?:qualifications|skills|requirements|experience)\\s*:)");

    /** Splits a description into clauses: lines, bullets, sentences and " - " lists. */
    private static final Pattern CLAUSE = p("\\n+|\\s*[•·▪●◦]\\s*|;\\s*|(?<=[.!?])\\s+(?=[A-Z0-9])"
            + "|\\s+-\\s+(?=[A-Z0-9])");

    // Title levels, checked in order: the most senior word wins ("Senior Staff Engineer" is staff).
    private static final Object[][] TITLE_LEVELS = {
            {p("\\b(intern|internship|trainee|apprentice\\w*|co-?op)\\b"), 0, "an internship / trainee title"},
            {p("\\b(distinguished|fellow)\\b"), 15, "\"Distinguished\" in the title"},
            {p("\\bprincipal\\b"), 10, "\"Principal\" in the title"},
            {p("\\bstaff\\b|\\bmts\\s*(?:4|iv)\\b"), 8, "\"Staff\" in the title"},
            {p("\\barchitect\\b"), 7, "\"Architect\" in the title"},
            {p("\\b(lead|tech lead)\\b"), 6, "\"Lead\" in the title"},
            {p("\\b(senior|sr\\.?|snr)\\b|\\bsmts\\b"), 4, "\"Senior\" in the title"},
            {p("\\b(?:sde|swe|engineer|developer|analyst|scientist|consultant|mts)[\\s-]*(?:iv|4)\\b"), 6, "level IV in the title"},
            {p("\\b(?:sde|swe|engineer|developer|analyst|scientist|consultant|mts)[\\s-]*(?:iii|3)\\b"), 4, "level III in the title"},
            {p("\\b(?:sde|swe|engineer|developer|analyst|scientist|consultant|mts)[\\s-]*(?:ii|2)\\b"), 2, "level II in the title"},
            {p("\\bmid[- ]?(?:level|senior)?\\b|\\bintermediate\\b"), 2, "a mid-level title"},
            {p("\\b(new grad\\w*|graduate|fresher|entry[- ]level|early career|university|campus|junior|jr\\.?)\\b"),
                    0, "an entry-level title"},
            {p("\\bassociate\\b(?!\\s+(?:director|manager|principal|vice|partner))"), 0, "\"Associate\" in the title"},
            {p("\\b(?:sde|swe|engineer|developer|analyst|scientist|mts)[\\s-]*(?:i|1)\\b"), 0, "level I in the title"},
    };

    // A careers site's own experience-level field (SmartRecruiters, IBM).
    private static final Object[][] LEVEL_HINTS = {
            {p("internship|\\bintern\\b"), 0},
            {p("entry[- ]level|graduate|student"), 0},
            {p("\\bassociate\\b"), 1},
            {p("mid[- ]senior|mid[- ]level"), 3},
            {p("director|executive"), 10},
    };

    /**
     * @param title      the job title
     * @param details    description or qualifications text (HTML is fine); empty when the listing has none
     * @param levelHint  the site's experience-level field, if any ("Entry Level", "Mid-Senior Level")
     * @param detailsRead true when {@code details} is the posting's full description
     */
    public static Requirements extract(String title, String details, String levelHint, boolean detailsRead) {
        String text = plainText(details);
        String required = requiredPart(text);

        int[] years = statedYears(required);
        Basis basis = Basis.STATED;
        String evidence = years == null ? "" : clip(required.substring(years[2], years[3]));
        if (years == null) {
            for (int[] span : clauses(required)) {
                String clause = required.substring(span[0], span[1]);
                Matcher fresher = FRESHER.matcher(clause);
                if (fresher.find() && !(ADVANCED_DEGREE.matcher(clause).find() && !BACHELOR.matcher(clause).find())) {
                    years = new int[]{0, -1};
                    evidence = clip(clause);
                    break;
                }
            }
        }
        if (years == null) {
            basis = Basis.ESTIMATED;
            Object[] level = titleLevel(title);
            if (level != null) {
                years = new int[]{(int) level[1], -1};
                evidence = "Estimated from " + level[2];
            } else {
                Integer hinted = levelHint(levelHint);
                if (hinted != null) {
                    years = new int[]{hinted, -1};
                    evidence = "Estimated from the site's experience level \"" + levelHint.trim() + "\"";
                }
            }
        }
        List<String> skills = SkillCatalog.find((title == null ? "" : title) + "\n" + text);
        if (skills.size() > 12) {
            skills = skills.subList(0, 12);
        }
        return new Requirements(
                years == null ? -1 : years[0],
                years == null ? -1 : years[1],
                basis,
                evidence,
                degree(required.isEmpty() ? text : required),
                batch((title == null ? "" : title) + "\n" + text),
                skills,
                detailsRead);
    }

    /** Title-only estimate, for listings that carry no description. */
    public static Requirements fromTitle(String title, String levelHint) {
        return extract(title, "", levelHint, false);
    }

    // ---------------------------------------------------------------- years

    /**
     * {min, max, clauseStart, clauseEnd} for the clause demanding the most experience, or null.
     * Across required clauses the largest minimum wins: "5+ years of software development; 2+ years
     * with AWS" needs 5.
     */
    static int[] statedYears(String required) {
        int[] best = null;
        for (int[] span : clauses(required)) {
            String clause = required.substring(span[0], span[1]);
            if (clause.isBlank() || BOILERPLATE.matcher(clause).find() || NOT_EXPERIENCE.matcher(clause).find()
                    && !clause.toLowerCase(Locale.ROOT).contains("experience")) {
                continue;
            }
            int[] y = bachelorRouteYears(clause);
            if (y == null) {
                continue;
            }
            if (best == null || y[0] > best[0]) {
                best = new int[]{y[0], y[1], span[0], span[1]};
            }
        }
        return best;
    }

    /** {start, end} of each clause: lines, bullets, sentences and " - " list items. */
    private static List<int[]> clauses(String text) {
        List<int[]> spans = new ArrayList<>();
        Matcher m = CLAUSE.matcher(text);
        int start = 0;
        while (m.find()) {
            spans.add(new int[]{start, m.start()});
            start = m.end();
        }
        spans.add(new int[]{start, text.length()});
        return spans;
    }

    /**
     * Years for the usual route into the job. Many postings offer a shorter route with an advanced
     * degree in the same sentence ("2 years of experience, or 1 year with an advanced degree";
     * "Bachelor's AND 2+ years … OR Master's AND 1+ year"): the part before that alternative counts.
     * A clause about the advanced-degree route alone is skipped.
     */
    private static int[] bachelorRouteYears(String clause) {
        Matcher advanced = ADVANCED_DEGREE.matcher(clause);
        if (!advanced.find()) {
            return yearsIn(clause);
        }
        int lastOr = -1;
        Matcher or = OR.matcher(clause);
        while (or.find() && or.start() < advanced.start()) {
            if (advanced.start() - or.start() <= 90) {
                lastOr = or.start();
            }
        }
        if (lastOr > 0) {
            int[] before = yearsIn(clause.substring(0, lastOr));
            if (before != null) {
                return before;
            }
        }
        return BACHELOR.matcher(clause).find() ? yearsIn(clause) : null;
    }

    /** {min, max} for one clause, or null when it states no experience in years. */
    static int[] yearsIn(String clause) {
        boolean context = EXPERIENCE_CONTEXT.matcher(clause).find();
        String noSchooling = clause.replaceAll("(?i)\\d{1,2}\\s*" + YEARS + "\\s*(?:of\\s*)?(?:full[- ]time\\s*)?"
                + "(?:education|schooling|degree)", " ");
        Matcher m = RANGE.matcher(noSchooling);
        if (m.find() && context) {
            int min = whole(m.group(1));
            int max = whole(m.group(2));
            if (valid(min) && valid(max) && max >= min) {
                return new int[]{min, max};
            }
        }
        for (Pattern pattern : new Pattern[]{PLUS, AT_LEAST}) {
            m = pattern.matcher(noSchooling);
            if (m.find() && context) {
                int min = whole(firstGroup(m));
                if (valid(min)) {
                    return new int[]{min, -1};
                }
            }
        }
        m = WORDS.matcher(noSchooling);
        if (m.find() && context) {
            return new int[]{WORD_VALUES.get(m.group(1).toLowerCase(Locale.ROOT)), -1};
        }
        m = PLAIN.matcher(noSchooling);
        if (m.find() && context) {
            int min = whole(m.group(1));
            if (valid(min)) {
                return new int[]{min, -1};
            }
        }
        return null;
    }

    private static String firstGroup(Matcher m) {
        for (int g = 1; g <= m.groupCount(); g++) {
            if (m.group(g) != null) {
                return m.group(g);
            }
        }
        return "-1";
    }

    private static int whole(String number) {
        return (int) Math.floor(Double.parseDouble(number));
    }

    private static boolean valid(int years) {
        return years >= 0 && years <= 25;
    }

    private static Object[] titleLevel(String title) {
        if (title == null) {
            return null;
        }
        // "IN_Senior Associate_…": underscores join words, which would hide them from \b.
        String words = title.replace('_', ' ');
        for (Object[] level : TITLE_LEVELS) {
            if (((Pattern) level[0]).matcher(words).find()) {
                return level;
            }
        }
        return null;
    }

    private static Integer levelHint(String hint) {
        if (hint == null || hint.isBlank()) {
            return null;
        }
        for (Object[] level : LEVEL_HINTS) {
            if (((Pattern) level[0]).matcher(hint).find()) {
                return (Integer) level[1];
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- degree and batch

    /** The lowest degree that qualifies, with the field when one is named. */
    static String degree(String text) {
        if (text.isBlank()) {
            return "";
        }
        String level;
        Matcher m;
        if ((m = BACHELOR.matcher(text)).find()) {
            level = "Bachelor's";
        } else if ((m = MASTER.matcher(text)).find()) {
            level = "Master's";
        } else if ((m = PHD.matcher(text)).find()) {
            level = "PhD";
        } else {
            return "";
        }
        String around = sentenceAround(text, m.start(), m.end());
        StringBuilder out = new StringBuilder(level);
        Matcher field = DEGREE_FIELD.matcher(around);
        if (field.find()) {
            out.append(" in ").append(fieldName(field.group()))
                    .append(around.toLowerCase(Locale.ROOT).contains("related") && !field.group().toLowerCase(Locale.ROOT)
                            .startsWith("related") ? " or a related field" : "");
        }
        if (EQUIVALENT.matcher(around).find()) {
            out.append(" (or equivalent experience)");
        }
        return out.toString();
    }

    private static String fieldName(String raw) {
        String f = raw.toLowerCase(Locale.ROOT);
        if (f.equals("cs") || f.equals("cse") || f.contains("computer science")) {
            return "Computer Science";
        }
        if (f.contains("computer engineering")) {
            return "Computer Engineering";
        }
        if (f.equals("it") || f.contains("information technology")) {
            return "IT";
        }
        if (f.startsWith("related")) {
            return "a related field";
        }
        if (f.equals("ece") || f.equals("eee") || f.contains("electronics")) {
            return "Electronics";
        }
        if (f.startsWith("math")) {
            return "Mathematics";
        }
        return Character.toUpperCase(f.charAt(0)) + f.substring(1);
    }

    /** "2025, 2026" from "2025/2026 batch", "Class of 2026", "graduating in 2025". */
    static String batch(String text) {
        TreeSet<String> years = new TreeSet<>();
        Matcher m = BATCH.matcher(text);
        while (m.find()) {
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null) {
                    years.add(m.group(g));
                }
            }
        }
        return String.join(", ", years);
    }

    // ---------------------------------------------------------------- text

    /** Tag-free text with one line per paragraph or list item, entities decoded. */
    public static String plainText(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        String s = html
                .replaceAll("(?i)<\\s*(?:br|/p|/li|/h[1-6]|/div|/tr|/ul|/ol)\\s*/?>", "\n")
                .replaceAll("(?i)<\\s*li[^>]*>", "\n• ")
                .replaceAll("<[^>]+>", " ")
                .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&rsquo;", "'").replace("&ndash;", "–")
                .replace("&mdash;", "—").replace("&bull;", "•").replace("\\n", "\n").replace(' ', ' ');
        Matcher num = Pattern.compile("&#(x?)([0-9A-Fa-f]+);").matcher(s);
        StringBuilder sb = new StringBuilder();
        while (num.find()) {
            int code = Integer.parseInt(num.group(2), num.group(1).isEmpty() ? 10 : 16);
            num.appendReplacement(sb, Matcher.quoteReplacement(new String(Character.toChars(code))));
        }
        num.appendTail(sb);
        return sb.toString().replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" ?\\n[ \\n]*", "\n").trim();
    }

    /**
     * The description without its wish list. A heading line such as "Preferred Qualifications:"
     * drops everything under it until the next heading; a single line that starts with a wish-list
     * word ("Good to have skills : Kafka", "Preferred: 3+ years with Go") drops only that line.
     */
    static String requiredPart(String text) {
        StringBuilder out = new StringBuilder();
        boolean inWishList = false;
        for (String line : INLINE_HEADING.matcher(text).replaceAll("\n").split("\n")) {
            String t = line.replaceFirst("^[\\s•\\-–*:]+", "").trim();
            if (PREFERRED.matcher(t).lookingAt()) {
                // "Preferred Qualifications:" alone on its line opens a section; text after it doesn't.
                String rest = t.replaceFirst("(?i)^[^:]{0,60}:", "").trim();
                boolean heading = t.length() <= 60 && (rest.isEmpty() || !t.contains(":")) && t.split("\\s+").length <= 6;
                if (heading) {
                    inWishList = true;
                }
                continue;
            }
            if (inWishList && REQUIRED_HEADING.matcher(t).lookingAt()) {
                inWishList = false;
            }
            if (!inWishList) {
                out.append(line).append('\n');
            }
        }
        return out.toString().trim();
    }

    /** The sentence or list item around a match. A dot inside a word ("B.Tech") does not end it. */
    private static String sentenceAround(String text, int start, int end) {
        int from = start;
        while (from > 0 && !boundary(text, from - 1)) {
            from--;
        }
        int to = end;
        while (to < text.length() && !boundary(text, to)) {
            to++;
        }
        return text.substring(from, to);
    }

    private static boolean boundary(String text, int i) {
        char c = text.charAt(i);
        if ("\n•;".indexOf(c) >= 0) {
            return true;
        }
        return ".!?".indexOf(c) >= 0 && (i + 1 >= text.length() || Character.isWhitespace(text.charAt(i + 1)));
    }

    /** At most 170 characters, cut at a word boundary. */
    static String clip(String s) {
        String t = s.replaceAll("\\s+", " ").replaceAll("^[\\s•\\-–:]+", "").trim();
        if (t.length() <= 170) {
            return t;
        }
        int cut = t.lastIndexOf(' ', 167);
        return t.substring(0, cut > 100 ? cut : 167) + "…";
    }
}
