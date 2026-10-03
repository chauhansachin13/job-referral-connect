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

/**
 * Minimum experience must be exactly what the posting states — never guessed. The wordings below
 * come from real postings that an earlier version of the reader got wrong.
 */
class RequirementsTest {

    private static Requirements read(String title, String text) {
        return RequirementsExtractor.extract(title, text, true);
    }

    private static void reads(String expected, String title, String text) {
        equal(expected, read(title, text).shortLabel(), title);
    }

    @Test
    void readsTheStatedMinimumInTheUsualWordings() {
        reads("3+ yrs", "Custom Software Engineer", "<p>Must have skills : Jakarta EE</p><p>Good to have skills : NA</p>"
                + "<p>Minimum 3 year(s) of experience is required</p>"
                + "<p>Educational Qualification : 15 years full time education</p>");
        reads("3+ yrs", "SDE II", "- 3+ years of non-internship professional software development experience - 2+ years "
                + "of non-internship design or architecture experience");
        reads("5–7 yrs", "Senior QA Engineer", "5-7 years of experience in software quality engineering");
        reads("2+ yrs", "Data Engineer", "At least two (2) years of experience building ETL pipelines.");
        reads("0–2 yrs", "Software Engineer", "Experience: 0-2 years. Strong Java.");
        reads("Fresher", "Software Engineer", "Freshers are welcome to apply. Strong Java.");
        reads("13+ yrs", "Senior Software Engineer, AVP", "<p>Experience:</p><ul><li>A minimum of 13 years of "
                + "progressively responsible professional software engineering experience</li><li>Ideally, a minimum of "
                + "6 years of experience in financial services</li></ul>");
    }

    @Test
    void aValueOnTheLineAfterItsLabelIsRead() {
        // PwC: the label and the value are on separate lines.
        Requirements pwc = read("IN_Associate_ AI Engineer_GCC_Advisory_Mumbai", "<p>Years of experience required:</p>"
                + "<p>4 – 7 Years</p><p>Education Qualification:</p><p>B.Tech</p>");
        equal("4–7 yrs", pwc.shortLabel());
        equal(Basis.STATED, pwc.basis());
        reads("2–4 yrs", "Software Engineer", "<p>Industry Experience - 2 to 4 years</p>");
        reads("2–5 yrs", "Engineer 2, Software Development", "<p>Relevant Work Experience</p><p>2-5 Years</p>");
    }

    @Test
    void rangesWrittenWithSpacesDashesOrWords() {
        reads("4–8 yrs", "Machine Learning Engineer", "4 - 8 years of professional experience building ML solutions.");
        reads("4–6 yrs", "Software Engineer", "Experience: 4‑6 years, of which minimum 2–3 years in mobile.");
        reads("5–8 yrs", "API Developer", "Capability in backend engineering, typically gained across five to eight "
                + "years of professional experience.");
        reads("3–5 yrs", "QA Analyst", "Overall 3 - 5+ years of experience in Software Testing with at least 1-3 years "
                + "in ETRM.");
        reads("7.5+ yrs", "Staff SW Engineer", "<p>Education &amp; Experience:</p><p>7.5+ years of relevant software "
                + "engineering experience</p>");
        reads("4–10 yrs", "BMC Remedy Developer", "<li>4 ~10 years of product development and Implementation "
                + "experience</li>");
        reads("10–13 yrs", "Cybersecurity Architect", "<p>Your Background:</p><li>EXP Level - 10Yrs to 13Yrs</li>");
        reads("6–9 yrs", "Advanced Software Engineer", "<li>6Yrs to 9 Yrs of experience in Software Engineering "
                + "(Must)</li>");
        reads("4–7 yrs", "Data Scientist", "<li>Master’s OR Bachelor’s degree in computer science with a minimum of 4 "
                + "years and maximum of 7 years of Information Systems experience.</li>");
    }

    @Test
    void fractionsStayExactSoAFewMonthsIsNotFresher() {
        Requirements months = read("Reference Data Analyst", "<li>0.6 to 3 years of work experience in corporate "
                + "Banking</li>");
        equal("0.6–3 yrs", months.shortLabel());
        check(!months.fits(0) && months.fits(1), "0.6 years is more than none and less than one");
        Requirements accenture = read("Custom Software Engineer", "<p>Minimum 7.5 year(s) of experience is required</p>");
        check(!accenture.fits(7) && accenture.fits(8), "7 years does not meet 7.5");
        equal(accenture, Requirements.fromJson(accenture.toJson()), "7.5 survives a save and load");
    }

