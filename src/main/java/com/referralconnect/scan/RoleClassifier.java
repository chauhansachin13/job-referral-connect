package com.referralconnect.scan;

import com.referralconnect.model.JobCategory;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides from a job title whether a posting is a computer-science role — software engineering,
 * data science, AI / ML, data engineering, data / BI analysis, or forward-deployed and solutions
 * engineering — and whether it is an internship.
 *
 * <p>The rules favour precision: a title must say what kind of computing work it is. A bare
 * "Engineer II" only counts at software companies, because at banks, chip makers and industrial
 * firms the same title is usually mechanical, electrical, chip or plant engineering.
 *
 * <p>Order matters: "Data Scientist, Machine Learning" is data science, "Software Engineer, Machine
 * Learning" is AI / ML and "Analytics Engineer" is data engineering, because more specific families
 * are checked first.
 */
public final class RoleClassifier {

    private RoleClassifier() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /** Leadership, recruiting and go-to-market titles that only look technical. */
    private static final Pattern EXCLUDE = p("\\b(manager|mgr|director|head of|vp|vice president|recruit\\w*|talent"
            + "|representative"
            + "|account executive|account manager|counsel|attorney|paralegal|business develop\\w*"
            + "|sales development|sales (?:specialist|representative|rep|executive|lead)|quota"
            + "|executive assistant|tutor|annotator|publications|technical writ\\w*)\\b");

    /** Forward-deployed titles win outright, even when they also mention data or AI. */
    private static final Pattern FORWARD_DEPLOYED = p("forward[ -]deployed|\\bfde\\b");

    /** Engineering roles that sit with customers and are the closest relatives of FDE. */
    private static final Pattern SOLUTIONS = p("solutions? (engineer|architect)|customer engineer"
            + "|deployment (engineer|strategist)");

    private static final Pattern DATA_ENGINEER = p("data engineer|analytics engineer|\\betl\\b|data platform"
            + "|big data|data infrastructure|data warehous\\w*|data pipeline");

    private static final Pattern DATA_SCIENCE = p("data scien\\w*|applied scien\\w*|research scien\\w*"
            + "|decision scien\\w*|statistician|quantitative (research\\w*|analyst|developer|modell?er)");

    /** Wording that only an AI / machine-learning role uses. */
    private static final Pattern AI_ML = p("machine learning|deep learning|\\bnlp\\b|computer vision|\\bllms?\\b"
            + "|\\bgen ?ai\\b|generative ai|artificial intelligence|mlops|reinforcement learning"
            + "|\\b(ai|ml|ai/ml)\\s*(engineer|developer|scientist|researcher|architect|specialist|analyst|intern|lead)");

    /**
     * A bare "AI" or "ML" is often just a listed skill ("Software Engineer III - Java, React, AI"),
     * so it only makes an AI / ML role when nothing in the title says software.
     */
    private static final Pattern AI_ML_MENTION = p("\\bai\\b|\\bml\\b");

    /** A data or AI keyword alone ("AI Tutor") is not enough; it must be an actual technical role. */
    private static final Pattern TECH_ROLE_NOUN = p("engineer|scien\\w*|research|analyst|developer|architect"
            + "|intern|specialist|technical staff|\\bmts\\b|programmer|modell?er|statistician|lead");

    /** Data and BI analysis only — not business, finance, risk or operations analysts. */
    private static final Pattern DATA_ANALYST = p("data analys\\w*|business intelligence|\\bbi\\b|power ?bi"
            + "|tableau|looker|qlik|product analyst|insights analyst|advanced analytics"
            + "|\\b(data|marketing|product|growth|web|digital|people|customer|business) analytics"
            + "|analytics (analyst|developer|specialist|consultant)");

    /** Titles that say the work is software: role words, practices, platforms and languages. */
    private static final Pattern SOFTWARE = p("software|\\bsw\\b|\\bsde\\b|\\bswe\\b|developer|programmer"
            + "|back[ -]?end|front[ -]?end|full[ -]?stack|technical staff|\\bmts\\b|computer scien\\w*"
            + "|site reliability|\\bsre\\b|dev ?(sec)?ops|platform engineer|cloud (engineer|developer|architect"
            + "|infrastructure|platform)|mobile (engineer|developer|app)|\\bandroid\\b|\\bios\\b"
            + "|web (engineer|developer)|firmware|embedded|security engineer|cyber ?security|cyber defen[cs]e"
            + "|application security|product security|\\bappsec\\b|\\biam engineer|identity and access"
            + "|information security|infosec|security (architect|analyst|researcher)|\\bpki\\b"
            + "|threat (detection|intelligence|hunting)|penetration test\\w*|vulnerability"
            + "|\\bqa (engineer|automation|analyst|lead|tester)|quality assurance engineer|software quality"
            + "|\\bsdet\\b|test automation|automation test|software test|\\btester\\b|test analyst"
            + "|database (engineer|developer)|distributed systems|compiler|kernel|observability|mainframe|cobol"
            + "|\\bjava\\b|\\bpython\\b|\\bgolang\\b|c\\+\\+|\\bc#|\\.net\\b|\\bkotlin\\b|\\bscala\\b|\\brust\\b"
            + "|node\\.?js|typescript|javascript|\\bangular\\b|\\breact\\b|\\bvue\\b|kubernetes|\\bk8s\\b"
            + "|\\baws\\b|\\bazure\\b|\\bgcp\\b|microservices?|spring boot");

