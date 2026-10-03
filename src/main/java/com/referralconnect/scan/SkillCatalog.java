package com.referralconnect.scan;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The technologies that job postings and candidate profiles are compared on. Each skill has one
 * display name and a pattern for the ways postings write it ("Node.js", "NodeJS", "node js").
 */
public final class SkillCatalog {

    private SkillCatalog() {
    }

    /** Display name → pattern. Case-insensitive unless the pattern turns that off. */
    private static final Map<String, Pattern> SKILLS = new LinkedHashMap<>();

    private static void add(String name, String regex) {
        SKILLS.put(name, Pattern.compile(regex, Pattern.CASE_INSENSITIVE));
    }

    /** For names that are ordinary words in other casings ("Go", "Rust", "Spark"). */
    private static void addCaseSensitive(String name, String regex) {
        SKILLS.put(name, Pattern.compile(regex));
    }

    static {
        // Languages
        add("Java", "\\bjava\\b(?!\\s*script)");
        add("Python", "\\bpython\\b");
        add("C++", "\\bc\\s*\\+\\+|\\bcpp\\b");
        add("C#", "\\bc\\s*#|\\.net\\b|\\bdotnet\\b");
        addCaseSensitive("C", "\\bC\\b(?=\\s*(?:,|/|and\\b|or\\b|programming|language|\\)))");
        addCaseSensitive("Go", "\\bGolang\\b|\\bgolang\\b|\\bGo\\b(?=\\s*(?:,|/|\\)|and\\b|or\\b|programming|language))");
        add("JavaScript", "\\bjavascript\\b|(?<![.\\w])js\\b|\\becmascript\\b");
        add("TypeScript", "\\btypescript\\b");
        add("Kotlin", "\\bkotlin\\b");
        addCaseSensitive("Swift", "\\bSwift(?:UI)?\\b");
        addCaseSensitive("Rust", "\\bRust\\b");
        add("Scala", "\\bscala\\b");
        addCaseSensitive("R", "(?<![\\w.&])R(?=\\s*(?:,|/|\\)|programming|language|studio\\b))|\\bRStudio\\b");
        add("SQL", "\\bsql\\b|\\bpl/sql\\b|\\bt-sql\\b");
        add("Bash / Shell", "\\bbash\\b|shell script\\w*|\\bunix shell\\b");
        add("Ruby", "\\bruby\\b|\\brails\\b");
        add("PHP", "\\bphp\\b");
        // Web and mobile
        add("React", "\\breact(?:\\.?js)?\\b(?!\\s*native)");
        add("React Native", "\\breact\\s*native\\b");
        add("Angular", "\\bangular(?:js)?\\b");
        add("Vue", "\\bvue(?:\\.?js)?\\b");
        add("Node.js", "\\bnode\\s*\\.?\\s*js\\b|\\bnodejs\\b");
        add("HTML / CSS", "\\bhtml5?\\b|\\bcss3?\\b");
        add("Android", "\\bandroid\\b");
        add("iOS", "\\bios\\b");
        add("Flutter", "\\bflutter\\b");
        // Back end
        add("Spring", "\\bspring(?:\\s*boot)?\\b");
        add("Django", "\\bdjango\\b");
        add("Flask", "\\bflask\\b");
        add("FastAPI", "\\bfastapi\\b");
        add("REST APIs", "\\brest(?:ful)?\\s*(?:apis?|services?|web services?)\\b|\\bapi design\\b");
        add("GraphQL", "\\bgraphql\\b");
        add("gRPC", "\\bgrpc\\b");
        add("Microservices", "\\bmicro-?services?\\b");
        add("Distributed systems", "\\bdistributed (?:systems?|computing)\\b");
        add("System design", "\\bsystem design\\b|\\bsystems? architecture\\b");
        add("Data structures & algorithms", "\\bdata structures?\\b|\\balgorithms?\\b");
        add("OOP", "\\bobject[- ]oriented\\b|\\boops?\\b");
        add("Kafka", "\\bkafka\\b");
        add("RabbitMQ", "\\brabbitmq\\b");
        add("Redis", "\\bredis\\b");
        // Data stores
        add("PostgreSQL", "\\bpostgre(?:s|sql)\\b");
        add("MySQL", "\\bmysql\\b");
        add("MongoDB", "\\bmongo(?:db)?\\b");
        add("NoSQL", "\\bnosql\\b");
        add("Cassandra", "\\bcassandra\\b");
        add("Elasticsearch", "\\belastic\\s*search\\b|\\bopensearch\\b");
        add("Oracle DB", "\\boracle (?:database|db|sql)\\b");
        // Cloud and ops
        add("AWS", "\\baws\\b|amazon web services");
        add("Azure", "\\bazure\\b");
        add("GCP", "\\bgcp\\b|google cloud");
        add("Docker", "\\bdocker\\b|\\bcontainers?\\b");
        add("Kubernetes", "\\bkubernetes\\b|\\bk8s\\b|\\beks\\b|\\baks\\b|\\bgke\\b");
        add("Terraform", "\\bterraform\\b|infrastructure as code|\\biac\\b");
        add("Linux", "\\blinux\\b|\\bunix\\b");
        add("CI/CD", "\\bci\\s*/\\s*cd\\b|continuous (?:integration|delivery|deployment)|\\bjenkins\\b|github actions");
        add("Git", "\\bgit\\b|\\bgithub\\b|\\bgitlab\\b|version control");
        add("DevOps", "\\bdevops\\b|\\bdevsecops\\b");
        add("Observability", "\\bobservability\\b|\\bprometheus\\b|\\bgrafana\\b|\\bdatadog\\b|\\bsplunk\\b");
        add("Networking", "\\btcp/ip\\b|\\bnetworking\\b|\\bnetwork protocols?\\b");
        add("Security", "\\b(?:application|network|cloud|information|cyber)\\s*security\\b|\\bcryptograph\\w*|\\boauth\\b|\\bsaml\\b|\\bsecure coding\\b");
        // Data engineering and analytics
        addCaseSensitive("Spark", "\\bSpark\\b|\\b[Pp]y[Ss]park\\b");
        add("Hadoop", "\\bhadoop\\b|\\bhive\\b|\\bhdfs\\b");
        add("Airflow", "\\bairflow\\b");
        add("Databricks", "\\bdatabricks\\b");
        add("Snowflake", "\\bsnowflake\\b");
        add("BigQuery", "\\bbigquery\\b");
        add("dbt", "\\bdbt\\b");
        add("ETL", "\\betl\\b|\\belt\\b|data pipelines?");
        add("Data warehousing", "\\bdata warehous\\w*\\b|\\bdata lake\\w*\\b|\\blakehouse\\b");
        add("Tableau", "\\btableau\\b");
        add("Power BI", "\\bpower\\s*bi\\b");
        add("Looker", "\\blooker\\b");
        addCaseSensitive("Excel", "\\b(?:MS |Microsoft )?Excel\\b");
        add("Statistics", "\\bstatistic(?:s|al)\\b|\\bhypothesis testing\\b|\\ba/b test\\w*");
        add("Pandas", "\\bpandas\\b");
        add("NumPy", "\\bnumpy\\b");
        // AI / ML
        add("Machine learning", "\\bmachine learning\\b|\\bml\\b");
        add("Deep learning", "\\bdeep learning\\b|\\bneural networks?\\b");
        add("NLP", "\\bnlp\\b|natural language processing");
        add("Computer vision", "\\bcomputer vision\\b|\\bopencv\\b|image processing");
        add("LLMs / GenAI", "\\bllms?\\b|large language models?|\\bgen\\s*ai\\b|generative ai|\\brag\\b|prompt engineering"
                + "|\\blangchain\\b|\\bagentic\\b|\\bai agents?\\b");
        add("PyTorch", "\\bpytorch\\b|\\btorch\\b");
        add("TensorFlow", "\\btensorflow\\b|\\bkeras\\b");
        add("scikit-learn", "\\bscikit[- ]learn\\b|\\bsklearn\\b");
        add("MLOps", "\\bmlops\\b|\\bmlflow\\b|\\bkubeflow\\b|\\bsagemaker\\b|\\bvertex ai\\b");
        // Testing and embedded
        add("Test automation", "\\bselenium\\b|\\bcypress\\b|\\bplaywright\\b|\\bjunit\\b|\\bpytest\\b|\\btestng\\b"
                + "|test automation|automation testing");
        add("Embedded", "\\bembedded\\b|\\bfirmware\\b|\\brtos\\b|\\bmicrocontrollers?\\b");
        add("SAP", "\\bsap\\b|\\babap\\b");
    }