    @Test
    void theFirstFigureInASentenceIsTheOverallOne() {
        reads("12+ yrs", "ServiceNow Solution Architect", "<li>Minimum 12 years of ServiceNow development, including 2+ "
                + "years as an architect.</li>");
        reads("8+ yrs", "Senior Software Engineer", "<li>8+ years of software or integration engineering experience, "
                + "including at least 4 years of hands-on Workday integration development</li>");
    }

    @Test
    void strongWordingsCountWithoutTheWordExperience() {
        reads("10+ yrs", "Lead Solution Engineer", "<li>10+ years in IT, consulting, or technology implementation</li>");
        reads("5+ yrs", "Platform Engineer", "<li>5+ years with infrastructure as code tools like Terraform</li>");
        reads("2+ yrs", "BI Engineer", "- 2+ years of analyzing and interpreting data with Redshift");
    }

    @Test
    void preferredLinesNeverBecomeTheMinimum() {
        Requirements google = read("Software Engineer III", "<h3>Minimum qualifications:</h3><ul>"
                + "<li>Bachelor's degree or equivalent practical experience.</li>"
                + "<li>2 years of experience with software development in one or more programming languages, or 1 year "
                + "of experience with an advanced degree.</li></ul><h3>Preferred qualifications:</h3><ul>"
                + "<li>5 years of experience with Java.</li></ul>");
        equal(2.0, google.minYears(), "preferred 5 years and the advanced-degree route are ignored");
        equal("Bachelor's (or equivalent experience)", google.degree());

        // PayPal: the wish list has its own sub-headings.
        reads("3+ yrs", "Sr. Site Reliability Engineer", "<p>Minimum Qualifications:</p><ul><li>3+ years relevant "
                + "experience and a Bachelor’s degree</li></ul><p>Additional Responsibilities &amp; Preferred "
                + "Qualifications :</p><p>What do you need to bring</p><p>Technical Skills</p><ul><li>5+ years of "
                + "experience in site reliability engineering</li></ul>");
        // A heading saying "required" ends a wish list ("Preferred skill sets:" came first).
        reads("8–15 yrs", "Azure Data Engineer", "<p>Preferred skill sets:</p><p>Spark</p>"
                + "<p>Years of experience required:</p><p>8 to 15 years</p>");
        // "is a plus" inside brackets is about the degree, not the years.
        reads("6+ yrs", "Senior Analyst", "<li>6+ years of industry experience and an Advanced degree in Analytics "
                + "(Masters or PhD is a plus)</li>");
        // "Good to have skills : NA" is a field, not the start of a wish list.
        reads("7.5+ yrs", "Custom Software Engineer", "<p>Good to have skills : NA</p><p>Minimum 7.5 year(s) of "
                + "experience is required</p>");
        // Bosch: one run-on line; its "Good to have skills:" is far from the years.
        Requirements bosch = read("Angular Developer", "<p>Angular Developer Key Responsibilities:3 to 6 years "
                + "experienceDevelop, implement, and maintain modern, scalable, and responsive single-page applications "
                + "(SPAs) using Angular (versions 18+) and TypeScript.Collaborate closely with UI/UX designers to "
                + "translate wireframes into high-quality code.Write clean, maintainable and well tested code following "
                + "best practices.Experienced in AI driven development, Github Copilot Good to have skills:Debugging "
                + "ability to handle feature from front end to back end</p>");
        equal("3–6 yrs", bosch.shortLabel());
        equal(Basis.STATED, bosch.basis());
        // Thomson Reuters: an "Experience & Background" heading ends the "Nice to Have" list.
        reads("8–10 yrs", "Senior BI & AI Engineer", "<p>Nice to Have</p><li>Experience with DBT or Azure Data "
                + "Factory.</li><p>Experience &amp; Background</p><li>8–10 years of experience in BI engineering, data "
                + "engineering, or analytics engineering.</li>");
        // PwC marks the field with "*"; it is still the required-years heading.
        reads("13+ yrs", "IN_Senior Associate_MuleSoft Security Engineer", "<p>Preferred skill sets:</p><p>Java/Python</p>"
                + "<p>*Years of experience required</p><li>13+ years of overall professional experience.</li>");
    }

