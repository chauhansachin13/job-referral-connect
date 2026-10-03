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
 * <p>Only what the posting says counts. Years come from its required qualifications ("Minimum 3
 * year(s) of experience is required", "5-7 years of experience", "Years of experience required:
 * 4 – 7 Years", "2+ yrs"); when several are given, the overall one wins ("8+ years in software,
 * 3+ with Kafka" needs 8). Preferred and nice-to-have lines are kept apart and used only when the
 * posting states no minimum, labelled as preferred. For postings that offer routes by degree
 * ("Bachelor's and 5 years, or Master's and 3 years"), the Bachelor's route is the one shown.
 * Nothing is ever guessed — not from job levels like "Senior" or "Associate" (which mean different
 * things at different companies) and not from "Intern": a posting that states no years is shown
 * as not stated.
 */
public final class RequirementsExtractor {

    private RequirementsExtractor() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    private static final String YEARS = "(?:years?|yrs?|year\\(s\\)|yr\\(s\\))";
    private static final String NUM = "(\\d{1,2}(?:\\.\\d)?)";
    private static final String WORD = "(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|thirteen"
            + "|fourteen|fifteen)";
    private static final String DASH = "(?:[-–—‑‒−~]|to)";

    // Strong forms: a range, a "+", or "minimum / at least". In a job posting these are about
    // experience unless the sentence says otherwise (company history, age, schooling).
    /** "3-5 years", "3 to 5 yrs", "3+ - 5 years", "5–7.5 years", "4 ~10 years", "10Yrs to 13Yrs". */
    private static final Pattern RANGE = p(NUM + "\\s*\\+?\\s*(?:" + YEARS + "\\s*)?" + DASH + "\\s*" + NUM + "\\s*\\+?\\s*"
            + YEARS);
    /** "five to eight years", "two (2) to four (4) years". */
    private static final Pattern WORD_RANGE = p("\\b" + WORD + "\\s*(?:\\(\\d{1,2}\\)\\s*)?" + DASH + "\\s*" + WORD
            + "\\s*(?:\\(\\d{1,2}\\)\\s*)?" + YEARS);
    /** "5+ years", "5 years+", "5 or more years", "5 years or more / and above". */
    private static final Pattern PLUS = p(NUM + "\\s*(?:\\+|plus)\\s*" + YEARS + "|" + NUM + "\\s*" + YEARS
            + "\\s*(?:\\+|plus\\b|or more\\b|and above\\b|or above\\b|or longer\\b)|" + NUM + "\\s*or more\\s*" + YEARS);
    /** "minimum 3 years", "at least 2 yrs", "over 4 years", "more than 3 years". */
    private static final Pattern AT_LEAST = p("(?:minimum(?: of)?|min\\.?|at\\s*least|atleast|more than|over|upwards"
            + " of|no less than|not less than)\\s*(?:a\\s*)?" + NUM + "\\s*\\+?\\s*" + YEARS);
    /** "a minimum of 4 years and maximum of 7 years". */
    private static final Pattern MIN_MAX = p("(?:minimum|min\\.?)(?: of)?\\s*" + NUM + "\\s*" + YEARS
            + "\\s*(?:and|to|&|-)\\s*(?:a\\s*)?(?:maximum|max\\.?)(?: of)?\\s*" + NUM + "\\s*" + YEARS);
    // Weak forms: a bare "3 years" / "five years" counts only when the sentence is about experience
    // or the line is the value of an experience label.
    private static final Pattern PLAIN = p("(?<![\\d.])" + NUM + "\\s*" + YEARS + "(?![a-z])");
    private static final Pattern WORDS = p("\\b" + WORD + "\\s*(?:\\(\\d{1,2}\\)\\s*)?\\+?\\s*" + YEARS
            + "(?![a-z])");

    private static final Map<String, Integer> WORD_VALUES = Map.ofEntries(
            Map.entry("one", 1), Map.entry("two", 2), Map.entry("three", 3), Map.entry("four", 4),
            Map.entry("five", 5), Map.entry("six", 6), Map.entry("seven", 7), Map.entry("eight", 8),
            Map.entry("nine", 9), Map.entry("ten", 10), Map.entry("eleven", 11), Map.entry("twelve", 12),
            Map.entry("thirteen", 13), Map.entry("fourteen", 14), Map.entry("fifteen", 15));

    /** Words that make a bare number of years about the candidate's experience. */
    private static final Pattern EXPERIENCE_CONTEXT = p("experience|\\bexp\\b|expertise|industry|professional"
            + "|\\bwork|hands[- ]on|track record|background|develop|programming|coding|engineering|building"
            + "|designing|relevant|proven|\\brole|\\bin (?:the )?(?:field|domain|it)\\b|career|tenure in|\\brelated\\b"
            // "Bachelors + 5 years", "Master's degree and 3 years": years attached to a degree route.
            + "|(?:bachelor|master|ph\\.?d|degree|b\\.?\\s?tech|b\\.?e)\\S*\\s*(?:\\+|and|with|plus)\\s*(?:a\\s*)?"
            + "(?:minimum\\s*(?:of\\s*)?)?\\d");
    /** Company history and the like: never about the candidate. */
    private static final Pattern BOILERPLATE = p("founded|anniversary|we have been|we've been|we’ve been|our (?:company"
            // "50 years in business" is history; "7+ years in Business Analysis" is a requirement.
            + "|firm|history|legacy|clients)|trusted by|years? (?:of|in) business(?![ \\t]+\\p{L})|years? old"
            + "|\\bage\\b|of age\\b"
            + "|since \\d{4}|over the (?:next|past|last)|for (?:more than|over) \\d+ years|has been \\w+ing"
            + "|have been \\w+ing|health screening|background check|every (?:two|three|\\d) years"
            + "|length of service|average tenure");
    /** How long a career break lasted ("a career break (1–5 years)", "a break of 1 to 5 years") is not experience. */
    private static final Pattern CAREER_BREAK = p("(?:career )?break\\s*(?:\\([^)]*\\)|(?:of|lasting|between|for)\\b[^.;,]*)");
    /** Schooling, contracts and benefits; skipped unless the sentence also talks about experience. */
    private static final Pattern NOT_EXPERIENCE = p("education|schooling|warranty|contract (?:of|for)|tenure of"
            + "|\\bbond\\b|parental|\\bleave\\b|insurance|retire|vesting|equity|clients|customers|serving"
            + "|\\bterm\\b|duration");

    private static final Pattern BACHELOR = p("bachelor|\\bb\\.?\\s?tech\\b|\\bb\\.\\s?e\\.?(?=\\W)|\\bbe\\s*/\\s*b\\.?\\s?tech"
            + "|\\bbs\\b|\\bb\\.\\s?s\\.?\\b|\\bbsc\\b|\\bb\\.sc\\b|\\bbca\\b|undergraduate|\\bbachelors\\b"
            + "|graduation in|any graduate|graduate in|\\bbe\\b(?=\\s*(?:/|,|in\\b|or\\b))");
    private static final Pattern MASTER = p("master'?s|master’s|\\bm\\.?\\s?tech\\b|\\bm\\.\\s?s\\.?\\b"
            + "|\\bms (?:degree|in)\\b|\\bmca\\b|\\bm\\.\\s?e\\.?\\b|\\bmsc\\b|\\bm\\.sc\\b|\\bmba\\b"
            + "|advanced degree|graduate degree");
    private static final Pattern PHD = p("\\bph\\.?\\s?d\\b|doctorate|doctoral");
    private static final Pattern DIPLOMA = p("\\bdiploma\\b|associate'?s degree|high school|in lieu of (?:a |the )?"
            + "(?:bachelor\\S*\\s*)?degree|without (?:a |the )?degree|no degree");
    private static final Pattern DEGREE_FIELD = p("computer science|computer engineering|\\bcse\\b|\\bcs\\b"
            + "|information technology|(?-i:\\bIT\\b)|electronics|\\bece\\b|\\beee\\b|mathematics|\\bmaths?\\b"
            + "|statistics|data science|engineering|related (?:technical |quantitative )?(?:field|discipline|area)"
            + "|stem|quantitative");
    private static final Pattern EQUIVALENT = p("or equivalent (?:practical |work |industry )?experience");
    private static final Pattern OR = p("\\bor\\b");

    /**
     * Wording that opens a role to people with no experience. "Entry level" counts only when it
     * describes the role — not NetApp's "Entry Level Careers Program" paragraph on every posting.
     */
    private static final Pattern FRESHER = p("\\bfreshers?\\b|fresh graduates?|\\bnew grad(?:uate)?s?\\b"
            + "|recent (?:college )?graduates?|no (?:prior )?(?:work )?experience (?:is )?required"
            + "|entry[- ]level (?:role|position|job|opening|candidates?|hires?|talent|professionals?|engineers?"
            + "|developers?|analysts?|software engineers?)\\b|\\b(?:is|an?|this) entry[- ]level\\b"
            + "|\\b0\\s*" + YEARS + "(?![a-z])|final[- ]year students?|graduating (?:students?|in 20\\d\\d)");
    /** "Mentor entry-level engineers" is about the people the hire will train, not the hire. */
    private static final Pattern MENTORING = p("\\b(?:mentor|train|coach|guide|lead|manag|supervis)\\w*\\b[^.;]{0,40}"
            + "entry[- ]level");
    private static final Pattern BATCH = p("\\b(20[2-3]\\d)\\s*(?:/\\s*(20[2-3]\\d)\\s*)?(?:batch|graduat\\w*|grads?"
            + "|pass[- ]?outs?|passing[- ]out|passouts?)\\b|graduating in\\s*(20[2-3]\\d)|class of\\s*(20[2-3]\\d)");

    /** A line or heading that starts a wish list ("Preferred Qualifications:", "Nice to have"). */
    private static final Pattern PREFERRED_START = p("(?:preferred|desired|desirable|nice[- ]to[- ]have"
            + "|good[- ]to[- ]have|bonus(?: points)?|additional (?:preferred )?qualifications|it would be great"
            + "|it'?s a plus|plus points|what would make you stand out|ideally|optional|added advantage"
            + "|great to have|preferable)\\b");
    /** Wording inside a line that makes that line a wish ("… is a plus", "5+ years preferred"). */
    private static final Pattern PREFERRED_INLINE = p("(?:is|are|would be|will be|considered)\\s+(?:an?\\s+)?"
            + "(?:strong |big |huge |added |great |definite )?(?:plus|advantage|bonus)\\b|nice[- ]to[- ]have"
            + "|good[- ]to[- ]have|\\bdesirable\\b|" + YEARS + "\\s*(?:\\(\\s*)?preferred|is preferred|are preferred"
            + "|\\(preferred\\)|preferred but not|\\bnot (?:required|mandatory)\\b");
    /** A sentence that ends by calling itself preferred: "… within a financial institution or similar preferred." */
    private static final Pattern PREFERRED_TAIL = p("\\b(?:or similar|or equivalent|or related|strongly|highly)\\s+"
            + "preferred\\s*[.!]?\\s*$");
    /** Walmart-style alternatives: "Option 1: Bachelor's … and 2 years", "Option 2: 4 years …". */
    private static final Pattern OPTION = p("\\s*option\\s*\\d+\\s*[:.\\-–)]");
    /**
     * A field line giving the years ("Years of experience required 4-7 yrs", "Experience: 5–8 years",
     * "EXP Level - 10Yrs to 13Yrs"): a requirement even when it follows a wish list.
     */
    private static final Pattern YEARS_FIELD = p("\\*?\\s*(?:(?:minimum|min\\.?|total|overall|relevant|required|work)"
            + "\\s+)?(?:years?\\s+of\\s+)?(?:(?:work|relevant|professional|total|overall)\\s+)?(?:experience|exp\\.?)"
            + "(?:\\s+(?:required|needed|level|range))?\\s*[:\\-–]?\\s*\\(?\\d");
    /** "Ideally 12+ years …, minimum 7 years of …": the part after the comma is a hard requirement. */
    private static final Pattern HARD_MINIMUM = p("[,;]\\s*(?:with\\s+)?(?:a\\s+)?(?:minimum|min\\.?|at least)\\b");
    /** Section titles written inline, mid-paragraph; they get a line of their own before parsing. */
    private static final Pattern INLINE_HEADING = p("(?<=\\S)\\s+(?=(?:preferred|desired|minimum|required|basic"
            + "|additional|key|academic) (?:qualifications|skills|requirements|experience|credentials)\\s*:)");

    /** Splits a line into clauses: sentences, semicolons and " - " lists (but not "4 - 8 years"). */
    private static final Pattern CLAUSE = p("\\s*[•·▪●◦]\\s*|;\\s*"
            // A sentence end, but not an abbreviation: "5+ years of query languages (e.g. SQL)" is one clause.
            + "|(?<=[.!?])(?<!\\b(?:e\\.g|i\\.e|etc|vs|approx|incl|viz|cf)\\.)\\s+(?=[A-Z0-9])"
            + "|(?<![\\d+])\\s+-\\s+(?=[A-Z0-9])|\\s+\\|\\s+"
            // Sentences glued without a space, as some sites send them: "an added advantage.8+ years".
            + "|(?-i:(?<=[a-z)][.!?])(?=[A-Z0-9]))");
    /**
     * A list marker; a number counts only when followed by a space ("1. Java", not "7.5+ years"),
     * and so does "*" ("*Years of experience required" is a heading).
     */
    private static final Pattern BULLET = p("^\\s*(?:[•·▪●◦\\-–]|\\*(?=\\s)|\\d{1,2}[.)](?=\\s))\\s*");
    /**
     * Field labels glued onto the text before them when a site drops its line breaks ("JIRAGood to
     * Have skills:Agile MethodologiesYears of Experience:6 to 9 years"); each gets a line of its own.
     */
    private static final Pattern GLUED_LABEL = Pattern.compile("(?<=[A-Za-z0-9).:])(?=(?:Good to [Hh]ave|Nice to "
            + "[Hh]ave|Must [Hh]ave|Years of [Ee]xperience|Total [Ee]xperience|(?:Preferred |Required |Minimum |Basic )?"
            + "Qualifications|Key [Rr]esponsibilities|Roles? (?:&|and) [Rr]esponsibilities)\\b)");
    /** A heading about experience or background ("Experience & Background") ends a wish list. */
    private static final Pattern EXPERIENCE_HEADING = p("\\b(?:experience|background)\\b");
    /** A requirement word anywhere in a heading ("Years of experience required:", "Required Experience and Skills"). */
    private static final Pattern REQUIRED_WORD = p("\\b(?:required|requirements?|minimum|mandatory|must|eligib\\w*)\\b");
    /** A wish-list word anywhere in a heading ("Additional Responsibilities & Preferred Qualifications"). */
    private static final Pattern PREFERRED_WORD = p("\\b(?:preferred|desired|desirable|nice[- ]to[- ]have"
            + "|good[- ]to[- ]have|bonus|plus points|great to have|added advantage|optional)\\b");
    /**
     * Headings that end a wish list: the posting's requirement sections and its top-level sections.
     * Other headings inside a wish list ("Technical Skills", "Soft Skills") are part of it.
     */
    private static final Pattern SECTION_RESET = p("(?:(?:minimum|required|basic|must[- ]have|mandatory|key|essential"
            + "|core)\\b.*|(?:job )?qualifications?|(?:job )?requirements?|eligibility.*|experience(?:\\s*(?:&|and)\\s*"
            + "(?:education|qualifications?|skills))?|education.*|(?:skills|experience) (?:&|and) (?:experience"
            + "|qualifications?|education)|what you(?:'ll| will)? need.*|who you are|what we(?:'re| are) looking for.*"
            + "|about (?:the role|the job|you|us|the team|.*company)|benefits.*|our benefits|who we are|job description"
            + "|(?:key |your |main )?responsibilities|the role|your role|what you(?:'ll| will) do.*|why join.*"
            + "|academic credentials|the person|candidate profile|your profile|your background|required skills"
            + "|skills required|must haves?|the opportunity|(?:role|position|job) (?:overview|summary)|overview|summary"
            + "|about the (?:role|position|opportunity)|what will you do.*|(?:secondary language\\(s\\) )?job description"
            + "|additional (?:information|info|details|job description)|other information)"
            + "\\s*:?");
    /** An experience label whose value may follow on the next line or after a dash. */
    private static final Pattern EXPERIENCE_LABEL = p("experience|\\bexp\\b|\\byears\\b|\\byrs\\b");

    private static final Pattern EXP_WORD = p("\\bexp\\b|\\bexp\\.|experience");
    private static final Pattern DURATION_AFTER = p("\\)?\\s*(?:program|programme|apprentice|contract|course|fixed"
            + "|term|duration|internship|graduate)");
    /** Years in a job title: "(5-9 Years)", "| 4-8 Years", "(4+ yrs in React…)". */
    private static final Pattern TITLE_YEARS = p("(?<![\\d.])" + NUM + "\\s*(?:\\+\\s*)?(?:" + DASH + "\\s*" + NUM
            + "\\s*\\+?\\s*)?" + YEARS + "(?![a-z])");
    /**
     * @param title       the job title (years written in it, e.g. "(5-9 Years)", count as stated)
     * @param details     description or qualifications text (HTML is fine); empty when the listing has none
     * @param detailsRead true when {@code details} is the posting's full description
     */
    public static Requirements extract(String title, String details, boolean detailsRead) {
        String safeTitle = title == null ? "" : title;
        String text = plainText(details);
        Sections sections = sections(text);

        Basis basis = Basis.STATED;
        List<Statement> wishes = new ArrayList<>();
        List<Statement> found = statements(sections.required(), wishes);
        // Years in the title ("… | 4+ Years") are stated too; when the title and text disagree the larger counts.
        Statement inTitle = titleYears(safeTitle);
        if (inTitle != null) {
            found.add(inTitle);
        }
        Statement chosen = choose(found);
        String evidence = chosen == null ? "" : chosen == inTitle ? "Stated in the job title: " + safeTitle.trim()
                : clip(chosen.clause());
        if (chosen == null) {
            for (String clause : clauses(sections.required())) {
                if (FRESHER.matcher(clause).find() && !advancedOnly(clause) && !MENTORING.matcher(clause).find()) {
                    chosen = new Statement(0, -1, clause, Degree.NONE);
                    evidence = clip(clause);
                    break;
                }
            }
        }
        if (chosen == null) {
            List<Statement> preferred = new ArrayList<>(wishes);
            // Inside a wish list, lines that also say "is a plus" are wishes as well: keep both kinds.
            preferred.addAll(statements(sections.preferred(), preferred));
            chosen = choose(preferred);
            if (chosen != null) {
                basis = Basis.PREFERRED;
                evidence = clip(chosen.clause());
            }
        }
        // Nothing else: no guessing from levels such as "Senior" or "Associate". Not stated is not stated.

        List<String> skills = SkillCatalog.find(safeTitle + "\n" + text);
        if (skills.size() > 12) {
            skills = skills.subList(0, 12);
        }
        String required = String.join("\n", sections.required());
        return new Requirements(
                chosen == null ? -1 : chosen.min(),
                chosen == null ? -1 : chosen.max(),
                chosen == null ? Basis.UNKNOWN : basis,
                evidence,
                degree(required.isBlank() ? text : required),
                batch(safeTitle + "\n" + text),
                skills,
                detailsRead);
    }

    /** What the title alone states, for listings whose description has not been read yet. */
    public static Requirements fromTitle(String title) {
        return extract(title, "", false);
    }

    // ---------------------------------------------------------------- sections

    /** The posting's lines, split into required and preferred (wish-list) ones. */
    record Sections(List<String> required, List<String> preferred) {
    }

    /**
     * A heading with a wish-list word ("Preferred Qualifications", "Nice to Have", "Additional
     * Responsibilities & Preferred Qualifications") starts a wish list, which lasts — through any
     * sub-headings such as "Technical Skills" — until a requirement or top-level heading ("Minimum
     * Qualifications", "Experience & Education", "Requirements", "About us", "Benefits"). A single
     * line that starts as a wish ("Nice to have: Go") is a wish too. Lines that only hold a value
     * ("4 – 7 Years") stay with the label above them.
     */
    static Sections sections(String text) {
        List<String> required = new ArrayList<>();
        List<String> preferred = new ArrayList<>();
        boolean inWishList = false;
        String label = null;
        String lined = GLUED_LABEL.matcher(INLINE_HEADING.matcher(text).replaceAll("\n")).replaceAll("\n");
        for (String raw : lined.split("\n")) {
            String t = BULLET.matcher(raw).replaceFirst("").trim();
            if (t.isEmpty()) {
                continue;
            }
            boolean bullet = BULLET.matcher(raw).lookingAt();
            boolean hasYears = mentionsYears(t);
            boolean heading = !bullet && !hasYears && isHeading(t);
            if (heading) {
                if (PREFERRED_WORD.matcher(t).find()) {
                    inWishList = true;
                } else if (SECTION_RESET.matcher(t).matches() || REQUIRED_WORD.matcher(t).find()
                        || EXPERIENCE_HEADING.matcher(t).find()) {
                    inWishList = false;
                }
                label = EXPERIENCE_LABEL.matcher(t).find() ? t : null;
                continue;
            }
            // "Years of experience required:" ⏎ "4 – 7 Years": the value inherits the label.
            String line = label != null && hasYears && t.split("\\s+").length <= 8 ? label + " " + t : t;
            label = null;
            // A years field ("Years of experience required 4-7 yrs") is a requirement wherever it sits.
            if (YEARS_FIELD.matcher(t).lookingAt()) {
                required.add(line);
                continue;
            }
            if (!inWishList && PREFERRED_START.matcher(t).lookingAt()) {
                // "Ideally 12+ years …, minimum 7 years of …": the hard minimum after the comma is required.
                Matcher hard = HARD_MINIMUM.matcher(line);
                if (hard.find()) {
                    preferred.add(line.substring(0, hard.start()));
                    required.add(line.substring(hard.start() + 1).trim());
                } else {
                    preferred.add(line);
                }
                continue;
            }
            (inWishList ? preferred : required).add(line);
        }
        return new Sections(required, preferred);
    }

    /**
     * A section title: a short line that is not a list item, with nothing after a colon. "Good to
     * have skills : NA" or "Experience: 5 years" are fields, not titles.
     */
    private static boolean isHeading(String t) {
        String s = t.trim();
        if (s.length() > 70 || s.split("\\s+").length > 9) {
            return false;
        }
        int colon = s.indexOf(':');
        if (colon >= 0 && colon < s.length() - 1) {
            return false;
        }
        return s.endsWith(":") || !s.matches(".*[.!?,;]$");
    }

    /** The clause without anything in brackets: "(Masters or PhD is a plus)" says nothing about the years. */
    private static String withoutBrackets(String clause) {
        return clause.replaceAll("\\([^)]*\\)", " ");
    }

    private static boolean mentionsYears(String s) {
        return RANGE.matcher(s).find() || WORD_RANGE.matcher(s).find() || PLUS.matcher(s).find()
                || AT_LEAST.matcher(s).find() || PLAIN.matcher(s).find() || WORDS.matcher(s).find();
    }

    // ---------------------------------------------------------------- statements

    enum Degree { BACHELOR, NONE, DIPLOMA, MASTER, PHD }

    /** One "N years" requirement and the clause it came from. */
    record Statement(double min, double max, String clause, Degree degree) {
    }

    private static List<String> clauses(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            for (String c : CLAUSE.split(line)) {
                if (!c.isBlank()) {
                    out.add(c.trim());
                }
            }
        }
        return out;
    }

    /**
     * Every years-of-experience statement in these lines. Clauses that call themselves a wish
     * ("6-12 years in SSD firmware would be a strong plus") go to {@code wishes} instead.
     */
    static List<Statement> statements(List<String> lines, List<Statement> wishes) {
        List<Statement> out = new ArrayList<>();
        for (String line : lines) {
            String previous = "";
            for (String raw : CLAUSE.split(line)) {
                String clause = raw.trim();
                if (clause.isEmpty()) {
                    continue;
                }
                // "Industry Experience - 2 to 4 years": a short experience label before the value.
                boolean labelled = previous.split("\\s+").length <= 6 && EXPERIENCE_LABEL.matcher(previous).find();
                for (Statement s : statement(clause, labelled)) {
                    // Judged on the statement's own sentence: a run-on line may say "Good to have" far away.
                    boolean wish = PREFERRED_INLINE.matcher(withoutBrackets(s.clause())).find()
                            || PREFERRED_TAIL.matcher(s.clause()).find();
                    (wish ? wishes : out).add(s);
                }
                previous = clause;
            }
        }
        return out;
    }

    /**
     * The years-of-experience statements in one clause: usually one, but a clause offering routes by
     * degree ("Bachelor's and 5 years, or Master's and 3 years, or PhD") gives one per route.
     */
    private static List<Statement> statement(String original, boolean labelled) {
        // Some postings arrive as one long run-on line; judge a years mention by its own surroundings.
        String text = original.length() > 300 ? around(original, firstYears(original)) : original;
        String clause = CAREER_BREAK.matcher(text).replaceAll(" ");
        if (BOILERPLATE.matcher(clause).find()) {
            return List.of();
        }
        if (NOT_EXPERIENCE.matcher(clause).find() && !clause.toLowerCase(Locale.ROOT).contains("experience")) {
            return List.of();
        }
        // Context words anywhere in the clause count for each route ("Masters + 3 years of related experience").
        boolean context = labelled || EXPERIENCE_CONTEXT.matcher(clause).find();
        List<String> routes = degreeRoutes(clause, context);
        if (routes.size() >= 2) {
            boolean bachelorRoute = routes.stream().anyMatch(r -> degreeOf(r) == Degree.BACHELOR);
            List<Statement> out = new ArrayList<>();
            for (String route : routes) {
                double[] y = yearsIn(route, context);
                Degree d = degreeOf(route);
                // Beside a Bachelor's route, a route naming no degree ("OR 8+ years of experience") is the no-degree one.
                out.add(new Statement(y[0], y[1], text, d == Degree.NONE && bachelorRoute ? Degree.DIPLOMA : d));
            }
            return out;
        }
        Statement single = singleRoute(text, clause, labelled);
        return single == null ? List.of() : List.of(single);
    }

    /**
     * The parts of a clause separated by "or" that each state years, when at least one names a
     * degree: "PhD with 3-7 years or Masters with 6-10 years" → two routes. Parts without years
     * ("Computer Science or a related field") join the part after them. Empty unless there are two
     * or more routes.
     */
    private static List<String> degreeRoutes(String clause, boolean context) {
        List<String> parts = new ArrayList<>();
        Matcher or = OR.matcher(clause);
        int from = 0;
        while (or.find()) {
            parts.add(clause.substring(from, or.start()));
            from = or.start();
        }
        parts.add(clause.substring(from));
        List<String> routes = new ArrayList<>();
        StringBuilder pending = new StringBuilder();
        for (String part : parts) {
            pending.append(part);
            if (yearsIn(pending.toString(), context) != null) {
                routes.add(pending.toString());
                pending.setLength(0);
            }
        }
        if (pending.length() > 0 && !routes.isEmpty()) {
            routes.set(routes.size() - 1, routes.get(routes.size() - 1) + pending);
        }
        boolean namesDegree = routes.stream().anyMatch(r -> degreeOf(r) != Degree.NONE);
        return routes.size() >= 2 && namesDegree ? routes : List.of();
    }

    private static Statement singleRoute(String text, String clause, boolean labelled) {
        // A clause offering the shorter route for advanced degrees: keep the part before it.
        String usual = clause;
        Matcher advanced = p("master'?s|master’s|\\bm\\.?\\s?tech\\b|\\bph\\.?\\s?d\\b|doctorate|advanced degree"
                + "|graduate degree|\\bmba\\b|\\bms\\b").matcher(clause);
        if (advanced.find()) {
            int lastOr = -1;
            Matcher or = OR.matcher(clause);
            while (or.find() && or.start() < advanced.start()) {
                if (advanced.start() - or.start() <= 90) {
                    lastOr = or.start();
                }
            }
            if (lastOr > 0 && yearsIn(clause.substring(0, lastOr), labelled) != null) {
                usual = clause.substring(0, lastOr);
            } else {
                // The other order: "3 years with a Master's or 5+ years with a Bachelor's" — keep the Bachelor's route.
                Matcher next = OR.matcher(clause);
                if (next.find(advanced.end()) && next.start() - advanced.end() <= 40) {
                    String before = clause.substring(0, next.start());
                    String after = clause.substring(next.end());
                    if (!BACHELOR.matcher(before).find() && BACHELOR.matcher(after).find()
                            && yearsIn(before, labelled) != null && yearsIn(after, labelled) != null) {
                        usual = after;
                    }
                }
            }
        }
        double[] y = yearsIn(usual, labelled);
        if (y == null) {
            return null;
        }
        Degree degree = degreeOf(usual);
        // Walmart lists "Option 1: Bachelor's … and 2 years" then "Option 2: 4 years …": the degree-less option.
        if (degree == Degree.NONE && OPTION.matcher(clause).lookingAt()) {
            degree = Degree.DIPLOMA;
        }
        return new Statement(y[0], y[1], text, degree);
    }

    /** Where the first years mention starts, or -1. */
    private static int firstYears(String s) {
        int best = -1;
        for (Pattern pattern : new Pattern[]{RANGE, WORD_RANGE, PLUS, AT_LEAST, PLAIN, WORDS}) {
            Matcher m = pattern.matcher(s);
            if (m.find() && (best < 0 || m.start() < best)) {
                best = m.start();
            }
        }
        return best;
    }

    /** About a sentence's worth of text around position {@code at}, cut at word boundaries. */
    private static String around(String s, int at) {
        if (at < 0) {
            return s.substring(0, Math.min(s.length(), 300));
        }
        int from = Math.max(0, at - 100);
        int to = Math.min(s.length(), at + 160);
        while (from > 0 && !Character.isWhitespace(s.charAt(from - 1))) {
            from++;
        }
        while (to < s.length() && !Character.isWhitespace(s.charAt(to))) {
            to++;
        }
        return s.substring(from, to).trim();
    }

    private static Degree degreeOf(String clause) {
        if (BACHELOR.matcher(clause).find()) {
            return Degree.BACHELOR;
        }
        if (DIPLOMA.matcher(clause).find()) {
            return Degree.DIPLOMA;
        }
        if (MASTER.matcher(clause).find()) {
            return Degree.MASTER;
        }
        if (PHD.matcher(clause).find()) {
            return Degree.PHD;
        }
        return Degree.NONE;
    }

    private static boolean advancedOnly(String clause) {
        Degree d = degreeOf(clause);
        return d == Degree.MASTER || d == Degree.PHD;
    }

    /**
     * The requirement that applies to most applicants: the largest minimum among the general and
     * Bachelor's statements, since "8+ years, including 3+ with Kafka" needs 8. The other routes a
     * posting offers — with a Master's or PhD (usually shorter), with a diploma or no degree
     * (usually longer) — count only when the posting gives nothing else. A range beats a bare
     * minimum on a tie.
     */
    static Statement choose(List<Statement> all) {
        List<List<Degree>> tiers = List.of(List.of(Degree.BACHELOR, Degree.NONE), List.of(Degree.DIPLOMA),
                List.of(Degree.MASTER), List.of(Degree.PHD));
        for (List<Degree> tier : tiers) {
            Statement best = null;
            for (Statement s : all) {
                if (!tier.contains(s.degree())) {
                    continue;
                }
                if (best == null || s.min() > best.min() || s.min() == best.min() && s.max() > best.max()) {
                    best = s;
                }
            }
            if (best != null) {
                return best;
            }
        }
        return null;
    }

    /**
     * {min, max} for one clause, or null when it states no experience in years. When a clause has
     * several, the first one is the overall figure ("Minimum 12 years of ServiceNow, including 2+
     * years as an architect" needs 12).
     */
    static double[] yearsIn(String clause, boolean labelled) {
        // "15 years full time education", "a 4 year bachelor's degree", "4-year engineering course" are schooling.
        String noSchooling = clause.replaceAll("(?i)\\d{1,2}\\s*-?\\s*" + YEARS + "\\s*(?:of\\s*)?(?:full[- ]time\\s*)?"
                + "(?:(?:bachelor\\S*|undergraduate|engineering|university|college)\\s*)?"
                + "(?:education|schooling|degree|course|program(?:me)?)", " ");
        // Strong forms, earliest first; on the same number a range beats "minimum 3" or "3+".
        double[] first = null;
        int at = Integer.MAX_VALUE;
        for (Pattern pattern : new Pattern[]{RANGE, WORD_RANGE, MIN_MAX, PLUS, AT_LEAST}) {
            Matcher m = pattern.matcher(noSchooling);
            while (m.find()) {
                double[] y = years(pattern, m);
                if (y != null) {
                    int start = m.start(firstGroupIndex(m));
                    if (start < at) {
                        first = y;
                        at = start;
                    }
                    break;
                }
            }
        }
        // A figure the sentence itself calls overall or total wins over one for a single skill:
        // "minimum 7+ years of SharePoint experience and 8 to 10 years of overall IT experience" needs 8–10.
        double[] overall = overallFigure(noSchooling);
        if (overall != null) {
            return overall;
        }
        if (first != null) {
            return first;
        }
        if (!labelled && !EXPERIENCE_CONTEXT.matcher(noSchooling).find()) {
            return null;
        }
        Matcher m = WORDS.matcher(noSchooling);
        if (m.find()) {
            return new double[]{WORD_VALUES.get(m.group(1).toLowerCase(Locale.ROOT)), -1};
        }
        m = PLAIN.matcher(noSchooling);
        if (m.find()) {
            double min = decimal(m.group(1));
            if (valid(min)) {
                return new double[]{min, -1};
            }
        }
        return null;
    }

    private static final Pattern OVERALL = p("\\b(?:overall|total|in total)\\b");

    /**
     * A strong figure marked as the overall one — "Overall 4 to 6 years", "8 to 10 years of overall
     * IT experience", "8+ years total" — when the clause has more than one figure; else null.
     */
    private static double[] overallFigure(String clause) {
        Matcher word = OVERALL.matcher(clause);
        if (!word.find()) {
            return null;
        }
        double[] chosen = null;
        int figures = 0;
        for (Pattern pattern : new Pattern[]{RANGE, WORD_RANGE, MIN_MAX, PLUS, AT_LEAST}) {
            Matcher m = pattern.matcher(clause);
            while (m.find()) {
                double[] y = years(pattern, m);
                if (y == null) {
                    continue;
                }
                figures++;
                int at = m.start(firstGroupIndex(m));
                String before = clause.substring(Math.max(0, at - 20), at);
                String after = clause.substring(m.end(), Math.min(clause.length(), m.end() + 25));
                boolean marked = OVERALL.matcher(before).find() && !before.matches("(?is).*\\b(?:overall|total)\\b.*\\d.*")
                        || after.matches("(?is)\\s*(?:of\\s+)?(?:\\w+\\s+){0,2}?(?:overall|total)\\b.*")
                        || after.matches("(?is)\\s*(?:of\\s+)?(?:\\w+\\s+)?(?:\\w+\\s+)?experience\\s+(?:overall|in total|total)\\b.*");
                if (marked && chosen == null) {
                    chosen = y;
                }
            }
        }
        return figures >= 2 ? chosen : null;
    }

    /** The years one strong-form match states, or null when the numbers make no sense. */
    private static double[] years(Pattern pattern, Matcher m) {
        if (pattern == RANGE || pattern == WORD_RANGE || pattern == MIN_MAX) {
            boolean words = pattern == WORD_RANGE;
            double min = words ? WORD_VALUES.get(m.group(1).toLowerCase(Locale.ROOT)) : decimal(m.group(1));
            double max = words ? WORD_VALUES.get(m.group(2).toLowerCase(Locale.ROOT)) : decimal(m.group(2));
            return valid(min) && valid(max) && max >= min ? new double[]{min, max} : null;
        }
        double min = decimal(m.group(firstGroupIndex(m)));
        return valid(min) ? new double[]{min, -1} : null;
    }

    private static Statement titleYears(String title) {
        Matcher m = TITLE_YEARS.matcher(title);
        if (!m.find()) {
            return null;
        }
        // Only an experience figure: a range, a "+", the word "exp", or set apart as "(…)", "| …", "- …".
        // "2 Year Apprenticeship" or "(2 years) programme" is a duration.
        String before = title.substring(0, m.start()).stripTrailing();
        String after = title.substring(m.end()).toLowerCase(Locale.ROOT);
        boolean marked = m.group(2) != null || m.group().contains("+") || EXP_WORD.matcher(title).find()
                || before.isEmpty() || "(|-–—,:".indexOf(before.charAt(before.length() - 1)) >= 0;
        if (!marked || DURATION_AFTER.matcher(after).lookingAt()) {
            return null;
        }
        double min = decimal(m.group(1));
        double max = m.group(2) == null ? -1 : decimal(m.group(2));
        return valid(min) && (max < 0 || valid(max) && max >= min) ? new Statement(min, max, title, Degree.NONE) : null;
    }

    private static int firstGroupIndex(Matcher m) {
        for (int g = 1; g <= m.groupCount(); g++) {
            if (m.group(g) != null) {
                return g;
            }
        }
        return 0;
    }

    /** "7.5" stays 7.5: the posting's own figure, not rounded either way. */
    private static double decimal(String number) {
        return Double.parseDouble(number);
    }

    private static boolean valid(double years) {
        return years >= 0 && years <= 25;
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
        if (f.equals("stem")) {
            return "STEM";
        }
        if (f.equals("quantitative")) {
            return "a quantitative field";
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
                .replace("&mdash;", "—").replace("&bull;", "•").replace("\\n", "\n").replace('\u00A0', ' ')
                .replace('\u2009', ' ').replace('\u202F', ' ');
        return Html.decodeNumericEntities(s).replaceAll("[ \\t\\x0B\\f\\r]+", " ").replaceAll(" ?\\n[ \\n]*", "\n").trim();
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
