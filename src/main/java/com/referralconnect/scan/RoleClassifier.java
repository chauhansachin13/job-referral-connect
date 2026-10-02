package com.referralconnect.scan;

import com.referralconnect.model.JobCategory;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Decides from a job title whether a posting is one of the tracked role families
 * (software developer, data analyst, data scientist, data engineer, forward-deployed engineer
 * and close relatives) and whether it is an internship.
 *
 * <p>Order matters: a "Software Engineer, Machine Learning" is filed under data science and an
 * "Analytics Engineer" under data engineering, because the more specific family is checked first.
 */
public final class RoleClassifier {

    private RoleClassifier() {
    }

    private static Pattern p(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
    }

    /** Leadership, recruiting and go-to-market titles that only look technical. */
    private static final Pattern EXCLUDE = p("\\b(manager|director|head of|vp|vice president|recruit\\w*|talent"
            + "|account executive|account manager|counsel|attorney|paralegal|business develop\\w*"
            + "|sales development|executive assistant|tutor|annotator|publications|technical writ\\w*)\\b");

    /** Forward-deployed titles win outright, even when they also mention data or AI. */
    private static final Pattern FORWARD_DEPLOYED = p("forward[ -]deployed|\\bfde\\b");

    /** Customer-facing engineering roles that are the closest relatives of FDE. */
    private static final Pattern SOLUTIONS = p("deployment (engineer|strategist)|solutions? (engineer|architect)"
            + "|customer (success |experience )?engineer|implementation engineer|sales engineer|technical consultant"
            + "|field engineer|support engineer|technical support|services? engineer");

    private static final Pattern DATA_ENGINEER = p("data engineer|analytics engineer|\\betl\\b|data platform"
            + "|big data|data infrastructure|data warehous\\w*|data pipeline");

    /** Wording that only a data-science / ML role uses. */
    private static final Pattern DATA_SCIENCE = p("data scien\\w*|machine learning|deep learning|\\bnlp\\b"
            + "|computer vision|applied scien\\w*|research scien\\w*|\\bllms?\\b|\\bgen ?ai\\b|generative ai"
            + "|artificial intelligence|mlops|quantitative research"
            + "|\\b(ai|ml|ai/ml)\\s*(engineer|developer|scientist|researcher|architect|specialist|analyst|intern)");

    /**
     * A bare "AI" or "ML" is often just a listed skill ("Software Engineer III - Java, React, AI"),
     * so it only makes a data-science role when the title is not already a software role.
     */
    private static final Pattern AI_ML_MENTION = p("\\bai\\b|\\bml\\b");
    private static final Pattern SOFTWARE_CORE = p("software (engineer|developer|development)|\\bsde\\b|\\bswe\\b"
            + "|full[ -]?stack|back[ -]?end|front[ -]?end|developer");

    /** A data-science keyword alone ("AI Tutor") is not enough; it must be an actual technical role. */
    private static final Pattern TECH_ROLE_NOUN = p("engineer|scien\\w*|research|analyst|developer|architect"
            + "|intern|specialist|technical staff|\\bmts\\b|programmer|modeler|statistician");

    private static final Pattern DATA_ANALYST = p("data analys\\w*|\\banalytics\\b|business intelligence|\\bbi\\b"
            + "|tableau|power ?bi|looker|qlik"
            + "|\\b(business|product|insights?|reporting|growth|marketing|risk|fraud|decision"
            + "|quantitative|strategy|pricing|supply chain|revenue) analyst");

    private static final Pattern SOFTWARE = p("software|\\bsde\\b|\\bswe\\b|developer|programmer|back[ -]?end"
            + "|front[ -]?end|full[ -]?stack|technical staff|\\bmts\\b|site reliability|\\bsre\\b|devops"
            + "|platform engineer|infrastructure engineer|cloud engineer|mobile engineer|\\bandroid\\b|\\bios\\b"
            + "|web engineer|systems engineer|security engineer|qa engineer|test engineer|\\bsdet\\b"
            + "|automation engineer|java|python|golang|react");

    /**
     * Plain "Engineer II" titles are software roles unless they are clearly physical engineering.
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
            + "|standard cell|custom design|\\bcpu\\b|physical implementation");
    /** …unless the title says the work is software after all ("Embedded Software Engineer, Hardware"). */
    private static final Pattern SOFTWARE_SIGNAL = p("software|firmware|embedded|\\bsde\\b|\\bswe\\b|\\bsw\\b");

    private static final Pattern INTERNSHIP = p("\\b(intern|interns|internship|co-?op|apprentice\\w*|trainee"
            + "|summer analyst|industrial training|student)\\b");

    public static Optional<JobCategory> classify(String title) {
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
        // Data roles first: "Data Analyst - Manufacturing" is still a data analyst.
        if (DATA_ENGINEER.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_ENGINEER);
        }
        if (DATA_SCIENCE.matcher(t).find() && TECH_ROLE_NOUN.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_SCIENTIST);
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
        if (AI_ML_MENTION.matcher(t).find() && TECH_ROLE_NOUN.matcher(t).find() && !SOFTWARE_CORE.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_SCIENTIST);
        }
        if (SOFTWARE.matcher(t).find() || GENERIC_ENGINEER.matcher(t).find()) {
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