    /**
     * Plain "Engineer II" titles, only trusted at software companies.
     * "Engineering" on its own is usually a department ("Senior Executive - Engineering"), not a role.
     */
    private static final Pattern GENERIC_ENGINEER = p("\\bengineer\\b|\\bengineering lead\\b");

    /** Chip, hardware and plant engineering that shares words like "engineer" or "developer" with software. */
    private static final Pattern HARDWARE = p("hardware|\\bhw\\b|mechanical|electrical|civil|manufacturing"
            + "|facilities|construction|chemical|process engineer|asic|\\brtl\\b|silicon|analog|thermal"
            + "|field service|network engineer|audio|optical|\\bfea\\b|\\bcae\\b|\\bpcb\\b|circuit|\\blvs\\b"
            + "|\\bdrc\\b|runset|physical design|design engineer|design verification|ip verification|\\bams\\b"
            + "|mixed[ -]signal|wafer|\\byield\\b|equipment|\\bassy\\b|packaging|lithography|metrology|\\bdft\\b"
            // Plant, project and facilities engineering at industrial companies.
            + "|\\blayout\\b|project engineer|maintenance engineer|\\bplant\\b|production (engineer|planning"
            + "|and operations|supervisor)|chiller|\\bhvac\\b|commissioning|site engineer|structural|piping"
            + "|instrumentation|electronics engineer|\\brf\\b|antenna|substation|turbine"
            + "|data cent(er|re) (engineer|technician)|quality engineer|\\bpurchase\\b|procurement|\\besd\\b"
            // Chip design and verification at semiconductor companies.
            + "|verification|validation|memory design|\\bpdn\\b|\\bpnr\\b|place and route|\\bsoc\\b|\\bsta\\b"
            + "|timing|synthesis|\\bdv\\b|\\bchip\\b|tape-?out|foundry|power integrity|signal integrity"
            + "|standard cell|custom design|\\bcpu\\b|physical implementation|\\bfpga\\b|\\bmcad\\b"
            + "|hydraulic|controls? engineer|reliability engineer");
    /** …unless the title says the work is software after all ("Embedded Software Engineer, Hardware"). */
    private static final Pattern SOFTWARE_SIGNAL = p("software|firmware|\\bsde\\b|\\bswe\\b|\\bsw\\b|site reliability");

    private static final Pattern INTERNSHIP = p("\\b(intern|interns|internship|co-?op|apprentice\\w*|trainee"
            + "|summer analyst|industrial training|student)\\b");

    /** Classifies a title from a company whose engineering titles are not assumed to be software. */
    public static Optional<JobCategory> classify(String title) {
        return classify(title, false);
    }

    /**
     * @param softwareCompany true for companies whose engineers are software engineers (the
     *                        startups and tech companies on Greenhouse, Lever and Ashby), where a
     *                        plain "Staff Engineer" title is a software role
     */
    public static Optional<JobCategory> classify(String title, boolean softwareCompany) {
        if (title == null || title.isBlank()) {
            return Optional.empty();
        }
        String t = title.toLowerCase(Locale.ROOT);
        if (EXCLUDE.matcher(t).find()) {
            return Optional.empty();
        }
        if (FORWARD_DEPLOYED.matcher(t).find()) {
            return Optional.of(JobCategory.FORWARD_DEPLOYED);
        }
        // Data and AI roles first: "Data Analyst - Manufacturing" is still a data analyst.
        if (DATA_ENGINEER.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_ENGINEER);
        }
        boolean technical = TECH_ROLE_NOUN.matcher(t).find();
        if (DATA_SCIENCE.matcher(t).find() && technical) {
            return Optional.of(JobCategory.DATA_SCIENTIST);
        }
        if (AI_ML.matcher(t).find() && technical) {
            return Optional.of(JobCategory.AI_ML);
        }
        if (DATA_ANALYST.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_ANALYST);
        }
        if (HARDWARE.matcher(t).find() && !SOFTWARE_SIGNAL.matcher(t).find()) {
            return Optional.empty();
        }
        if (SOLUTIONS.matcher(t).find()) {
            return Optional.of(JobCategory.FORWARD_DEPLOYED);
        }
        boolean software = SOFTWARE.matcher(t).find();
        if (AI_ML_MENTION.matcher(t).find() && technical && !software) {
            return Optional.of(JobCategory.AI_ML);
        }
        if (software || (softwareCompany && GENERIC_ENGINEER.matcher(t).find())) {
            return Optional.of(JobCategory.SOFTWARE_DEVELOPER);
        }
        return Optional.empty();
    }

    /**
     * @param employmentHint the ATS's own employment-type field when it has one
     *                       (Lever "commitment", Ashby "employmentType"), otherwise empty
     */
    public static boolean isInternship(String title, String employmentHint) {
        if (employmentHint != null && employmentHint.toLowerCase(Locale.ROOT).contains("intern")) {
            return true;
        }
        return title != null && INTERNSHIP.matcher(title).find();
    }
}
