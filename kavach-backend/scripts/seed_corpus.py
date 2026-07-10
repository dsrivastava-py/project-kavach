"""
Seed scam corpus with real + synthetic examples.
Run: docker-compose exec api python -m scripts.seed_corpus

Sources cited inline — do not invent phrasing, use reported scam scripts.
"""
import asyncio
import sys
from pathlib import Path

# Add parent to path so `app` is importable
sys.path.insert(0, str(Path(__file__).parent.parent))

from app.core.config import get_settings
from app.core.db import SessionLocal
from app.services.rag import embed_and_store

# ---------------------------------------------------------------------------
# Seed data — real phishing scripts from public sources
# Sources:
#   - MHA Cybercrime Advisory 2024 (cybercrime.gov.in)
#   - PIB Press Release: Digital Arrest scam warnings
#   - Mint/ET reporting on TRAI/courier scams (cited per entry)
# ---------------------------------------------------------------------------

SEED_CORPUS = [
    # --- TRAI Disconnect scams ---
    {
        "source": "advisory",
        "tags": ["trai_disconnect", "urgency_pressure"],
        "text": (
            "This is a recorded message from TRAI. Your mobile number +91XXXXXXXXXX has been "
            "flagged for illegal activity and will be disconnected within 2 hours. "
            "Press 1 to speak to our officer immediately. Press 9 to avoid disconnection."
        ),
    },
    {
        "source": "advisory",
        "tags": ["trai_disconnect"],
        "text": (
            "Dear customer, your SIM card linked to Aadhaar XXXX-XXXX-XXXX is being used "
            "for spam and illegal calls. TRAI has issued a disconnection notice. "
            "Call our helpline 1234567890 within 30 minutes to avoid deactivation."
        ),
    },
    {
        "source": "press",
        "tags": ["trai_disconnect", "verification_deposit"],
        "text": (
            "TRAI Notice: Your telecom account has been flagged for suspicious outgoing calls. "
            "To prevent service suspension, verify your identity by paying a ₹999 verification fee. "
            "This is fully refundable once verification is complete."
        ),
    },
    # --- Digital Arrest / CBI ---
    {
        "source": "advisory",
        "tags": ["cbi_digital_arrest", "urgency_pressure"],
        "text": (
            "I am officer Rajesh Kumar from CBI Mumbai. Your Aadhaar number is linked to "
            "47 criminal cases including money laundering and drug trafficking. "
            "You are under digital arrest. Do not move or contact anyone. "
            "Stay on this call while we verify your account."
        ),
    },
    {
        "source": "advisory",
        "tags": ["cbi_digital_arrest", "verification_deposit"],
        "text": (
            "This is ED (Enforcement Directorate). A warrant has been issued against your PAN card. "
            "You are involved in a ₹14 crore money laundering case. "
            "To avoid physical arrest, transfer ₹50,000 to our secure government account immediately. "
            "This will be refunded within 24 hours after clearance."
        ),
    },
    {
        "source": "press",
        "tags": ["cbi_digital_arrest"],
        "text": (
            "Video call from 'CBI officer': Your account has been used for illegal transactions. "
            "We are recording this call as evidence. Do not disconnect. You are under digital arrest. "
            "Your family will also be arrested if you don't cooperate. Pay bail amount now."
        ),
    },
    {
        "source": "advisory",
        "tags": ["cbi_digital_arrest", "urgency_pressure"],
        "text": (
            "Supreme Court notice: Case filed against you for cybercrime. "
            "Report to nearest police station OR pay ₹25,000 online to settle the case. "
            "Warrant will be executed in 2 hours if no response."
        ),
    },
    # --- Courier / Parcel ---
    {
        "source": "press",
        "tags": ["courier_parcel_scam", "cbi_digital_arrest"],
        "text": (
            "FedEx notification: A parcel in your name has been seized at Mumbai airport. "
            "Contents include 5 passports, 200g of drugs, and illegal documents. "
            "Your Aadhaar is linked to this shipment. A case has been registered. "
            "Press 1 to speak to the CBI officer handling your case."
        ),
    },
    {
        "source": "advisory",
        "tags": ["courier_parcel_scam"],
        "text": (
            "DHL Express: Your package from China containing prohibited items was intercepted by customs. "
            "Your name and phone number are on the package. Please call +91-XXXXXXXXXX "
            "within 1 hour to avoid arrest under NDPS Act."
        ),
    },
    # --- Verification / Safe Account Transfer ---
    {
        "source": "advisory",
        "tags": ["verification_deposit", "urgency_pressure"],
        "text": (
            "RBI alert: Your bank account is under investigation for suspicious transactions. "
            "To protect your funds, immediately transfer all balance to this RBI-verified safe account: "
            "ACC NO: XXXXXXXXXX IFSC: XXXXX. Account will be restored in 24 hours."
        ),
    },
    {
        "source": "advisory",
        "tags": ["verification_deposit"],
        "text": (
            "Your SBI account has been compromised. To secure your money, transfer to temporary "
            "government-secured account. Our officer will guide you through the process. "
            "Do not tell your family — this is confidential investigation protocol."
        ),
    },
    # --- Synthetic variants (same patterns, different phrasing) ---
    {
        "source": "synthetic",
        "tags": ["trai_disconnect"],
        "text": "Alert from Department of Telecom: Number flagged. Disconnection in 1 hour. Verify at once.",
    },
    {
        "source": "synthetic",
        "tags": ["cbi_digital_arrest", "urgency_pressure"],
        "text": (
            "This call is being recorded by NCB. Your Aadhaar linked to drug racket. "
            "Digital arrest issued. Pay ₹1 lakh to avoid physical arrest. Time limit: 30 minutes."
        ),
    },
    {
        "source": "synthetic",
        "tags": ["courier_parcel_scam", "cbi_digital_arrest"],
        "text": (
            "Customs officer speaking. Parcel with your address seized. Contains illegal cash and drugs. "
            "You have 2 hours to report or face arrest under IPC 302."
        ),
    },
    {
        "source": "synthetic",
        "tags": ["verification_deposit"],
        "text": "To verify identity and avoid account block, pay ₹500 refundable fee via UPI: kavach@scam",
    },
    {
        "source": "synthetic",
        "tags": ["trai_disconnect", "urgency_pressure"],
        "text": "Final notice: Your number will be blocked by TRAI in 15 minutes. Call 9XXXXXXXX to stay connected.",
    },
    {
        "source": "synthetic",
        "tags": ["cbi_digital_arrest"],
        "text": (
            "I am IPS Officer speaking from Delhi HQ. Money laundering case registered under your PAN. "
            "Cooperate now or face arrest. Do not leave your location."
        ),
    },
    {
        "source": "synthetic",
        "tags": ["courier_parcel_scam"],
        "text": "BlueDart courier: Your consignment seized. Aadhaar-linked illegal items found. Contact us urgently.",
    },
    {
        "source": "synthetic",
        "tags": ["verification_deposit", "urgency_pressure"],
        "text": "Account frozen due to suspicious login. Pay ₹299 to unlock. Offer expires in 30 minutes.",
    },
    {
        "source": "synthetic",
        "tags": ["cbi_digital_arrest", "verification_deposit"],
        "text": (
            "ED notice under PMLA. Your assets will be attached unless you transfer ₹2 lakh to "
            "escrow account. This secures your innocence while investigation continues."
        ),
    },
    # --- Hindi variants ---
    {
        "source": "advisory",
        "tags": ["trai_disconnect"],
        "text": (
            "TRAI की ओर से सूचना: आपका मोबाइल नंबर 2 घंटे में बंद किया जाएगा। "
            "कृपया अभी 9 दबाएं और अधिकारी से बात करें।"
        ),
    },
    {
        "source": "advisory",
        "tags": ["cbi_digital_arrest"],
        "text": (
            "CBI अधिकारी बोल रहे हैं। आपके आधार से 47 आपराधिक मामले जुड़े हैं। "
            "आप digital arrest में हैं। तुरंत ₹50,000 ट्रांसफर करें।"
        ),
    },
    # --- Safe examples (for precision/recall held-out set) ---
    {
        "source": "synthetic",
        "tags": ["safe"],
        "text": "Your order #12345 from Flipkart has been shipped and will arrive by Thursday.",
    },
    {
        "source": "synthetic",
        "tags": ["safe"],
        "text": "Reminder: Your appointment with Dr. Sharma is tomorrow at 11am. Reply YES to confirm.",
    },
]


async def main() -> None:
    s = get_settings()
    if not s.OPENAI_API_KEY and not s.GROQ_API_KEY:
        print("ERROR: OPENAI_API_KEY or GROQ_API_KEY required for embeddings. Set in .env.")
        sys.exit(1)

    async with SessionLocal() as session:
        for i, item in enumerate(SEED_CORPUS, 1):
            if "safe" in item["tags"]:
                print(f"[{i}/{len(SEED_CORPUS)}] Skipping safe example (no embedding needed for rules-only safe class)")
                continue
            print(f"[{i}/{len(SEED_CORPUS)}] Embedding: {item['text'][:60]}...")
            await embed_and_store(
                script_text=item["text"],
                source=item["source"],
                red_flag_tags=item["tags"],
                session=session,
            )
    print(f"Done. {len([x for x in SEED_CORPUS if 'safe' not in x['tags']])} scam corpus entries stored.")


if __name__ == "__main__":
    asyncio.run(main())
