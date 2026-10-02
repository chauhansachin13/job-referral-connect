# Job Referral Connect

A desktop app that **scans company career boards for recently posted jobs and internships in India**
(Software Developer, Data Analyst, Data Scientist / ML, Data Engineer, Forward Deployed Engineer and
similar roles) and lets a job seeker **request a referral** from an employee at that company in one
click. The referrer receives the candidate's full details — resume, LinkedIn, GitHub, skills and a
short pitch — and marks the request *Referred*, *Needs more info* or *Declined*. The seeker sees every
update.

**100% Java.** No frameworks, no build tool, no third-party libraries — just the JDK (Swing for the UI,
`java.net.http` for scanning, `javax.crypto` for password hashing). It includes its own small JSON
parser and test runner.

![Openings with referral availability](docs/screenshots/02-seeker-openings.png)

---

## How it works

```
  Greenhouse ─┐
  Lever ──────┼──► JobScanner ──► filter: India + target role + posted in last 30 days
  Ashby ──────┘    (40 boards,         │
                    in parallel)       ▼
                              Openings table  ── "Get referral" ──►  ReferralService
                              (seeker)                                    │ routes to the referrer at that
                                                                          │ company with the fewest pending
                                                                          ▼ requests
                                                              Referrer inbox (referral packet)
                                                                          │
                                       Referred / Needs more info / Declined
                                                                          │
                                                              Seeker's "My referral requests"
```

### 1. Scanning recent openings

- Reads the **public job-board APIs** of Greenhouse, Lever and Ashby — the same JSON the companies'
  own careers pages use. No scraping, no login, no API keys.
- Ships with **40 companies** that currently hire in India for these roles (MongoDB, Okta, Zscaler,
  Stripe, Databricks, GitLab, Pure Storage, Rubrik, Netskope, Razorpay, Paytm, Meesho, CRED, Notion,
  OpenAI, Anthropic, Sarvam AI and more). A referrer whose company is missing can add it by pasting the
  careers-board link; it is checked once before it is accepted.
- Keeps only postings that are:
  - **in India** — Bengaluru, Hyderabad, Pune, Mumbai, Delhi NCR, Chennai, Kolkata, Ahmedabad, other
    Indian cities, or remote-India (while not mistaking *Indiana* or *Indianapolis* for India);
  - **a target role** — titles are classified into Software Developer, Data Analyst, Data Scientist /
    ML, Data Engineer and FDE / Solutions; managers, recruiters, sales and non-software engineering are
    dropped;
  - **recent** — first published in the last 30 days (filterable down to the last 24 hours).
- Internships are detected from the title (`intern`, `internship`, `co-op`, `apprentice`, …) and from
  the ATS's own employment-type field.
- A full scan of 40 boards checks ~8,000 postings in well under a minute (12 requests in parallel)
  and refreshes automatically when results are older than 30 minutes.

### 2. Requesting a referral (job seeker)

Each opening shows whether anyone at that company has signed up as a referrer. **Get referral** opens
a form pre-filled from the seeker's profile:

![Referral request form](docs/screenshots/07-request-dialog.png)

Rules that keep the system useful for referrers:

| Rule | Why |
| --- | --- |
| Resume link, skills and education/experience are required | A referrer cannot refer without them |
| Pitch of at least 30 characters | It's the one thing a resume doesn't say |
| One live request per job | No duplicates |
| At most 3 open requests per company | No flooding one company's referrers |
| Routed to the referrer with the **fewest pending requests** | Spreads the load |
| A referrer who declined you for a job is never asked again for it | Retry goes to someone else |

### 3. Acting on it (referrer)

The referrer's inbox shows each request as a **referral packet**: candidate, contact details,
resume / LinkedIn / GitHub links, the job, the pitch and a timeline.

![Referrer inbox](docs/screenshots/05-referrer-inbox.png)

