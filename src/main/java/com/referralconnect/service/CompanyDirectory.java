package com.referralconnect.service;

import com.referralconnect.model.Ats;
import com.referralconnect.model.CompanyBoard;
import com.referralconnect.store.DataStore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The company job boards that get scanned: a built-in list of companies that hire in India for
 * the tracked roles, plus any board a referrer adds for their own company.
 */
public final class CompanyDirectory {

    /** Verified public boards with recent India openings in software, data or FDE roles. */
    public static final List<CompanyBoard> SEED = List.of(
            gh("Airbnb", "airbnb"),
            gh("Anthropic", "anthropic"),
            gh("Celonis", "celonis"),
            gh("Coinbase", "coinbase"),
            gh("Commvault", "commvault"),
            gh("Databricks", "databricks"),
            gh("Datadog", "datadog"),
            gh("Druva", "druva"),
            gh("Elastic", "elastic"),
            gh("Fivetran", "fivetran"),
            gh("GitLab", "gitlab"),
            gh("Harness", "harnessinc"),
            gh("HighRadius", "highradius"),
            gh("InMobi", "inmobi"),
            gh("MongoDB", "mongodb"),
            gh("Netskope", "netskope"),
            gh("New Relic", "newrelic"),
            gh("Observe.AI", "observeai"),
            gh("Okta", "okta"),
            gh("PubMatic", "pubmatic"),
            gh("Pure Storage", "purestorage"),
            gh("Razorpay", "razorpaysoftwareprivatelimited"),
            gh("Rubrik", "rubrik"),
            gh("Samsara", "samsara"),
            gh("Sigmoid", "sigmoid"),
            gh("Stripe", "stripe"),
            gh("Thoughtworks", "thoughtworks"),
            gh("Toast", "toast"),
            gh("Zscaler", "zscaler"),
            new CompanyBoard("CRED", Ats.LEVER, "cred"),
            new CompanyBoard("FamPay", Ats.LEVER, "fampay"),
            new CompanyBoard("Hevo Data", Ats.LEVER, "hevodata"),
            new CompanyBoard("Meesho", Ats.LEVER, "meesho"),
            new CompanyBoard("Mindtickle", Ats.LEVER, "mindtickle"),
            new CompanyBoard("Paytm", Ats.LEVER, "paytm"),
            new CompanyBoard("Atlan", Ats.ASHBY, "atlan"),
            new CompanyBoard("Composio", Ats.ASHBY, "composio"),
            new CompanyBoard("Notion", Ats.ASHBY, "notion"),
            new CompanyBoard("OpenAI", Ats.ASHBY, "openai"),
            new CompanyBoard("Sarvam AI", Ats.ASHBY, "sarvam"));

    private static CompanyBoard gh(String name, String token) {
        return new CompanyBoard(name, Ats.GREENHOUSE, token);
    }

    private final DataStore store;

    public CompanyDirectory(DataStore store) {
        this.store = store;
    }

    /** Seed and custom boards, one per key, sorted by company name. */
    public List<CompanyBoard> all() {
        Map<String, CompanyBoard> byKey = new LinkedHashMap<>();
        for (CompanyBoard b : SEED) {
            byKey.put(b.key(), b);
        }
        for (CompanyBoard b : store.read(s -> List.copyOf(s.customBoards))) {
            byKey.putIfAbsent(b.key(), b);
        }
        List<CompanyBoard> all = new ArrayList<>(byKey.values());
        all.sort(Comparator.comparing(b -> b.name().toLowerCase()));
        return all;
    }

    public Optional<CompanyBoard> find(String key) {
        return all().stream().filter(b -> b.key().equals(key)).findFirst();
    }

    /** Adds a referrer's own company board; returns the existing entry if it is already known. */
    public CompanyBoard add(CompanyBoard board) {
        if (board.name().isEmpty()) {
            throw new ServiceException("Enter the company name.");
        }
        if (board.token().isEmpty()) {
            throw new ServiceException("That careers link does not contain a board name.");
        }
        Optional<CompanyBoard> existing = find(board.key());
        if (existing.isPresent()) {
            return existing.get();
        }
        store.update(s -> {
            if (s.customBoards.stream().noneMatch(b -> b.key().equals(board.key()))) {
                s.customBoards.add(board);
            }
        });
        return board;
    }
}
