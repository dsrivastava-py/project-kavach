"""
Rules engine — deterministic, YAML-driven, no LLM.
Loads rules/*.yaml once at startup (or on explicit reload call).
Each rule file schema:
  tag: str
  language: str          # 'en', 'hi', 'any', etc.
  patterns: list[str]    # regex patterns
  severity: float        # 0.0–1.0
"""
import functools
import re
from dataclasses import dataclass, field
from pathlib import Path

import yaml

_RULES_DIR = Path(__file__).parent.parent.parent / "scripts" / "rules"


@dataclass
class RuleMatchResult:
    matched_flags: list[str] = field(default_factory=list)
    rule_confidence: float = 0.0


@functools.lru_cache(maxsize=1)
def _load_rules() -> list[dict]:
    rules = []
    for path in sorted(_RULES_DIR.glob("*.yaml")):
        with path.open(encoding="utf-8") as f:
            data = yaml.safe_load(f)
            if isinstance(data, list):
                rules.extend(data)
            else:
                rules.append(data)
    return rules


def reload_rules() -> None:
    """Explicit cache invalidation — admin endpoint can call this."""
    _load_rules.cache_clear()


def match_rules(text: str, language: str = "en") -> RuleMatchResult:
    if not text:
        return RuleMatchResult()

    rules = _load_rules()
    matched: list[str] = []
    total_severity = 0.0

    for rule in rules:
        rule_lang = rule.get("language", "any")
        if rule_lang not in ("any", language):
            continue
        for pattern in rule.get("patterns", []):
            try:
                if re.search(pattern, text, re.IGNORECASE):
                    tag = rule.get("tag", pattern[:40])
                    if tag not in matched:
                        matched.append(tag)
                        total_severity += float(rule.get("severity", 0.5))
                    break  # one match per rule is enough
            except re.error:
                pass

    confidence = min(total_severity, 1.0)
    return RuleMatchResult(matched_flags=matched, rule_confidence=confidence)