    @Test
    void textGluedTogetherBySiteIsSplitIntoItsFields() {
        // Bosch's careers site drops the line breaks between sections and sentences.
        Requirements glued = read("Senior Data Scientist", "<p>AI Application DeploymentQualificationsBachelor's Degree "
                + "(BE/BTech) in Computer Science or a related field.Master's Degree is an added advantage.8+ years of "
                + "experience in Data Science, Data Engineering, AI, or Analytics domains.</p>");
        equal("8+ yrs", glued.shortLabel(), "the added advantage is the Master's degree, not the years");
        equal(Basis.STATED, glued.basis());
        reads("6–9 yrs", "Senior Dot Net Software Developer", "<p>3. Web application tech stack - ASP .NET &amp; "
                + "Angular4. JIRAGood to Have skills:Agile MethodologiesYears of Experience:6 to 9 years</p>");
        check(read("Data Scientist", "<li>5+ years of data querying languages (e.g. SQL, Hive) experience</li>")
                .evidence().contains("SQL, Hive"), "\"e.g.\" does not end the quoted sentence");
    }

    @Test
    void aPreferenceIsShownOnlyWhenNoMinimumIsStatedAndIsLabelledSo() {
        Requirements amd = read("Linux Kernel Development engineer", "<p>PREFERRED EXPERIENCE: 9+ years of "
                + "Experience in Linux kernel development</p>");
        equal("Pref. 9+ yrs", amd.shortLabel());
        equal(Basis.PREFERRED, amd.basis());
        check(amd.longLabel().contains("preferred"), amd.longLabel());
        equal(Basis.PREFERRED, read("Staff Engineer, ESSD Firmware", "<li>6-12 Years in Embedded Firmware Storage/SSD "
                + "would be a strong plus.</li>").basis());
    }

    @Test
    void theOverallFigureWinsAndOnlyAlternativeDegreeRoutesAreSkipped() {
        reads("8+ yrs", "Senior Software Engineer", "<li>8+ years of software engineering experience</li>"
                + "<li>3+ years with Kafka</li>");
        reads("15–20 yrs", "Principal, Software Engineer", "<p>Minimum Qualifications:</p><p>Option 1: Bachelor's "
                + "degree in computer science and 5 years’ experience in software engineering.</p><p>What you'll bring:"
                + "</p><li>15–20 years of industry experience building large-scale distributed systems.</li>");
        reads("8–10 yrs", "Specialist Cloud Engineer", "<p>Master’s degree and 6 to 8 years of experience OR</p>"
                + "<p>Bachelor’s degree and 8 to 10 years of experience OR</p>"
                + "<p>Diploma and 10 to 12 years of experience</p>");
        reads("4–5 yrs", "Security Developer", "<li>Bachelor’s degree with 4 to 5+ years of software engineering</li>"
                + "<li>OR in lieu of degree, 6 to 7+ years of software engineering</li>");
        reads("5+ yrs", "Software Engineer", "Bachelors + 5 years of related experience, or Masters + 3 years of related "
                + "experience, or PhD + 0 years of related experience.");
        reads("5+ yrs", "Senior Statistical Programmer I", "<li>At least 3 years of industry experience with Master’s or "
                + "5+ years with Bachelor’s</li>");
        reads("5+ yrs", "Staff Software Engineer", "<li>5+ years of relevant work experience with a Bachelor’s Degree or "
                + "at least 2 years of work experience with an Advanced degree (e.g. Masters, MBA, JD, MD) or 0 years of "
                + "work experience with a PhD, OR 8+ years of relevant work experience.</li>");
    }

