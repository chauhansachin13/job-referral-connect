package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.JobCategory;
import com.referralconnect.scan.RoleClassifier;

import java.util.Optional;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.model.JobCategory.DATA_ANALYST;
import static com.referralconnect.model.JobCategory.DATA_ENGINEER;
import static com.referralconnect.model.JobCategory.DATA_SCIENTIST;
import static com.referralconnect.model.JobCategory.FORWARD_DEPLOYED;
import static com.referralconnect.model.JobCategory.SOFTWARE_DEVELOPER;

class RoleClassifierTest {

    private static void is(JobCategory expected, String title) {
        equal(Optional.of(expected), RoleClassifier.classify(title), title);
    }

    private static void ignored(String title) {
        equal(Optional.empty(), RoleClassifier.classify(title), title);
    }

    @Test
    void softwareRoles() {
        is(SOFTWARE_DEVELOPER, "Software Engineer 3");
        is(SOFTWARE_DEVELOPER, "SDE 2 Infra");
        is(SOFTWARE_DEVELOPER, "Staff Software Development Engineer - Java/Go + Distributed Systems");
        is(SOFTWARE_DEVELOPER, "Senior Site Reliability Engineer");
        is(SOFTWARE_DEVELOPER, "Full Stack Developer");
        is(SOFTWARE_DEVELOPER, "Member of Technical Staff");
        is(SOFTWARE_DEVELOPER, "Senior Engineer - C++ (with Cloud)");
    }

    @Test
    void dataRoles() {
        is(DATA_ANALYST, "Data Analyst");
        is(DATA_ANALYST, "Staff Data Analyst");
        is(DATA_ANALYST, "Senior Analyst, Advanced Analytics");
        is(DATA_ANALYST, "Product Analyst II");
        is(DATA_SCIENTIST, "Data Scientist, Performance Analytics");
        is(DATA_SCIENTIST, "Machine Learning Engineer");
        is(DATA_SCIENTIST, "Software Engineer, Machine Learning");
        is(DATA_SCIENTIST, "Member Technical Staff - Applied AI Engineer");
        is(DATA_ENGINEER, "Data Engineer");
        is(DATA_ENGINEER, "Analytics Engineer");
    }

    @Test
    void forwardDeployedAndSimilar() {
        is(FORWARD_DEPLOYED, "Forward Deployed Engineer, India");
        is(FORWARD_DEPLOYED, "Forward Deployed Architect II");
        is(FORWARD_DEPLOYED, "FDE - Enterprise");
        is(FORWARD_DEPLOYED, "Solutions Architect");
        is(FORWARD_DEPLOYED, "Product Support Engineer III");
        is(FORWARD_DEPLOYED, "Technical Services Engineer");
    }

    @Test
    void ignoresNonTargetRoles() {
        ignored("Engineering Manager");
        ignored("Technical Recruiter");
        ignored("Account Executive, Enterprise");
        ignored("AI Tutor - Hindi");
        ignored("Mechanical Engineer");
        ignored("Product Manager");
        ignored("Renewals Operations Analyst II");
        ignored("Business Development Representative");
        ignored("Administrative Business Partner");
        ignored("");
        ignored(null);
    }

    @Test
    void aSkillMentionDoesNotTurnSoftwareIntoDataScience() {
        is(SOFTWARE_DEVELOPER, "Software Engineer III - Java, Kafka, React, AI");
        is(SOFTWARE_DEVELOPER, "Full Stack Developer (React, Node, ML APIs)");
        is(DATA_SCIENTIST, "AI Benchmarking Specialist");
        is(DATA_SCIENTIST, "Generative AI Data Quality Engineer");
        is(DATA_SCIENTIST, "Machine Learning Analyst-Python and SQL");
        is(DATA_SCIENTIST, "PhD Intern, Apple Ads (Machine Learning)");
    }

    @Test
    void biToolsAreDataAnalysis() {
        is(DATA_ANALYST, "Tableau Developer Analyst, Retail Customer Care");
        is(DATA_ANALYST, "Power BI Developer");
        is(DATA_ANALYST, "Data Analyst - Manufacturing");
    }

    @Test
    void hardwareAndChipRolesAreNotSoftware() {
        ignored("2026/03: HW Developer (PS-DC Projects)");
        ignored("FEA Engineer");
        ignored("Senior Engineer - Memory Circuit Design Verification");
        ignored("LVS Runset Development Engineer");
        ignored("Principal Engineer Assy Equipment & Process");
        ignored("Design Engineer II");
        ignored("Field Service Engineer");
        is(SOFTWARE_DEVELOPER, "Embedded Software Engineer, Hardware Platform");
        is(SOFTWARE_DEVELOPER, "Lead Staff Engineer, SSD Firmware Test");
    }

    @Test
    void industrialEngineeringIsNotSoftware() {
        // Real titles from industrial and pharma MNCs that used to slip into "Software Developer".
        ignored("Layout Engineer");
        ignored("Associate Project Engineer");
        ignored("Maintenance Engineer");
        ignored("Senior Executive- Engineering");
        ignored("Lead–WC Chiller Sustaining & Value Engineering");
        ignored("Data Centre Engineer - Hitachi Payment Services Pvt. Ltd.");
        ignored("Junior Engineer II, Production and Operations");
        ignored("Technical/Product Publications, Sr Engineer");
        ignored("Purchase Engineer");
        ignored("ESD Engineer");
        // Chip design and verification at semiconductor companies (Arm, Synopsys, AMD, TI…).
        ignored("Architecture Validation Engineer");
        ignored("Engineer- Memory Design");
        ignored("Engineer-PDN");
        ignored("Engineer- Tech PNR methodology");
        ignored("Graduate Engineer, SoC PnP Architect");
        ignored("Lead STA / Timing analysis Design Engineer");
        is(SOFTWARE_DEVELOPER, "Engineer - CAD SW");
        is(SOFTWARE_DEVELOPER, "Software Verification Engineer");
        is(SOFTWARE_DEVELOPER, "GPU Software Engineer");
        ignored("Senior Scientist, translational Biomarkers and Bioanalytics");
        is(SOFTWARE_DEVELOPER, "Caching Engineering Lead");
        is(SOFTWARE_DEVELOPER, "IBM Sterling OMS Senior Engineer");
        is(SOFTWARE_DEVELOPER, "Software Quality Engineer - Automation");
        is(SOFTWARE_DEVELOPER, "Senior Site Reliability Engineer");
        is(FORWARD_DEPLOYED, "Production Support Engineer");
    }

    @Test
    void detectsInternships() {
        check(RoleClassifier.isInternship("Software Engineer Intern", ""), "intern");
        check(RoleClassifier.isInternship("SDE Internship 2026", ""), "internship");
        check(RoleClassifier.isInternship("Data Science Co-op", ""), "co-op");
        check(RoleClassifier.isInternship("Software Engineer", "Intern"), "ATS employment type");
        check(!RoleClassifier.isInternship("International Payments Engineer", "Full-time"), "international");
        check(!RoleClassifier.isInternship("Internal Tools Engineer", "FullTime"), "internal");
    }
}
