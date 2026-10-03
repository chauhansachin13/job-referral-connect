package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.Requirements;
import com.referralconnect.model.Requirements.Basis;
import com.referralconnect.scan.RequirementsExtractor;
import com.referralconnect.scan.SkillCatalog;

import java.util.List;
import java.util.Map;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;

class RequirementsTest {

    private static Requirements read(String title, String text) {
        return RequirementsExtractor.extract(title, text, "", true);
    }

    @Test
    void readsTheStatedMinimumInTheUsualWordings() {
        equal(3, read("Custom Software Engineer", "<p>Must have skills : Jakarta EE</p><p>Good to have skills : NA</p>"
                + "<p>Minimum 3 year(s) of experience is required</p>"
                + "<p>Educational Qualification : 15 years full time education</p>").minYears(),
                "Accenture's template; 15 years of schooling is not experience");
        equal(3, read("SDE II", "- 3+ years of non-internship professional software development experience - 2+ years "
                + "of non-internship design or architecture experience").minYears(), "Amazon: the larger minimum wins");
        Requirements range = read("Senior QA Engineer", "5-7 years of experience in software quality engineering");
        equal(5, range.minYears());
        equal(7, range.maxYears());
        equal("5–7 yrs", range.shortLabel());
        equal(2, read("Data Engineer", "At least two (2) years of experience building ETL pipelines.").minYears());
        equal(0, read("Software Engineer", "Experience: 0-2 years. Strong Java.").minYears());
        equal(Basis.STATED, read("SDE", "4 yrs of hands-on Java development").basis());
        Requirements dashes = read("Software Engineer", "Experience: 4‑6 years, of which minimum 2–3 years of hands-on "
                + "experience developing mobile applications.");
        equal(4, dashes.minYears(), "a non-breaking hyphen still makes a range");
        equal(6, dashes.maxYears());
    }

    @Test
    void underscoresInTitlesDoNotHideTheLevel() {
        equal(4, RequirementsExtractor.fromTitle("IN_Senior Associate_MuleSoft Security Engineer", "").minYears());
    }

    @Test
    void ignoresThePreferredSectionAndTheAdvancedDegreeRoute() {
        Requirements google = read("Software Engineer III", "<h3>Minimum qualifications:</h3><ul>"
                + "<li>Bachelor's degree or equivalent practical experience.</li>"
                + "<li>2 years of experience with software development in one or more programming languages, or 1 year "
                + "of experience with an advanced degree.</li></ul><h3>Preferred qualifications:</h3><ul>"
                + "<li>5 years of experience with Java.</li></ul>");
        equal(2, google.minYears(), "preferred 5 years ignored; the advanced-degree route ignored");
        equal("Bachelor's (or equivalent experience)", google.degree());

        Requirements microsoft = read("Software Engineer II", "Required Qualifications: Bachelor's Degree in Computer "
                + "Science or related technical field AND 2+ years technical engineering experience with coding in "
                + "languages including C, C++, C#, Java, or Python OR Master's Degree in Computer Science AND 1+ "
                + "year(s) technical engineering experience. Preferred Qualifications: Bachelor's Degree AND 5+ years "
                + "experience.");
        equal(2, microsoft.minYears());
        check(microsoft.degree().startsWith("Bachelor's in Computer Science"), microsoft.degree());
        check(microsoft.evidence().contains("2+ years"), "the sentence is kept as evidence");
    }

    @Test
    void companyHistoryIsNotExperience() {
        Requirements r = read("Senior Software Engineer", "We are a 25-year-old company founded in 1999. "
                + "We have been building payments for over 20 years.");
        equal(Basis.ESTIMATED, r.basis(), "nothing stated, so the title gives an estimate");
        equal(4, r.minYears());
        equal("~4+ yrs", r.shortLabel());
    }

    @Test
    void freshersBatchesAndTitleEstimates() {
        Requirements grads = read("Graduate Engineer Trainee", "Open to 2025/2026 batch B.Tech CSE graduates. "
                + "Freshers welcome.");
        equal(0, grads.minYears());
        equal("Fresher", grads.shortLabel());
        equal("2025, 2026", grads.batch());
        equal("Bachelor's in Computer Science", grads.degree());

        Map<String, Integer> titles = Map.of(
                "Staff Software Engineer", 8, "Principal Data Scientist", 10, "Software Engineer II", 2,
                "Software Engineer III", 4, "Machine Learning Intern", 0, "Associate Software Engineer", 0,
                "Lead Data Engineer", 6);
        titles.forEach((title, years) -> {
            Requirements r = RequirementsExtractor.fromTitle(title, "");
            equal(years.intValue(), r.minYears(), title);
            equal(Basis.ESTIMATED, r.basis(), title);
            check(!r.detailsRead(), "title-only estimates are not marked as read");
        });
        equal(-1, RequirementsExtractor.fromTitle("Software Engineer", "").minYears(), "no level, no guess");
        equal(0, RequirementsExtractor.fromTitle("Developer", "Full-time Entry Level").minYears(),
                "the site's own experience level");
        equal("—", Requirements.UNKNOWN.shortLabel());
    }

    @Test
    void masterOnlyClausesDoNotMakeAFresherRole() {
        Requirements r = read("Data Scientist", "• Masters degree with 2 years of experience\n• PhD with 0 years\n"
                + "Nice to have: 6+ years with Spark");
        check(!r.known(), "only the advanced-degree routes state years");
    }

    @Test
    void requirementsSurviveASaveAndLoad() {
        Requirements r = new Requirements(3, 5, Basis.STATED, "3-5 years of Java", "Bachelor's", "2026",
                List.of("Java", "SQL"), true);
        equal(r, Requirements.fromJson(r.toJson()));
        equal(Requirements.UNKNOWN, Requirements.fromJson(Map.of()));
        check(r.fits(3) && !r.fits(2), "fits() compares with the minimum");
    }

    @Test
    void findsSkillsWithoutFalseFriends() {
        List<String> skills = SkillCatalog.find("Java, Spring Boot, REST APIs and MySQL; React and Node.js on AWS "
                + "with Docker and Kubernetes. You will excel at working with Go, C++ and PyTorch.");
        for (String s : List.of("Java", "Spring", "REST APIs", "MySQL", "React", "Node.js", "AWS", "Docker",
                "Kubernetes", "Go", "C++", "PyTorch")) {
            check(skills.contains(s), "finds " + s + " in " + skills);
        }
        check(!skills.contains("Excel"), "\"excel at\" is not Microsoft Excel");
        check(!skills.contains("JavaScript"), "Java is not JavaScript");
        check(SkillCatalog.find("Let's go to the office").isEmpty(), "lower-case go is a verb");
        check(SkillCatalog.fromProfile("java, springboot, Pandas, Figma").containsAll(List.of("Java", "Spring",
                "Pandas", "Figma")), "profile skills map to catalogue names and keep unknown ones");
    }
}
