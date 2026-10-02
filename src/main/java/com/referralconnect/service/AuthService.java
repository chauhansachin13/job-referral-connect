package com.referralconnect.service;

import com.referralconnect.model.Account;
import com.referralconnect.model.CandidateProfile;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.model.Role;
import com.referralconnect.store.DataStore;

import java.time.Clock;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/** Registration, sign-in and profile updates for seekers and referrers. */
public final class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");
    public static final int MIN_PASSWORD_LENGTH = 8;

    private final DataStore store;
    private final Clock clock;

    public AuthService(DataStore store, Clock clock) {
        this.store = store;
        this.clock = clock;
    }

    public Account registerSeeker(String name, String email, String password) {
        return register(Role.SEEKER, name, email, password, null, "");
    }

    public Account registerReferrer(String name, String email, String password, CompanyBoard company,
                                    String designation) {
        if (company == null) {
            throw new ServiceException("Choose the company you can refer for.");
        }
        return register(Role.REFERRER, name, email, password, company, designation);
    }

    private Account register(Role role, String name, String email, String password, CompanyBoard company,
                             String designation) {
        String cleanName = name == null ? "" : name.trim();
        String cleanEmail = normalise(email);
        if (cleanName.isEmpty()) {
            throw new ServiceException("Enter your name.");
        }
        validateEmail(cleanEmail);
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new ServiceException("Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
        PasswordHasher.Hashed hashed = PasswordHasher.hash(password);
        return store.write(s -> {
            if (s.accounts.stream().anyMatch(a -> a.email().equals(cleanEmail))) {
                throw new ServiceException("An account with " + cleanEmail + " already exists. Sign in instead.");
            }
            Account a = new Account(UUID.randomUUID().toString(), role, clock.instant(), hashed.hash(),
                    hashed.salt(), CandidateProfile.of(cleanName, cleanEmail));
            if (company != null) {
                a.setCompany(company);
                a.setDesignation(designation);
            }
            s.accounts.add(a);
            return a;
        });
    }

    public Account login(String email, String password) {
        String cleanEmail = normalise(email);
        Optional<Account> account = store.read(s -> s.accounts.stream()
                .filter(a -> a.email().equals(cleanEmail)).findFirst());
        // Same message either way, so the form does not reveal which emails are registered.
        if (account.isEmpty() || password == null
                || !PasswordHasher.verify(password, account.get().passwordHash(), account.get().passwordSalt())) {
            throw new ServiceException("Wrong email or password.");
        }
        return account.get();
    }

    public Optional<Account> find(String accountId) {
        return store.read(s -> s.accounts.stream().filter(a -> a.id().equals(accountId)).findFirst());
    }

    public Account require(String accountId) {
        return find(accountId).orElseThrow(() -> new ServiceException("Account no longer exists."));
    }

    public Account updateProfile(String accountId, CandidateProfile profile) {
        if (profile.name().isEmpty()) {
            throw new ServiceException("Name cannot be empty.");
        }
        validateEmail(profile.email());
        return store.write(s -> {
            Account me = s.accounts.stream().filter(a -> a.id().equals(accountId)).findFirst()
                    .orElseThrow(() -> new ServiceException("Account no longer exists."));
            boolean emailTaken = s.accounts.stream()
                    .anyMatch(a -> a != me && a.email().equals(profile.email()));
            if (emailTaken) {
                throw new ServiceException("Another account already uses " + profile.email() + ".");
            }
            me.setProfile(profile);
            return me;
        });
    }

    public Account updateReferrerSettings(String accountId, String designation, boolean acceptingRequests) {
        return store.write(s -> {
            Account me = s.accounts.stream().filter(a -> a.id().equals(accountId)).findFirst()
                    .orElseThrow(() -> new ServiceException("Account no longer exists."));
            if (!me.isReferrer()) {
                throw new ServiceException("Only referrers have referral settings.");
            }
            me.setDesignation(designation);
            me.setAcceptingRequests(acceptingRequests);
            return me;
        });
    }

    private static String normalise(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validateEmail(String email) {
        if (!EMAIL.matcher(email).matches()) {
            throw new ServiceException("Enter a valid email address.");
        }
    }
}
