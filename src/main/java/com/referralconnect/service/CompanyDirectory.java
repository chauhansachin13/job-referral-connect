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

    /**
     * Companies with offices in India whose careers boards were checked to answer with India
     * openings. Grouped by the platform their careers site runs on.
     */
    public static final List<CompanyBoard> SEED = List.of(
            // Big tech with their own careers sites.
            new CompanyBoard("Amazon", Ats.AMAZON, "amazon"),
            new CompanyBoard("Apple", Ats.APPLE, "apple"),
            new CompanyBoard("Google", Ats.GOOGLE, "google"),
            new CompanyBoard("IBM", Ats.IBM, "ibm"),

            // iCIMS Jibe and Radancy careers sites: the careers host.
            new CompanyBoard("AMD", Ats.JIBE, "careers.amd.com"),
            new CompanyBoard("Synopsys", Ats.RADANCY, "careers.synopsys.com"),
            new CompanyBoard("Arm", Ats.RADANCY, "careers.arm.com"),
            new CompanyBoard("NetApp", Ats.RADANCY, "careers.netapp.com"),
            new CompanyBoard("Moody's", Ats.RADANCY, "careers.moodys.com"),

            // Eightfold-hosted careers sites: host|domain.
            eightfold("Microsoft", "apply.careers.microsoft.com|microsoft.com"),
            eightfold("Qualcomm", "careers.qualcomm.com|qualcomm.com"),
            eightfold("Morgan Stanley", "morganstanley.eightfold.ai|morganstanley.com"),
            eightfold("Ericsson", "jobs.ericsson.com|ericsson.com"),
            eightfold("Vodafone", "vodafone.eightfold.ai|vodafone.com"),
            eightfold("Netflix", "explore.jobs.netflix.net|netflix.com"),
            eightfold("Bayer", "bayer.eightfold.ai|bayer.com"),

            // Oracle Recruiting Cloud: host|siteNumber.
            new CompanyBoard("JPMorgan Chase", Ats.ORACLE, "jpmc.fa.oraclecloud.com|CX_1001"),
            new CompanyBoard("Texas Instruments", Ats.ORACLE, "edbz.fa.us2.oraclecloud.com|CX"),
            new CompanyBoard("Ford", Ats.ORACLE, "efds.fa.em5.oraclecloud.com|CX_1"),

            // Workday: tenant/wdN/site.
            workday("NVIDIA", "nvidia/wd5/NVIDIAExternalCareerSite"),
            workday("Salesforce", "salesforce/wd12/External_Career_Site"),
            workday("Adobe", "adobe/wd5/external_experienced"),
            workday("Intel", "intel/wd1/External"),
            workday("Cisco", "cisco/wd5/Cisco_Careers"),
            workday("PayPal", "paypal/wd1/jobs"),
            workday("Mastercard", "mastercard/wd1/CorporateCareers"),
            workday("Autodesk", "autodesk/wd1/Ext"),
            workday("Broadcom", "broadcom/wd1/External_Career"),
            workday("Micron", "micron/wd1/External"),
            workday("HP", "hp/wd5/ExternalCareerSite"),
            workday("Hewlett Packard Enterprise", "hpe/wd5/Jobsathpe"),
            workday("Workday", "workday/wd5/Workday"),
            workday("Red Hat", "redhat/wd5/jobs"),
            workday("Thomson Reuters", "thomsonreuters/wd5/External_Career_Site"),
            workday("Analog Devices", "analogdevices/wd1/External"),
            workday("NXP Semiconductors", "nxp/wd3/careers"),
            workday("Cadence", "cadence/wd1/External_Careers"),
            workday("Applied Materials", "amat/wd1/External"),
            workday("KLA", "kla/wd1/Search"),
            workday("Samsung", "sec/wd3/Samsung_Careers"),
            workday("Zoom", "zoom/wd5/Zoom"),
            workday("Kyndryl", "kyndryl/wd5/KyndrylProfessionalCareers"),
            workday("Ciena", "ciena/wd5/Careers"),
            workday("Target", "target/wd5/targetcareers"),
            workday("Nike", "nike/wd1/nke"),
            workday("Expedia Group", "expedia/wd108/search"),
            workday("Warner Bros. Discovery", "warnerbros/wd5/global"),
            workday("Gartner", "gartner/wd5/EXT"),
            workday("S&P Global", "spgi/wd5/SPGI_Careers"),
            workday("State Street", "statestreet/wd1/Global"),
            workday("Citi", "citi/wd5/2"),
            workday("Deutsche Bank", "db/wd3/DBWebsite"),
            workday("Nasdaq", "nasdaq/wd1/Global_External_Site"),
            workday("LSEG", "lseg/wd3/Careers"),
            workday("Accenture", "accenture/wd103/AccentureCareers"),
            workday("PwC", "pwc/wd3/Global_Experienced_Careers"),
            workday("Boeing", "boeing/wd1/EXTERNAL_CAREERS"),
            workday("General Motors", "generalmotors/wd5/Careers_GM"),
            workday("Caterpillar", "cat/wd5/CaterpillarCareers"),
            workday("GE Aerospace", "geaerospace/wd5/GE_ExternalSite"),
            workday("GE HealthCare", "gehc/wd5/GEHC_ExternalSite"),
            workday("Philips", "philips/wd3/jobs-and-careers"),
            workday("Shell", "shell/wd3/ShellCareers"),
            workday("Medtronic", "medtronic/wd1/MedtronicCareers"),
            workday("Pfizer", "pfizer/wd1/PfizerCareers"),
            workday("Novartis", "novartis/wd3/Novartis_Careers"),
            workday("AstraZeneca", "astrazeneca/wd3/Careers"),
            workday("GSK", "gsk/wd5/GSKCareers"),
            workday("Sanofi", "sanofi/wd3/SanofiCareers"),
            workday("Amgen", "amgen/wd1/Careers"),
            workday("Walmart", "walmart/wd504/WalmartExternal"),
            workday("Lowe's", "lowes/wd5/LWS_External_CS"),
            workday("Marvell", "marvell/wd1/MarvellCareers"),
            workday("Microchip", "microchiphr/wd5/External/myworkdaysite"),
            workday("Hitachi", "hitachi/wd1/hitachi"),
            workday("Harman", "harman/wd3/HARMAN"),
            workday("Aptiv", "aptiv/wd5/APTIV_CAREERS"),
            workday("Motorola Solutions", "motorolasolutions/wd5/Careers"),
            workday("CrowdStrike", "crowdstrike/wd5/crowdstrikecareers"),
            workday("PTC", "ptc/wd1/PTC"),
            workday("Equinix", "equinix/wd1/External"),
            workday("Comcast", "comcast/wd115/Comcast_Careers"),
            workday("Visa", "visa/wd5/Visa"),
            workday("Wells Fargo", "wf/wd1/WellsFargoJobs/myworkdaysite"),
            workday("Barclays", "barclays/wd3/External_Career_Site_Barclays"),
            workday("Northern Trust", "ntrs/wd1/northerntrust"),
            workday("BlackRock", "blackrock/wd1/BlackRock_Professional"),
            workday("Fidelity Investments", "fmr/wd1/FidelityCareers"),
            workday("Ameriprise Financial", "ameriprise/wd5/Ameriprise"),
            workday("Synchrony", "synchronyfinancial/wd5/Careers"),
            workday("FIS", "fis/wd5/SearchJobs"),
            workday("Fiserv", "fiserv/wd5/EXT"),
            workday("Broadridge", "broadridge/wd5/Careers"),
            workday("Morningstar", "morningstar/wd5/Morningstar"),
            workday("RELX", "relx/wd3/relx"),
            workday("Wolters Kluwer", "wk/wd3/External"),
            workday("Amex GBT", "travelhrportal/wd1/Jobs"),
            workday("FedEx", "fedex/wd1/FXE-MEISA-External"),
            workday("ABB", "abb/wd3/External_Career_Page"),
            workday("Johnson Controls", "jci/wd5/JCI"),
            workday("Carrier", "carrier/wd5/jobs"),
            workday("Otis", "otis/wd504/REC_Ext_Gateway"),
            workday("3M", "3m/wd1/Search"),
            workday("GE Vernova", "gevernova/wd5/Vernova_ExternalSite"),
            workday("BP", "bpinternational/wd3/bpCareers"),
            workday("Johnson & Johnson", "jj/wd5/JJ"),
            workday("Abbott", "abbott/wd5/abbottcareers"),
            workday("Eli Lilly", "lilly/wd115/LLY"),
            workday("Bristol Myers Squibb", "bristolmyerssquibb/wd5/BMS"),
            workday("MSD", "msd/wd5/SearchJobs"),
            workday("Takeda", "takeda/wd502/External"),
            workday("Roche", "roche/wd3/roche-ext"),

            // SmartRecruiters: company identifier.
            smartRecruiters("Bosch", "BoschGroup"),
            smartRecruiters("ServiceNow", "ServiceNow"),
            smartRecruiters("NielsenIQ", "NielsenIQ"),
            smartRecruiters("Freshworks", "Freshworks"),
            smartRecruiters("Experian", "Experian"),
            smartRecruiters("Continental", "Continental"),
            smartRecruiters("Canva", "Canva"),

            // Greenhouse, Lever and Ashby public job boards.
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

    private static CompanyBoard workday(String name, String token) {
        return new CompanyBoard(name, Ats.WORKDAY, token);
    }

    private static CompanyBoard eightfold(String name, String token) {
        return new CompanyBoard(name, Ats.EIGHTFOLD, token);
    }

    private static CompanyBoard smartRecruiters(String name, String token) {
        return new CompanyBoard(name, Ats.SMARTRECRUITERS, token);
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