- **Mark as referred** (optional note, e.g. "Submitted in our portal")
- **Ask for more info** (required note) — the seeker updates their details and resubmits
- **Decline** (required reason, so the candidate can improve)
- **Email candidate** opens the referrer's mail app, **Copy details** puts the packet on the clipboard
  for pasting into the company's internal referral form
- Referrers can pause new requests from **Settings**

```
PENDING ──► REFERRED
   │  ╲──► DECLINED
   │   ╲─► NEEDS_INFO ──► PENDING   (seeker resubmits)
   └─────► WITHDRAWN               (seeker cancels; also from NEEDS_INFO)
```

The seeker tracks everything under **My referral requests**:

![Seeker's requests](docs/screenshots/03-seeker-requests.png)

---

## Running it

Requires **Java 21 or newer** (`java -version`).

```bash
git clone https://github.com/chauhansachin13/job-referral-connect.git
cd job-referral-connect
javac -d out --source-path src/main/java src/main/java/com/referralconnect/Main.java
java -cp out com.referralconnect.Main --demo
```

`--demo` adds sample accounts so you can try both sides straight away (password `demo1234`):

| Role | Email |
| --- | --- |
| Job seeker | `seeker@demo.local` |
| Referrer at MongoDB | `referrer.mongodb@demo.local` |
| Referrer at Okta, Zscaler, GitLab, Stripe, Databricks, Pure Storage, Rubrik, Notion, Paytm, Netskope, Celonis | `referrer.<company>@demo.local` (e.g. `referrer.okta@demo.local`, `referrer.purestorage@demo.local`) |

**See both sides live:** start the app twice. Sign in as the seeker in one window and as the MongoDB
referrer in the other. Request a referral for a MongoDB job and it appears in the referrer's inbox
within a few seconds; act on it and the seeker's window updates.

Other options:

```bash
java -cp out com.referralconnect.Main                       # normal start (no demo accounts)
java -cp out com.referralconnect.Main --scan                # scan and print openings in the terminal
java -cp out com.referralconnect.Main --home /path/folder   # keep data somewhere else
```

On Java 22+ you can also skip `javac` entirely: `java src/main/java/com/referralconnect/Main.java --demo`.

### Tests

```bash
javac -d out-test --source-path src/main/java:src/test/java src/test/java/com/referralconnect/TestRunner.java
java -cp out-test com.referralconnect.TestRunner
```

47 tests cover the JSON parser, role and India classification, all three ATS parsers, the scanner
(with a fake HTTP layer), password hashing, the data store (persistence, two windows writing at once,
rollback, damaged files), sign-up/sign-in, and every referral rule and status transition.

---

## Where data lives

Everything is stored in one readable JSON file: `~/.job-referral-connect/data.json` (override with
`--home`, `-Dreferralconnect.home=…` or `REFERRALCONNECT_HOME`). Every write takes an OS file lock,
re-reads the file and replaces it atomically, so several app windows — or several machines pointing
`--home` at a shared folder — can use it at the same time without overwriting each other. Passwords
are stored as salted PBKDF2-SHA256 hashes.

## Project layout

```
src/main/java/com/referralconnect/
├── Main.java                 entry point (GUI, --demo, --scan, --home)
├── json/Json.java            minimal JSON parser/writer
├── model/                    Account, CandidateProfile, JobPosting, ReferralRequest, RequestStatus, …
├── scan/                     JobScanner, AtsParsers, RoleClassifier, IndiaLocations, BoardUrlParser
├── store/DataStore.java      file-locked JSON persistence
├── service/                  AuthService, JobService, ReferralService, CompanyDirectory, DemoData
└── ui/                       Swing screens: sign-in, seeker dashboard, referrer inbox, dialogs
src/test/java/com/referralconnect/
└── TestRunner.java + *Test.java
```

## Limitations

- Referrers' employment is not verified; anyone can sign up as a referrer for any listed company.
- Notifications are in-app (and via the referrer's own mail app); the app does not send email itself.
- Companies whose careers pages are not on Greenhouse, Lever or Ashby (e.g. Workday-only employers)
  can't be scanned.