    @Test
    void historyAgesBreaksAndBenefitsAreNotExperience() {
        Requirements r = read("Senior Software Engineer", "We are a 25-year-old company founded in 1999. "
                + "PayPal has been revolutionizing commerce globally for more than 25 years. Complementary Health "
                + "screening for 35 yrs.");
        check(!r.known(), "nothing about the candidate's experience");
        reads("6+ yrs", "Senior Software Engineer II", "<li>6+ years of professional experience in frontend software "
                + "engineering</li><p>With an average length of service of 9 years, we are confident that we offer an "
                + "appealing working prospect for our people.</p>");
        reads("2–5 yrs", "Developer (Vapasi) - Intern", "<li>A minimum of 2 to 5 years of hands-on software development "
                + "experience before taking a career break</li><li>A career break lasting between 1 and 5 years</li>");
        // "years in business" is company history — unless a field follows ("Business Analysis").
        reads("7+ yrs", "AI Analyst", "<p>Required Qualifications</p><li>Bachelor's degree required</li><li>7+ years in "
                + "Business Analysis, Finance Analytics, or FP&amp;A Operations</li><li>2-3 years of Experience with "
                + "AI-powered tools</li>");
        check(!read("Engineer", "<p>Proudly serving customers with 50 years in business.</p>").known(),
                "a company's years in business");
    }

    @Test
    void entryLevelMeansTheRoleNotACareersProgram() {
        Requirements netapp = read("Cybersecurity Risk Analyst", "<p>The Cybersecurity Risk Lead is a senior-level "
                + "individual contributor.</p><p>NetApp Entry Level Careers Program</p><p>The NetApp Entry Level Careers "
                + "Program is designed to help you grow your career.</p>");
        check(!netapp.known(), "the careers-program paragraph on every NetApp posting is not this job");
        check(!read("Software Engineering Lead", "<li>Mentor and/or train entry-level software engineers</li>").known(),
                "the people the hire will mentor");
        reads("Fresher", "Associate Engineer", "<p>This is an entry-level position for recent graduates.</p>");
    }

    @Test
    void nothingIsGuessedFromTheTitle() {
        for (String title : List.of("Senior Software Engineer", "Staff Software Engineer", "Principal Data Scientist",
                "Software Engineer II", "Associate Software Engineer", "Lead Data Engineer", "Machine Learning Intern",
                "Graduate Engineer Trainee")) {
            Requirements r = RequirementsExtractor.fromTitle(title);
            check(!r.known(), title + " gives no years");
            equal("Not read yet", r.shortLabel(), title + ": only the title is known so far");
            Requirements read = read(title, "<p>Build services in Java on AWS.</p>");
            check(!read.known(), title + " with a description that gives no years");
            equal("Not stated", read.shortLabel(), title + ": read, and it states no years");
        }
        Requirements titled = RequirementsExtractor.fromTitle("Software Engineer - Networking | 4-8 Years");
        equal("4–8 yrs", titled.shortLabel(), "years written in the title are stated");
        equal(Basis.STATED, titled.basis());
    }

    @Test
    void degreeAndBatch() {
        Requirements grads = read("Graduate Engineer Trainee", "Open to 2025/2026 batch B.Tech CSE graduates. "
                + "Freshers welcome.");
        equal("Fresher", grads.shortLabel());
        equal("2025, 2026", grads.batch());
        equal("Bachelor's in Computer Science", grads.degree());
        equal("Bachelor's in STEM", read("Data Scientist", "<li>Bachelor’s degree in a STEM field.</li>").degree());
        equal("Master's in a quantitative field", read("Quant", "<li>Master's degree in a quantitative discipline.</li>")
                .degree());
    }

    @Test
    void requirementsSurviveASaveAndLoadAndOldResultsAreDropped() {
        Requirements r = new Requirements(3, 5, Basis.STATED, "3-5 years of Java", "Bachelor's", "2026",
                List.of("Java", "SQL"), true);
        equal(r, Requirements.fromJson(r.toJson()));
        equal(Requirements.UNKNOWN, Requirements.fromJson(Map.of()));
        check(r.fits(3) && !r.fits(2), "fits() compares with the minimum");
        // Saved by the earlier reader (no version): not shown, read again on the next scan.
        Requirements old = Requirements.fromJson(Map.of("min", 0, "basis", "ESTIMATED", "evidence",
                "Estimated from \"Associate\" in the title", "skills", List.of("Python"), "detailsRead", true));
        check(!old.known() && !old.detailsRead(), "old guesses are discarded");
        equal(List.of("Python"), old.skills());
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