    /** Every skill name, in catalogue order. */
    public static List<String> names() {
        return List.copyOf(SKILLS.keySet());
    }

    /**
     * The skills a text mentions, ordered by where they first appear (so the title's skills come
     * first when the title is passed in front of the description).
     */
    public static List<String> find(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Map<Integer, String> byPosition = new java.util.TreeMap<>();
        for (Map.Entry<String, Pattern> e : SKILLS.entrySet()) {
            Matcher m = e.getValue().matcher(text);
            if (m.find()) {
                int at = m.start();
                while (byPosition.containsKey(at)) {
                    at++; // two skills matched at the same spot ("React" in "React Native"): keep both
                }
                byPosition.put(at, e.getKey());
            }
        }
        return List.copyOf(new LinkedHashSet<>(byPosition.values()));
    }

    /**
     * Skills a candidate lists, as catalogue names where they match one ("springboot" → "Spring"),
     * plus anything else they wrote, so an unusual skill can still match a job title word.
     */
    public static Set<String> fromProfile(String skillsText) {
        Set<String> out = new LinkedHashSet<>(find(skillsText));
        if (skillsText != null) {
            for (String part : skillsText.split("[,;/|·\\n]+")) {
                String p = part.trim();
                if (!p.isEmpty() && find(p).isEmpty()) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /** Lower-case keys for comparing skill names regardless of how they were written. */
    public static Set<String> keys(java.util.Collection<String> skills) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : skills) {
            out.add(s.toLowerCase(Locale.ROOT).trim());
        }
        return out;
    }

    /** How many of the job's skills the candidate has, and which. */
    public static List<String> overlap(java.util.Collection<String> jobSkills, java.util.Collection<String> candidate) {
        Set<String> have = keys(candidate);
        List<String> out = new ArrayList<>();
        for (String s : jobSkills) {
            if (have.contains(s.toLowerCase(Locale.ROOT))) {
                out.add(s);
            }
        }
        return out;
    }
}
