# Project Kavach — The Family Shield

### ET Hackathon Submission Document

| | |
| :--- | :--- |
| **Project** | Kavach (कवच — "armour / shield") |
| **Team** | **Believers** |
| **Team Members** | Devansh Srivastava · Shaurya Singh |
| **Category** | AI for Social Good / Fraud Prevention |
| **Problem addressed** | Digital-arrest & financial-fraud targeting elderly citizens |

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [The Problem — Why This Matters](#2-the-problem--why-this-matters)
3. [The Core Insight (The Reframe)](#3-the-core-insight-the-reframe)
4. [The Solution — Four Layers of Protection](#4-the-solution--four-layers-of-protection)
5. [System Architecture](#5-system-architecture)
6. [Technology Stack](#6-technology-stack)
7. [How It Works — Component Deep Dive](#7-how-it-works--component-deep-dive)
8. [The Android Application](#8-the-android-application)
9. [Data Model & API Surface](#9-data-model--api-surface)
10. [Privacy, Consent & Legal Design](#10-privacy-consent--legal-design)
11. [The Live Demo Flow](#11-the-live-demo-flow)
12. [Business Model & Go-To-Market](#12-business-model--go-to-market)
13. [Competitive Analysis](#13-competitive-analysis)
14. [Risks & Honest Limitations](#14-risks--honest-limitations)
15. [Why Kavach Wins on the Rubric](#15-why-kavach-wins-on-the-rubric)
16. [Roadmap](#16-roadmap)
17. [Team](#17-team)

---

## 1. Executive Summary

**Digital arrest is a siege, not a call.** It runs for hours or days, its weapon is *isolation*, and its objective is a single UPI/RTGS transfer. Every existing anti-scam product tries to *listen to the call* — a door that is both technically welded shut (OS/privacy walls) and already occupied by a giant (Google Scam Detection).

Kavach takes the door the scam itself points at. The scam works by cutting a frightened elder off from their family — so the product that beats it is the one that **wires the family back in.**

Kavach is a multi-layer fraud-protection platform with three defensible ideas:

1. **Audio-free siege detection.** We never touch the call audio. The siege leaks unmistakable *behavioral* signals any Android app can read with normal permissions: an hours-long call with an unknown/international number → a screen-share event → the banking app opening → a large first-time UPI intent. An on-device + backend risk engine fuses these into a graduated risk score.
2. **Guardian Mesh.** The elder's phone is paired to 1–2 trusted family "guardians." When risk crosses a threshold, the guardian receives a **real-time, content-free alert** — *"Mom has been on a video call with ****0001 for 2h 40m and just opened her bank app"* — with a one-tap call to intervene. One phone call from a trusted human collapses the entire scam.
3. **Zero-learning UX.** The elder installs nothing new to learn. Day-to-day protection runs over **WhatsApp**, which they already use: forward any suspicious text/screenshot/voice note/number → get an instant verdict, matched red flags, and a safe next step in Hindi/English.

Underneath sits an **intelligence backend**: an opt-in one-tap live-audio Deep-Check (Whisper STT + LLM script classifier + acoustic AI-voice heuristic), a **Neo4j fraud graph** clustering numbers → mule accounts → devices, and **tamper-evident, Section-65B-friendly evidence packages** for law enforcement.

> **Positioning:** *"Google protects a phone. Truecaller screens a number. Kavach protects a family — because the digital-arrest scam is not defeated by hearing the call, it is defeated by breaking the silence."*

---

## 2. The Problem — Why This Matters

The "digital arrest" scam is a real, large, and lethal problem in India — not a hypothetical:

- **≈ ₹3,000 crore/year** in reported digital-arrest losses; **30,000+** complaints on record.
- **Suicides** attributed to these scams; a **Supreme Court-monitored CBI probe**; the **Prime Minister** addressing it directly in *Mann Ki Baat*; a **DoT preinstall mandate** debated in Parliament.
- The victims are overwhelmingly **elderly** — the demographic least equipped to spot a scam mid-panic and most trusting of "authority."

**The mechanics of the attack:**
A scammer impersonating CBI/ED/police/customs calls (often over a WhatsApp video call to appear "official"), tells the victim they are implicated in a serious crime (money laundering, a seized parcel, an Aadhaar-linked case), and places them under a fake **"digital arrest"** — a psychological siege lasting hours or even days. The victim is ordered to stay on camera, not to disconnect, and not to contact anyone. The siege ends only when the terrified victim transfers money to "verify their innocence."

**Why existing solutions fail this specific scam:**
- **Call-listening tools** (upload-a-recording checkers, real-time dialers) require the victim to *already be alert enough to check* — the very thing a siege destroys. They are also OS-blocked, privacy-radioactive, and duplicated natively by Google.
- **Single-device protection** (Google, Truecaller) cannot see WhatsApp video streams, cannot model a multi-hour cross-app siege, and — by privacy design — **cannot alert a family member.**

The gap is structural: **nobody protects the family graph for this scenario.** That gap is Kavach.

---

## 3. The Core Insight (The Reframe)

Three reframes drive the entire design:

1. **You don't need the audio.** The siege leaks behavioral signals observable with normal Android permissions:
   `long call/video with unsaved international/VoIP number` → `screen-share event` → `banking app foreground during the call` → `large first-time UPI intent`.
   Detecting the *shape* of the siege sidesteps the entire audio/privacy wall.

2. **You don't need to convince the victim.** A terrified person under psychological siege cannot be reasoned with mid-attack. You need to alert **one trusted human** whose single phone call breaks the spell.

3. **You don't need the elder to learn anything.** The app is installed and configured by their **adult children**. The elder's only touchpoint is **WhatsApp**, which they already use daily.

The detection window is **hours-to-days** (the deadline is *money movement*, not call pickup), and the weapon is **isolation** — so the counter-weapon is a **guardian layer** that no incumbent ships for this scenario.

---

## 4. The Solution — Four Layers of Protection

| # | Layer | What it does | Problem it solves |
| :--- | :--- | :--- | :--- |
| **1** | **Fraud Shield on WhatsApp** | A WhatsApp bot (Meta Cloud API / Twilio). Forward any suspicious text, screenshot, voice note, or number → instant verdict + matched red flags + safe next step (*"hang up; real police never video-call; dial 1930"*) in Hindi/English. | **UX:** zero new app for the elder to learn — the channel *is* the product. |
| **2** | **Siege Sentinel (Android, audio-free)** | On-device risk engine over behavioral signals: unknown/international number, call & video-call duration, screen-share start, foreground app sequence (dialer→WhatsApp→bank→UPI), time-of-day, first-time-payee heuristics. Graduated risk score; full-screen *"There is no such thing as digital arrest"* interrupt at high risk with one-tap 1930 & guardian call. | **Detection without audio:** covers normal calls AND WhatsApp video without touching any audio stream. |
| **3** | **Guardian Mesh** | Elder's phone paired to 1–2 family guardians. Risk threshold crossed → guardian gets a real-time, **content-free** alert with context + one-tap call/intervene. Consent-first: guardian sees *signals*, never *content*. | **The differentiator:** attacks the scam's isolation mechanic. Nobody protects the family graph. |
| **4** | **Deep-Check & Intelligence Backend** | Opt-in one-tap live-audio check (speakerphone mic → Whisper STT → LLM script classifier + AI-voice/spoof heuristic — legal, user-triggered, participant recording). Neo4j fraud graph clustering numbers→mule accounts→devices; hash-stamped Section-65B-friendly evidence packages; one-tap Chakshu/1930 reporting. | **Technical depth + investigator view + legal admissibility.** |

**Core (non-negotiable) features:** WhatsApp bot verdicts (Hindi/English) · siege risk engine with graduated alerts · guardian alert + one-tap intervene · full-screen digital-arrest interrupt with 1930 shortcut · explainable red flags on every verdict · investigator graph screen · evidence package.

**Explicit non-goals (stated with pride — discipline is a feature):** no silent call recording · no interception of WhatsApp streams · no claim of reliable passive deepfake detection · no cloud audio without a user tap.

---

## 5. System Architecture

Kavach is a **monorepo** with two deployable units plus a supporting service mesh:

- **`kavach-backend/`** — Python 3.12 / FastAPI async API, Celery workers, and a 5-service Docker infrastructure.
- **`kavach-android/`** — Kotlin / Jetpack Compose app (Clean Architecture: `data` / `domain` / `presentation`), the Siege Sentinel + Guardian console.

```
┌──────────────────────────────────────────────────────────────────────────┐
│                              CLIENTS                                        │
│                                                                            │
│  Elder's WhatsApp   Elder's Android      Guardian's Android/Web            │
│  (Fraud Shield)     (Siege Sentinel)     (Guardian Console)                │
└─────────┬──────────────────┬────────────────────┬─────────────────────────┘
          │ webhook          │ signal batches     │ WebSocket + FCM
          │                  │ /api/v1/signals    │ /ws/guardian/{id}
          ▼                  ▼                    ▲
┌──────────────────────────────────────────────────────────────────────────┐
│                        FASTAPI (async REST + WS)                           │
│  whatsapp · signals · guardians · deepcheck · graph · billing · ws · health│
│  ── JWT auth · role guards · Redis token-bucket rate limit · CORS ──       │
└─────────┬───────────────────────────────────────────┬──────────────────────┘
          │ enqueue (Redis broker)                     │ read/write
          ▼                                            ▼
┌────────────────────────┐              ┌──────────────────────────────────┐
│   CELERY WORKERS        │              │        SERVICE LAYER              │
│  • risk_engine.evaluate │◄────────────►│  classifier · rules_engine       │
│  • deepcheck.run        │              │  rag · llm_router · risk_engine   │
│  • alert.send_fcm_push  │              │  deepcheck_chain (LangGraph)     │
└──────┬─────────┬────────┘              │  spoof_detector · whisper_service │
       │         │                       │  evidence_builder · graph_service │
       │         │                       │  alert_dispatch                   │
       ▼         ▼                       └──────────────────────────────────┘
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────────────┐
│PostgreSQL│ │  Redis   │ │  Neo4j   │ │  MinIO   │ │  LLM providers        │
│+ pgvector│ │broker/   │ │fraud     │ │audio +   │ │  (Groq/OpenAI/        │
│(corpus,  │ │pubsub/   │ │graph     │ │evidence  │ │   Anthropic/Gemini    │
│incidents)│ │ratelimit │ │(rings)   │ │PDFs)     │ │   via LiteLLM)        │
└──────────┘ └──────────┘ └──────────┘ └──────────┘ └──────────────────────┘
```

**Design principles baked into the architecture:**
- **Fast ingest, async compute.** Signal ingestion returns `200 OK` immediately; risk evaluation happens in a Celery worker. WhatsApp verdicts short-circuit on high-confidence rules for a `<1s` response.
- **Framework-agnostic services.** The `services/` layer has no FastAPI imports — every business rule is independently unit-testable.
- **Rules-first, LLM-second.** Deterministic rules cap false positives and make every verdict explainable; the LLM is a fallback reasoner, never the sole arbiter.
- **Content-free by default.** Guardians and the backend see behavioral *signals*, not call content.

---

## 6. Technology Stack

| Component | Technology | Role |
| :--- | :--- | :--- |
| **Core Framework** | Python 3.12, FastAPI 0.115 | Async REST API, high-speed request processing. |
| **Primary Database** | PostgreSQL 15 + `pgvector` | Persistent storage + semantic vector search over the scam-script corpus. |
| **Caching & Pub/Sub** | Redis 7 | Celery broker, rate-limiter cache, real-time WebSocket pub/sub for guardian alerts. |
| **Background Tasks** | Celery 5.4 (+ Flower) | Risk evaluation, deep-check pipeline, FCM push dispatch. |
| **Graph Database** | Neo4j 5 (self-hosted) | Phone→device→mule-account linkage to expose fraud rings. |
| **Object Storage** | MinIO (S3-compatible) | Audio records and evidence-package PDFs. |
| **LLM Router** | LiteLLM 1.52 | Provider abstraction (Groq, OpenAI, Anthropic, Gemini) with per-task model map + auto-failover. |
| **Multi-Agent State** | LangGraph 0.2 | Deep-check `StateGraph` fusing transcript semantics with acoustic features. |
| **Speech-to-Text** | Whisper (via Groq) | Transcription for the opt-in Deep-Check audio flow. |
| **Acoustic Analysis** | `librosa`, `soundfile`, `numpy` | Local feature extraction (MFCC, spectral flatness/centroid) for the AI-voice heuristic. |
| **PDF Engine** | WeasyPrint | HTML/CSS → court-friendly evidence PDFs. |
| **Observability** | structlog (JSON), Prometheus (`/metrics`), Flower | Structured logs, metrics, task monitoring. |
| **Android** | Kotlin, Jetpack Compose, Hilt, Room, Retrofit/OkHttp, Firebase (FCM) | Siege Sentinel + Guardian console, offline queue, push. |
| **Migrations** | Alembic | Versioned schema (`0001`→`0004`). |

**LLM providers by task** (`llm_router._provider_model`): default fallback picks `groq/llama-3.3-70b-versatile`, `gpt-4o-mini`, `claude-haiku-4-5`, or `gemini-1.5-flash` per configured provider order — so a single provider outage never takes the classifier down.

---

## 7. How It Works — Component Deep Dive

### A. WhatsApp Fraud Shield — `classifier.py`

The hybrid classification pipeline runs in a strict cost-ordered sequence:

1. **Voice → text.** If a voice note is forwarded, it is transcribed via Whisper before classification.
2. **Rules engine first.** `match_rules(text, language)` runs deterministic regex patterns loaded once at startup from YAML (`scripts/rules/*.yaml` — CBI digital arrest, courier/parcel, TRAI disconnect, verification deposit).
3. **Short-circuit.** If rule confidence `≥ HIGH_CONFIDENCE_THRESHOLD` (0.85), the pipeline returns a `scam` verdict **immediately** — skipping RAG and the LLM. This guarantees a `<1s` response and near-zero API cost for known scripts.
4. **RAG + LLM (only when rules are uncertain).** The top-5 similar scam scripts from the `pgvector` corpus become LLM context; the LLM returns a strict-JSON verdict `{verdict, confidence, red_flags, reasoning}`.
5. **Hybrid merge.** Rule flags and LLM flags are unioned; the verdict escalates to `scam`/`suspicious` if *either* source crosses its threshold. If the LLM is unavailable, the system **degrades gracefully** to a rules-only verdict at reduced confidence — it never hard-fails.
6. **Localized reply.** `_build_user_message` renders a Hindi or English WhatsApp reply with the red flags and the canonical safe next step: *"Hang up immediately. Real police/CBI never video-call. Call 1930."*

Example CBI rule (`cbi_digital_arrest.yaml`, severity `0.85`):
```yaml
- tag: cbi_digital_arrest
  language: any
  severity: 0.85
  patterns:
    - "\\b(CBI|ED|police|officer)\\b.*\\bdigital arrest\\b"
    - "\\byou are under arrest\\b"
    - "do not (move|leave|disconnect).*\\b(officer|CBI|police)\\b"
    - "money laundering.*your\\s+(name|account|number)"
```

### B. Siege Sentinel — the Risk Engine (`risk_engine.py` + `tasks.py`)

The risk engine is a **pure, side-effect-free state machine** — it takes signal events in and returns a risk delta and level; the Celery task persists the result. Weights live in `risk_weights.yaml` so false-positive tuning between demo runs requires **zero code changes**:

```yaml
unknown_or_international_number:                 0.15
call_duration_over_30min:                       0.10   # +0.05 per further 30min, capped 0.25
video_call_active:                              0.15
screen_share_start:                             0.25
banking_app_foreground_during_active_call:      0.20
first_time_payee_detected:                      0.20
```

**Graduated alert ladder** (the false-positive firewall — the single most important product-safety mechanism):

| Score | Level | Action |
| :--- | :--- | :--- |
| 0.00–0.30 | `graduated_1` | **Log only** — silent |
| 0.30–0.50 | `graduated_2` | **In-app nudge** on the elder's phone |
| 0.50–0.75 | `graduated_3` | **Guardian push** (WebSocket + FCM) |
| 0.75+ | `graduated_4` | **Full-screen interrupt** with one-tap 1930 & guardian call |

**Flow:** `POST /api/v1/signals/ingest` bulk-inserts device events and returns `200 OK` instantly. A Celery worker (`risk_engine.evaluate`) then loads events from the last 6 hours (or since the open incident started), recomputes the cumulative score **from scratch each run for idempotency**, opens/updates the `Incident` row, links contributing signals, and — only on transition *into* `graduated_3`/`graduated_4` — calls `dispatch_guardian_alert()`.

### C. Guardian Mesh & Alert Dispatch (`alert_dispatch.py`, `ws.py`)

When a threshold is crossed, `dispatch_guardian_alert`:
1. Loads up to **2 guardians** by `priority_order`.
2. Builds a **content-free context string** from recent signals — e.g. *"Mom has been on a video call with ****0001 for 160 minutes; screen sharing is active; just opened HDFC Bank."* Phone numbers are **masked to the last 4 digits** (`_mask_number`); raw payloads never leave the backend.
3. **Publishes to Redis pub/sub** (`alerts:{guardian_id}`) for any live WebSocket connection, **and** enqueues an **independent FCM push** via Celery — two channels, both fire, so a dropped socket never means a missed alert.
4. Inserts an `Alert` row and enforces **idempotency**: no duplicate unacknowledged alert within the `ALERT_COOLDOWN_MINUTES` window; re-notify only on level increase or after cooldown.

The guardian's app holds a resilient WebSocket (`GuardianWebSocketClient`) with exponential-backoff reconnect (1s → 30s) and responds to 30s `ping` heartbeats.

### D. Deep-Check Fusion — the LangGraph Chain (`deepcheck_chain.py`)

The **only** place LangGraph is used. A three-node `StateGraph` fuses transcript semantics with acoustic features — each node is independently unit-testable:

1. **`extract_signals`** — rules engine + LLM extract red-flag phrases and *quoted evidence spans* from the Whisper transcript.
2. **`spoof_fusion`** — a **transparent, rules-based** combination (not a black box): if the caller impersonates authority (`cbi|police|enforcement|customs|…`) **AND** `spoof_score > 0.5` → a `official_impersonation_with_synthetic_voice` compound flag (+0.2). Financial demand + voice anomaly → another compound flag (+0.15).
3. **`produce_verdict`** — synthesizes the final confidence and a natural-language summary including AI-voice likelihood.

> **Engineering note:** the verdict node is registered as `"produce_verdict"` (not `"verdict"`) to avoid a state-dictionary key collision with the `verdict` output field — a subtle but real LangGraph footgun.

The full pipeline (`tasks.run_deepcheck`): `status: pending → transcribing → analyzing → done`, transcribe (Groq Whisper) → extract acoustic features → run chain → persist transcript/flags/spoof score → append to the incident's evidence hash chain → sync to Neo4j.

### E. AI-Voice Heuristic — Honesty by Design (`spoof_detector.py`)

This is deliberately **a heuristic scorer, not a trained classifier** — and the code says so, loudly. `librosa` extracts three features (MFCC inter-frame variance, spectral flatness mean, spectral centroid variance); synthetic voices tend to show lower values on all three. A weighted, sigmoid-clamped combination yields a `spoof_score ∈ [0,1]`.

**Hard invariant:** every `SpoofResult` carries `assistive_only: true` and a disclaimer — *"this score is assistive only and cannot confirm or deny that the audio is AI-generated."* The code **fails open** to a neutral score rather than crashing the pipeline. Framing the AI-voice check as an *assistive signal, never a verdict* is a deliberate honesty stance that scores with technical judges.

### F. Tamper-Evident Evidence — `evidence_builder.py`

Every meaningful state change (incident opened, risk escalated, guardian alerted, deep-check completed, incident resolved) is appended to a **SHA-256 hash chain** stored as JSONB in PostgreSQL:

$$\text{Hash}_n = \text{SHA256}(\text{Hash}_{n-1} \,\Vert\, \text{CanonicalJSON}(\text{Event}_n))$$

Canonical JSON (sorted keys, no whitespace) makes hashing deterministic. `verify_hash_chain` returns `False` at the first broken link — so any post-hoc DB edit is immediately detectable. `generate_evidence_pdf` renders an HTML template (signal timeline, deep-check excerpt, hash-chain table, **Section 65B certificate block**) via WeasyPrint and stores it in MinIO with a 1-hour presigned download URL.

> **Legal integrity note (kept in the code):** the Section 65B template is boilerplate and **explicitly marked "not legally reviewed"** in both the code and the generated PDF — we present it as an evidentiary *scaffold*, not a finished legal instrument.

### G. Neo4j Fraud Graph (`graph_service.py`)

All incident linkages are pushed to Neo4j. The schema:
```
(:PhoneNumber)-[:INVOLVED_IN]->(:Incident)
(:PhoneNumber)-[:CALLED_FROM]->(:Device)
(:MuleAccount)-[:LINKED_TO]->(:PhoneNumber | :MuleAccount)   # ring edges
```
Every write uses `MERGE` (idempotent). `GET /api/v1/graph/ring/{phone}` runs a variable-length Cypher path match (default 3 hops, up to 6) and returns a clean force-directed shape:
```json
{ "nodes": [{ "id": "+919876540001", "label": "…", "group": "PhoneNumber" }],
  "edges": [{ "source": "…", "target": "…", "relationship": "LINKED_TO" }] }
```
This feeds directly into D3.js / React-Force-Graph — the "investigator view" where a demo scammer's number lights up inside a seeded ~40-node mule ring.

### H. Resilience & Security Cross-Cuts

- **LLM failover** (`llm_router.py`): walks a configurable provider list with retries; logs every call (`llm_call_log`) with tokens, latency, and cost estimate.
- **Rate limiting** (`rate_limit.py`): Redis token-bucket middleware on public webhook surfaces.
- **Auth** (`security.py`): JWT issuance/verification, `require_role` guards (elder / guardian / investigator), and device API-key hashing.
- **Health** (`/health`): real connection checks against Postgres, Redis, and Neo4j — not a static 200.
- **Graceful degradation everywhere:** missing FCM key → skip push (WebSocket still delivers); LLM down → rules-only verdict; spoof extraction fails → neutral score.

---

## 8. The Android Application

`kavach-android/` is a Kotlin / Jetpack Compose app in **Clean Architecture** (`data` → `domain` → `presentation`) with Hilt DI, a Room offline cache, and Retrofit/OkHttp networking.

**Key runtime pieces:**
- **`SignalMonitorService`** — a `START_STICKY` foreground service that flushes the local Room signal queue to `/api/v1/signals/ingest` every **30s** (`SIGNAL_BATCH_INTERVAL_MS`), batching up to 100 events. Device listeners (call state, accessibility events) write to the queue; the service only owns the upload loop. Runs under a persistent, silent "Kavach Monitoring" notification — **privacy is visible.**
- **`GuardianWebSocketClient`** — resilient WS to `/ws/guardian/{id}` with exponential-backoff reconnect and heartbeat handling.
- **`KavachFirebaseMessagingService`** — receives FCM guardian pushes even when the app is backgrounded.
- **Interceptors** — `AuthInterceptor` (Bearer JWT), `DeviceKeyInterceptor` (device API key), `TokenExpiryInterceptor` (refresh/logout on 401).

**Screens** (`Screen.kt`): Auth · PairGuardian · ElderPairing · Dashboard · Incidents · IncidentDetail · Alerts · DeepCheck · Graph · Plans · Settings · Notifications · About · Help · Feedback.

The app is written with **Play-policy-legal permissions only** and explicit per-signal consent screens — the demo video shows them, because *privacy is a feature, not a compliance chore.* Unit tests cover ViewModels, use-cases, and repositories.

---

## 9. Data Model & API Surface

**PostgreSQL entities** (`app/models/`, UUID PKs, `created_at`/`deleted_at` mixins): `user`, `family`, `elder`, `guardian`, `device`, `signal_event`, `incident`, `incident_signal`, `deepcheck_session`, `evidence_package`, `alert`, `consent_event`, `scam_corpus` (pgvector), `whatsapp_verdict`, `graph_sync_log`, `subscription`, `llm_call_log`. Schema is versioned with Alembic (`0001_initial` → `0004_billing_webhook_log`).

**HTTP / WS API surface:**

| Method | Path | Purpose |
| :--- | :--- | :--- |
| `GET` | `/health`, `/api/v1/health` | Real PG/Redis/Neo4j connection check |
| `GET`/`POST` | `/api/v1/whatsapp` | Meta verify (GET) + webhook receiver (POST) |
| `POST` | `/api/v1/signals/ingest` | Bulk signal ingest → async risk eval |
| `POST` | `/api/v1/guardians/pair` | Pair guardian via short-lived Redis code → JWT |
| `POST` | `/api/v1/guardians/generate-pairing-code` | Elder issues a 6-digit pairing code |
| `POST` | `/api/v1/incidents/{id}/resolve` | Resolve / mark false-positive (guardian) |
| `POST` | `/api/v1/incidents/{id}/evidence` | Generate evidence PDF |
| `POST` | `/api/v1/deepcheck/sessions` | Upload audio (multipart) → `202` + session_id |
| `GET` | `/api/v1/deepcheck/sessions/{id}` | Poll deep-check status/result |
| `GET` | `/api/v1/graph/ring/{phone}` | Mule-ring subgraph (investigator) |
| `GET` | `/api/v1/billing/plans` | Plan metadata |
| `POST` | `/api/v1/billing/webhook/{provider}` | Billing webhook stub |
| `WS` | `/ws/guardian/{guardian_id}?token=JWT` | Real-time guardian alerts |
| `GET` | `/metrics` | Prometheus metrics |

**Webhook parsing:** Meta signatures are verified via `X-Hub-Signature-256` (HMAC-SHA256 with `META_WEBHOOK_SECRET`); Twilio sends `application/x-www-form-urlencoded`, parsed via `request.form()` (`From`, `Body`).

---

## 10. Privacy, Consent & Legal Design

Privacy is a **pitch weapon**, not an afterthought — especially post-Sanchar-Saathi-controversy:

- **Audio-free by default.** No signal layer touches call audio. The only audio path is the **opt-in, one-tap, user-triggered** Deep-Check — a legal *participant* recording, Truecaller-style.
- **Consent-first.** Every device signal has an explicit consent screen; consent events are persisted (`consent_event`). A **minimal-permission mode** (call metadata + WhatsApp bot only) still delivers value.
- **Guardians see signals, never content.** Context strings are built from event *types*; phone numbers are masked to the last 4 digits; raw payloads never leave the backend.
- **DPDP-aligned.** Every permission's purpose is documented; Accessibility is never used for anything call-related.
- **Tamper-evident evidence** with an honest, clearly-unreviewed Section-65B scaffold — positioned as risk-*reduction*, never a guarantee.

---

## 11. The Live Demo Flow

**The demo *is* the UX** — visceral and instantly understood by any judge:

1. **Phone A** (the "elder") plays a scripted digital-arrest scenario. Behavioral signals stream to the backend; the risk score climbs through the graduated ladder (`log → nudge → guardian → interrupt`).
2. **Phone B** (the "son") buzzes mid-scenario with a **Guardian Alert**: *"Mom has been on a video call with ****0001 for 2h 40m and just opened her bank app."* One tap dials the elder — **the scam collapses.** (Detection ends in a *blocked outcome*, not a red banner.)
3. **A WhatsApp forward** of a real phishing SMS returns a **Hindi verdict with red flags in seconds.**
4. At high risk, Phone A shows the **full-screen interrupt** quoting *"There is no such thing as digital arrest"* with one-tap **1930** and guardian call.
5. The **investigator dashboard** lights up the demo scammer's number inside a seeded **~40-node mule ring** (Neo4j → force-graph).
6. The incident **auto-generates a hash-stamped evidence PDF**.

Every segment is pre-recorded as a contingency; the live path runs on two physical phones.

---

## 12. Business Model & Go-To-Market

**Who pays — and why it isn't the victim:**

| Revenue line | Payer | Mechanics |
| :--- | :--- | :--- |
| **Family Plan (B2C wedge)** | Adult children (25–45, urban) | ₹99–199/mo protects up to 4 elder profiles. The payer≠user split is the unlock: motivated, app-literate buyer; zero-friction protected user. |
| **Bank / insurer white-label (core B2B2C)** | Banks, card issuers, insurers | Banks carry fraud liability & RBI pressure. *"Senior Shield"* bundled at ₹5–15 per protected user/month. **This is where the real revenue lives.** |
| **Telco bundle** | Jio / Airtel / Vi | Value-added safety pack; telcos have AI spam layers but lack the family/guardian construct. |
| **Intelligence & evidence (B2G)** | I4C, state cyber cells, CERT-In | Anonymized scam-script signatures, ring graphs, structured evidence feeding Chakshu/1930/CFMC. Grant/contract revenue + credibility. |

**GTM ladder:** Ship the free WhatsApp bot publicly now (every forwarded message = training data + PR) → 1,000-family pilot via RWAs/senior associations in 2 metros → publish a prevention case study (*"X sieges interrupted, ₹Y prevented"*) → bank white-label pilot + insurer rider + I4C data partnership.

**Financial sketch (honest, order-of-magnitude):** signals-only architecture keeps per-user cost near zero (no streaming STT); LLM verdicts ~₹0.5–2 each; audio deep-checks a few ₹, used rarely. *Illustrative Y2:* 50k families × ₹120/mo ≈ **₹7.2cr ARR** + one bank deal (500k users × ₹6/mo) ≈ **₹3.6cr ARR** — credible seed-stage traction, deliberately not an inflated TAM.

**The honest impact metric is prevented-loss, not revenue.** Even a 0.5% national dent = **₹100+ crore/year protected.**

---

## 13. Competitive Analysis

| Competitor | Their strength | Kavach's defensible edge |
| :--- | :--- | :--- |
| **Google Scam Detection** | Free, native, on-device Gemini Nano, Hindi, mid-call alerts, OEM distribution. | **Single-device & privacy-boxed by design:** cannot see WhatsApp streams, cannot model multi-hour cross-app sieges, and — deliberately — **cannot alert a family member.** Kavach begins where their privacy sandbox ends. |
| **Truecaller** | 400M installs, caller-ID network, AI Call Scanner, Family Protection. | Family features manage *numbers/blocks* — no siege modeling, no guardian-intervention flow, no evidence/1930 pipeline. (Also a plausible acquisition path.) |
| **Sanchar Saathi / Chakshu / 1930** | State distribution, preinstall mandate, CFMC coordination. | Reporting infrastructure, **not real-time protection** — and trust-impaired by the surveillance controversy. Kavach **integrates** with it (evidence, one-tap reports) rather than fights it. |
| **ScamMukt / Quick Heal AntiFraud** | Shipping consumer apps; ₹50/mo anchor. | Single-device detect-and-warn; neither models the siege nor breaks isolation. They validate *willingness-to-pay* for exactly the wedge Kavach takes past them. |

**The judge's inevitable question — "why won't Google kill you?"** — answered without flinching: Google's privacy model is single-device *by design* (that's the point of on-device); it will not white-label to an Indian bank's fraud stack. The B2C plan is the **wedge**, never the thesis — the company lives on **banks/insurers + evidence/1930 integration + regional-language depth.**

---

## 14. Risks & Honest Limitations

| Risk | Severity | Mitigation |
| :--- | :--- | :--- |
| **False positives** | Product-existential — one wrong 2 AM *"your father is being scammed"* alert kills trust. | Graduated alert ladder (log → nudge → guardian → interrupt), human-confirm loops, YAML-tunable per-family sensitivity, FP rate reported as a first-class metric. |
| **Commoditization** (12–24 mo) | Highest strategic. | Move to bank/insurer rails fast; own evidence/1930 integration and regional-language depth. If Google ships *"notify a trusted contact,"* the B2C wedge halves — which is *why B2C is the wedge, not the thesis.* |
| **Permission friction / Play policy drift** | Medium. | Minimal-permission mode still delivers value; document every permission (DPDP-aligned); never touch Accessibility for call-related logic. |
| **Adversarial adaptation** | Medium. | Signals fire from OS-level metadata the scammer cannot script away without abandoning the long-siege format that makes them money. The guardian mesh is the resilience layer. |
| **Trust & liability** | Medium. | Position as risk-*reduction*, never guarantee; log everything; DPDP-grade consent artifacts from day one. |
| **AI-voice overclaim** | Reputational. | Explicitly framed `assistive_only` — never a verdict; disclaimer surfaced in API, UI, and PDF. |

---

## 15. Why Kavach Wins on the Rubric

| Criterion | Weight | How Kavach scores |
| :--- | :--- | :--- |
| **Innovation** | 25% | Audio-free siege detection + guardian-mesh intervention is a **genuinely novel mechanism** — not a better classifier, a different *unit of protection*. Judges have seen scam classifiers; they have not seen *isolation-breaking as a product*. |
| **Business Impact** | 25% | ₹3,000cr/yr losses; elderly-victim stories every judge knows; **prevention demonstrated, not just detection**; B2B2C model with banks/insurers who carry fraud liability. |
| **Technical Excellence** | 20% | Multi-signal risk engine + rules/LLM hybrid with explainable red flags + Whisper/acoustic deep-check + Neo4j graph + tamper-evident evidence pipeline. Real metrics: precision/recall on a scripted corpus, alert latency, deliberate false-positive design. |
| **Scalability** | 15% | Signals-only architecture is cheap (no per-call cloud STT); WhatsApp channel scales nationally with no installs; clear rollout ladder: families → banks → telcos → I4C. |
| **User Experience** | 15% | The demo *is* the UX: two-phone guardian intervention mid-video, Hindi WhatsApp verdicts in seconds, a full-screen interrupt quoting the PM's *Mann Ki Baat* line. Visceral and instantly understood. |

---

## 16. Roadmap

**Shipped (this build):** WhatsApp Fraud Shield (Hindi/English) · Siege Sentinel risk engine + graduated ladder · Guardian Mesh (WebSocket + FCM) · LangGraph Deep-Check + AI-voice heuristic · Neo4j mule-ring graph · tamper-evident evidence PDFs · full Android app (Compose, clean arch) · Docker infra + Alembic migrations + test suites.

**Add-ons (priority order):**
1. **UPI-moment friction** — detect payment-app foreground during an active siege → overlay *"Pause: are you being told to pay to 'verify'?"*
2. **Active video-call challenge card** — *"Ask the officer to wave their hand across their face"* (deepfake active-probe, framed assistive) — a planned 4th LangGraph node.
3. **IVR access path** for feature phones.
4. **iOS** — Guardian console + WhatsApp bot first (iOS signal access is narrower — stated plainly).

---

## 17. Team

**Team Believers**

| Member |
| :--- |
| **Devansh Srivastava** |
| **Shaurya Singh** |

> *"We never hear the call — and that's exactly why this works."*

---

*Setup, run, and developer-handover instructions live in [`README.md`](./README.md) and [`kavach-backend/README-BACKEND.md`](./kavach-backend/README-BACKEND.md).*
