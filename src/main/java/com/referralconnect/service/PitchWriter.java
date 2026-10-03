package com.referralconnect.service;

import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobPosting;
import com.referralconnect.scan.SkillCatalog;

import java.util.List;
import java.util.Set;

/**
 * Drafts the "why I'm a good fit" note from the seeker's profile and the posting. It is a
 * starting point the seeker edits: it only states facts from their own profile.
 */
public final class PitchWriter {

    private PitchWriter() {
    }

    public static String draft(CandidateProfile profile, JobPosting job) {
        Set<String> mine = SkillCatalog.fromProfile(profile.skills());
        List<String> jobSkills = job.requirements().skills().isEmpty()
                ? SkillCatalog.find(job.title()) : job.requirements().skills();
        List<String> overlap = SkillCatalog.overlap(jobSkills, mine);
        if (overlap.size() > 4) {
            overlap = overlap.subList(0, 4);
        }

        StringBuilder sb = new StringBuilder("Hi! I'd love to be considered for the ").append(job.title())
                .append(" role at ").append(job.company()).append(". ");
        String who = describe(profile);
        if (!who.isEmpty()) {
            sb.append("I'm ").append(who).append(". ");
        }
        if (!overlap.isEmpty()) {
            sb.append("The role asks for ").append(join(overlap)).append(", which I've worked with hands-on");
            sb.append(profile.experience().isEmpty() ? "" : " (" + profile.experience() + ")").append(". ");
        } else if (!profile.skills().isEmpty()) {
            sb.append("My core skills are ").append(profile.skills()).append(". ");
        }
        if (job.requirements().known() && profile.yearsKnown() && profile.years() >= job.requirements().minYears()
                && !job.internship()) {
            sb.append("I meet the ").append(job.requirements().minYears() == 0 ? "entry-level"
                    : job.requirements().minYears() + "+ year").append(" experience bar. ");
        }
        sb.append("[Add one project or result you're proud of that fits this team.] ");
        sb.append("Happy to share anything else that helps — thank you for considering a referral!");
        return sb.toString();
    }

    private static String describe(CandidateProfile p) {
        StringBuilder sb = new StringBuilder();
        if (!p.education().isEmpty()) {
            sb.append("a ").append(p.education()).append(" graduate");
        }
        if (p.yearsKnown()) {
            if (!sb.isEmpty()) {
                sb.append(" with ");
            }
            sb.append(p.years() == 0 ? "internship experience" : p.years() + (p.years() == 1 ? " year" : " years")
                    + " of experience");
        }
        return sb.toString();
    }

    private static String join(List<String> items) {
        if (items.size() == 1) {
            return items.get(0);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }
}
