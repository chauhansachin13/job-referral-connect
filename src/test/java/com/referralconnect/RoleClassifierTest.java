package com.referralconnect;

import com.referralconnect.TestRunner.Test;
import com.referralconnect.model.JobCategory;
import com.referralconnect.scan.RoleClassifier;

import java.util.Optional;

import static com.referralconnect.TestRunner.check;
import static com.referralconnect.TestRunner.equal;
import static com.referralconnect.model.JobCategory.AI_ML;
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
        is(SOFTWARE_DEVELOPER, "Computer Scientist");
        is(SOFTWARE_DEVELOPER, "QA Engineer");
        is(SOFTWARE_DEVELOPER, "Test Automation Engineer");
        is(SOFTWARE_DEVELOPER, "Lead Cyber Defense Engineer, ITC");
        is(SOFTWARE_DEVELOPER, "Lead Engineer - Embedded SW Development");
        is(SOFTWARE_DEVELOPER, "Platform Engineer - Kubernetes");
    }

    @Test
    void plainEngineerTitlesCountOnlyAtSoftwareCompanies() {
        equal(Optional.of(SOFTWARE_DEVELOPER), RoleClassifier.classify("Staff Engineer, Revenue Experiences", true));
        ignored("Staff Engineer, Revenue Experiences");
        equal(Optional.of(SOFTWARE_DEVELOPER), RoleClassifier.classify("Caching Engineering Lead", true));
        ignored("Senior R/D Engineer");
        // Even at a software company, physical engineering is not software.
        equal(Optional.empty(), RoleClassifier.classify("Mechanical Engineer", true));
    }

    @Test
    void dataScienceAndAiAreSeparate() {
        is(DATA_SCIENTIST, "Data Scientist, Performance Analytics");
        is(DATA_SCIENTIST, "Data Scientist - Machine Learning");
        is(DATA_SCIENTIST, "Applied Scientist II");
        is(AI_ML, "Machine Learning Engineer");
        is(AI_ML, "Software Engineer, Machine Learning");
        is(AI_ML, "Member Technical Staff - Applied AI Engineer");
        is(AI_ML, "Generative AI Data Quality Engineer");
        is(AI_ML, "Senior MLOps Engineer - DSX Enablement");
        is(AI_ML, "PhD Intern, Apple Ads (Machine Learning)");
        is(AI_ML, "AI Benchmarking Specialist");
    }

    @Test
    void dataEngineeringAndAnalysis() {
        is(DATA_ENGINEER, "Data Engineer");
        is(DATA_ENGINEER, "Analytics Engineer");
        is(DATA_ANALYST, "Data Analyst");
        is(DATA_ANALYST, "Staff Data Analyst");
        is(DATA_ANALYST, "Senior Analyst, Advanced Analytics");
        is(DATA_ANALYST, "Product Analyst II");
        is(DATA_ANALYST, "Tableau Developer Analyst, Retail Customer Care");
        is(DATA_ANALYST, "Power BI Developer");
        is(DATA_ANALYST, "Data Analyst - Manufacturing");
        is(DATA_ANALYST, "Digital Marketing Data Analyst - Python and Pyspark");
    }

    @Test
    void forwardDeployedAndSolutionsEngineering() {
        is(FORWARD_DEPLOYED, "Forward Deployed Engineer, India");
        is(FORWARD_DEPLOYED, "Forward Deployed Architect II");
        is(FORWARD_DEPLOYED, "FDE - Enterprise");
        is(FORWARD_DEPLOYED, "Solutions Architect");
        is(FORWARD_DEPLOYED, "Solutions Engineer (Ahmedabad, India)");
        is(FORWARD_DEPLOYED, "Customer Engineer, Google Cloud");
    }

    @Test
    void nonCseRolesAreDropped() {
        // Real titles that the earlier, broader rules let in.
        ignored("Business Analyst II, Business assessment and reinforcement (BAR)");
        ignored("Senior Credit Risk Analyst, AVP (Hybrid)");
        ignored("Accounting & Reporting Analyst");
        ignored("Renewals Operations Analyst II");
        ignored("Application Support Engineer");
        ignored("Product Support Engineer III");
        ignored("Technical Services Engineer");
        ignored("Senior Technical Consultant - B2B");
        ignored("Mid-Market Sales Engineer");
        ignored("Zonal Service Engineer");
        ignored("Control Engineer – Drive Controls");
        ignored("Package Reliability Engineer");
        ignored("Supplier Engineer");
        ignored("Senior MCAD engineer");
        ignored("FPGA development Engineer");
        ignored("N_Bosch Rexroth India_ Engineer / Executive_Technical Sales_Hydraulics");
        ignored("Cloud Support Associate");
    }

    @Test
    void aSkillMentionDoesNotTurnSoftwareIntoAi() {
        is(SOFTWARE_DEVELOPER, "Software Engineer III - Java, Kafka, React, AI");
        is(SOFTWARE_DEVELOPER, "Full Stack Developer (React, Node, ML APIs)");
        is(SOFTWARE_DEVELOPER, "Senior Test Analyst - Telecom Applications & AI Testing - VOIS");
    }

    @Test
    void securityEngineeringIsCse() {
        is(SOFTWARE_DEVELOPER, "ICT Engineer- PKI Security");
        is(SOFTWARE_DEVELOPER, "Technical Consultant-Threat Detection Response & Intelligence");
        is(SOFTWARE_DEVELOPER, "Information Security Analyst");
        is(SOFTWARE_DEVELOPER, "Lead Authentication Engineer, Cybersecurity Engineering");
    }

    @Test
    void ignoresNonTargetRoles() {
        ignored("Engineering Manager");
        ignored("Technical Recruiter");
        ignored("Account Executive, Enterprise");
        ignored("AI Tutor - Hindi");
        ignored("Mechanical Engineer");
        ignored("Product Manager");
        ignored("Business Development Representative");
        ignored("Administrative Business Partner");
        ignored("");
        ignored(null);
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
        ignored("Architecture Validation Engineer");
        ignored("Engineer- Memory Design");
        ignored("Engineer-PDN");
        ignored("Graduate Engineer, SoC PnP Architect");
        ignored("Lead STA / Timing analysis Design Engineer");
        is(SOFTWARE_DEVELOPER, "Embedded Software Engineer, Hardware Platform");
        is(SOFTWARE_DEVELOPER, "Lead Staff Engineer, SSD Firmware Test");
        is(SOFTWARE_DEVELOPER, "Engineer - CAD SW");
        is(SOFTWARE_DEVELOPER, "Software Verification Engineer");
        is(SOFTWARE_DEVELOPER, "GPU Software Engineer");
    }

    @Test
    void industrialEngineeringIsNotSoftware() {
        ignored("Layout Engineer");
        ignored("Associate Project Engineer");
        ignored("Maintenance Engineer");
        ignored("Senior Executive- Engineering");
        ignored("Lead–WC Chiller Sustaining & Value Engineering");
        ignored("Data Centre Engineer - Hitachi Payment Services Pvt. Ltd.");
        ignored("Junior Engineer II, Production and Operations");
        ignored("Technical/Product Publications, Sr Engineer");
        ignored("Senior Scientist, translational Biomarkers and Bioanalytics");
        ignored("Purchase Engineer");
        ignored("ESD Engineer");
        is(SOFTWARE_DEVELOPER, "Software Quality Engineer - Automation");
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
