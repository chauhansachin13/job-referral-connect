package com.referralconnect.model;

import com.referralconnect.json.Json;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Per-account settings and bookkeeping that are not part of the referral profile.
 *
 * @param previousVisitAt     when the account's previous session started: openings first seen after
 *                            it are marked "new"
 * @param visitAt             when the current session started
 * @param notificationsReadAt notifications up to this time count as read
 * @param hiddenJobIds        openings the seeker marked "not interested"
 * @param preferredRoles      role families the seeker wants; empty for any
 * @param preferredCities     cities the seeker wants; empty for anywhere
 */
public record UserPrefs(
        Instant previousVisitAt,
        Instant visitAt,
        Instant notificationsReadAt,
        Set<String> hiddenJobIds,
        Set<JobCategory> preferredRoles,
        Set<String> preferredCities) {

    public UserPrefs {
        hiddenJobIds = Set.copyOf(hiddenJobIds);
        preferredRoles = preferredRoles.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(preferredRoles));
        preferredCities = Set.copyOf(preferredCities);
    }

    /** A first visit: nothing counts as "new" yet, and every notification is still unread. */
    public static UserPrefs fresh(Instant now) {
        return new UserPrefs(now, now, Instant.EPOCH, Set.of(), Set.of(), Set.of());
    }

    public UserPrefs startVisit(Instant now) {
        return new UserPrefs(visitAt, now, notificationsReadAt, hiddenJobIds, preferredRoles, preferredCities);
    }

    public UserPrefs readNotifications(Instant at) {
        return new UserPrefs(previousVisitAt, visitAt, at, hiddenJobIds, preferredRoles, preferredCities);
    }

    public UserPrefs hide(String jobId, boolean hidden) {
        Set<String> ids = new LinkedHashSet<>(hiddenJobIds);
        if (hidden) {
            ids.add(jobId);
        } else {
            ids.remove(jobId);
        }
        return new UserPrefs(previousVisitAt, visitAt, notificationsReadAt, ids, preferredRoles, preferredCities);
    }

    public UserPrefs withPreferences(Set<JobCategory> roles, Set<String> cities) {
        return new UserPrefs(previousVisitAt, visitAt, notificationsReadAt, hiddenJobIds, roles, cities);
    }

    /** Hidden ids of jobs that no longer exist are dropped so the set does not grow forever. */
    public UserPrefs keepHidden(Set<String> existingJobIds) {
        Set<String> ids = new LinkedHashSet<>(hiddenJobIds);
        ids.retainAll(existingJobIds);
        return ids.size() == hiddenJobIds.size() ? this
                : new UserPrefs(previousVisitAt, visitAt, notificationsReadAt, ids, preferredRoles, preferredCities);
    }

    public Map<String, Object> toJson() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("previousVisitAt", previousVisitAt.toString());
        m.put("visitAt", visitAt.toString());
        m.put("notificationsReadAt", notificationsReadAt.toString());
        m.put("hiddenJobIds", new ArrayList<>(hiddenJobIds));
        m.put("preferredRoles", preferredRoles.stream().map(Enum::name).sorted().toList());
        m.put("preferredCities", preferredCities.stream().sorted().toList());
        return m;
    }

    public static UserPrefs fromJson(Map<String, Object> m) {
        Set<String> hidden = new LinkedHashSet<>();
        Json.arr(m, "hiddenJobIds").forEach(o -> hidden.add(String.valueOf(o)));
        Set<JobCategory> roles = EnumSet.noneOf(JobCategory.class);
        for (Object o : Json.arr(m, "preferredRoles")) {
            try {
                roles.add(JobCategory.valueOf(String.valueOf(o)));
            } catch (IllegalArgumentException ignored) {
                // A role family that no longer exists.
            }
        }
        List<String> cities = new ArrayList<>();
        Json.arr(m, "preferredCities").forEach(o -> cities.add(String.valueOf(o)));
        return new UserPrefs(
                Instant.parse(Json.str(m, "previousVisitAt")),
                Instant.parse(Json.str(m, "visitAt")),
                Instant.parse(Json.str(m, "notificationsReadAt")),
                hidden, roles, new LinkedHashSet<>(cities));
    }
}
