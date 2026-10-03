package com.referralconnect.service;

import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.JobPosting;
import com.referralconnect.model.Requirements;
import com.referralconnect.model.UserPrefs;
import com.referralconnect.scan.SkillCatalog;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How well an opening fits a seeker, 0–100: their skills against the skills the posting names,
 * their years of experience against its minimum, and their preferred roles and cities.
 */
public final class JobMatcher {

    private JobMatcher() {
    }

    /** How many of a job's skills count; postings list their core skills first. */
    private static final int CORE_SKILLS = 6;

    /**
     * @param matched skills the seeker has that the job asks for
     * @param missing the job's core skills the seeker did not list
     * @param experienceFits false when the job asks for more years than the seeker has
     */
    public record Match(int score, List<String> matched, List<String> missing, boolean experienceFits) {

        /** "Strong match", "Good match", "Partial match", "Low match". */
        public String label() {
            return score >= 80 ? "Strong match" : score >= 65 ? "Good match" : score >= 45 ? "Partial match" : "Low match";
        }
    }

    public static Match match(JobPosting job, CandidateProfile profile, UserPrefs prefs) {
        return scorer(profile, prefs).match(job);
    }

    /** Scores many jobs for one seeker, reading their skills once. */
    public static Scorer scorer(CandidateProfile profile, UserPrefs prefs) {
        return new Scorer(profile, prefs);
    }

    /** Titles repeat a lot ("Software Engineer"); their skills are worked out once. */
    private static final Map<String, List<String>> TITLE_SKILLS = new ConcurrentHashMap<>();

    private static List<String> titleSkills(String title) {
        if (TITLE_SKILLS.size() > 20_000) {
            TITLE_SKILLS.clear();
        }
        return TITLE_SKILLS.computeIfAbsent(title, SkillCatalog::find);
    }

    public static final class Scorer {
        private final CandidateProfile profile;
        private final UserPrefs prefs;
        private final Set<String> mine;

        private Scorer(CandidateProfile profile, UserPrefs prefs) {
            this.profile = profile;
            this.prefs = prefs;
            this.mine = SkillCatalog.fromProfile(profile.skills());
        }

        public Match match(JobPosting job) {
            return JobMatcher.match(job, profile, prefs, mine);
        }
    }

    private static Match match(JobPosting job, CandidateProfile profile, UserPrefs prefs, Set<String> mine) {
        List<String> inTitle = titleSkills(job.title());
        List<String> jobSkills = job.requirements().skills().isEmpty() ? inTitle : job.requirements().skills();
        List<String> core = jobSkills.subList(0, Math.min(CORE_SKILLS, jobSkills.size()));
        List<String> matched = SkillCatalog.overlap(jobSkills, mine);
        List<String> missing = new ArrayList<>(core);
        missing.removeAll(matched);

        double skill;
        if (mine.isEmpty() || core.isEmpty()) {
            skill = 0.5; // nothing to compare: neither a plus nor a minus
        } else {
            long coreMatched = SkillCatalog.overlap(core, mine).size();
            skill = Math.min(1.0, (coreMatched + 0.5 * (matched.size() - coreMatched)) / Math.min(4, core.size()));
            // A title skill the candidate lacks ("Java Developer" for a Python person) weighs more.
            if (!inTitle.isEmpty() && SkillCatalog.overlap(inTitle, mine).isEmpty()) {
                skill *= 0.7;
            }
        }

        Requirements req = job.requirements();
        boolean fits = !profile.yearsKnown() || req.fits(profile.years());
        double experience;
        if (!profile.yearsKnown() || !req.known()) {
            experience = 0.6;
        } else if (profile.years() >= req.minYears()) {
            boolean wayOver = req.maxYears() >= 0 && profile.years() > req.maxYears() + 3;
            experience = wayOver ? 0.7 : 1.0;
        } else {
            double gap = req.minYears() - profile.years();
            // A preference is softer than a stated minimum.
            experience = Math.max(0, 1 - (req.preferredOnly() ? 0.15 : 0.25) * gap);
        }

        double role = prefs == null || prefs.preferredRoles().isEmpty() ? 0.6
                : prefs.preferredRoles().contains(job.category()) ? 1.0 : 0.15;
        double city = prefs == null || prefs.preferredCities().isEmpty() ? 0.6
                : prefs.preferredCities().stream().anyMatch(c -> job.city().contains(c)) ? 1.0 : 0.25;

        int score = (int) Math.round(100 * (0.45 * skill + 0.30 * experience + 0.15 * role + 0.10 * city));
        return new Match(Math.max(0, Math.min(100, score)), matched, missing, fits);
    }
}
