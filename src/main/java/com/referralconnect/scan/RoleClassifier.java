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
            + "|sales development|executive assistant|tutor|annotator)\\b");

    private static final Pattern FORWARD_DEPLOYED = p("forward[ -]deployed|\\bfde\\b|deployment (engineer|strategist)"
            + "|solutions? (engineer|architect)|customer (success |experience )?engineer|implementation engineer"
            + "|sales engineer|technical consultant|field engineer|support engineer|technical support"
            + "|services? engineer");

    private static final Pattern DATA_ENGINEER = p("data engineer|analytics engineer|\\betl\\b|data platform"
            + "|big data|data infrastructure|data warehous\\w*|data pipeline");

    private static final Pattern DATA_SCIENCE = p("data scien\\w*|machine learning|\\bml\\b|\\bai\\b|\\bai/ml\\b"
            + "|artificial intelligence|deep learning|\\bnlp\\b|computer vision|applied scien\\w*"
            + "|research scien\\w*|\\bllms?\\b|gen ?ai|mlops|quantitative research");

    /** A data-science keyword alone ("AI Tutor") is not enough; it must be an actual technical role. */
    private static final Pattern TECH_ROLE_NOUN = p("engineer|scien\\w*|research|analyst|developer|architect"
            + "|intern|specialist|technical staff|\\bmts\\b|programmer|modeler|statistician");

    private static final Pattern DATA_ANALYST = p("data analys\\w*|analytics|business intelligence|\\bbi\\b"
            + "|\\b(business|product|insights?|reporting|growth|marketing|risk|fraud|decision"
            + "|quantitative|strategy|pricing|supply chain|revenue) analyst");

    private static final Pattern SOFTWARE = p("software|\\bsde\\b|\\bswe\\b|developer|programmer|back[ -]?end"
            + "|front[ -]?end|full[ -]?stack|technical staff|\\bmts\\b|site reliability|\\bsre\\b|devops"
            + "|platform engineer|infrastructure engineer|cloud engineer|mobile engineer|\\bandroid\\b|\\bios\\b"
            + "|web engineer|systems engineer|security engineer|qa engineer|test engineer|\\bsdet\\b"
            + "|automation engineer|java|python|golang|react");

    /** Plain "Engineer II" titles are software roles unless they are clearly physical engineering. */
    private static final Pattern GENERIC_ENGINEER = p("\\bengineer(ing)?\\b");
    private static final Pattern NON_SOFTWARE_ENGINEERING = p("mechanical|electrical|hardware|civil|manufacturing"
            + "|facilities|construction|chemical|process engineer|asic|\\brtl\\b|silicon|analog|thermal"
            + "|field service|network engineer|audio|optical");

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
        if (DATA_ENGINEER.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_ENGINEER);
        }
        if (DATA_SCIENCE.matcher(t).find() && TECH_ROLE_NOUN.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_SCIENTIST);
        }
        if (DATA_ANALYST.matcher(t).find()) {
            return Optional.of(JobCategory.DATA_ANALYST);
        }
        if (SOFTWARE.matcher(t).find()) {
            return Optional.of(JobCategory.SOFTWARE_DEVELOPER);
        }
        if (GENERIC_ENGINEER.matcher(t).find() && !NON_SOFTWARE_ENGINEERING.matcher(t).find()) {
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
