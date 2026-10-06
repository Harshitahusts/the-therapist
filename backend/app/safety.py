"""Risk detection that runs on every user utterance, before the model's next turn.

Deterministic rules come first so behaviour is predictable and testable; the
optional moderation signal can only raise the level, never lower it. Nothing
here stores what the user said.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from enum import IntEnum

from .ai import ModerationResult


class RiskLevel(IntEnum):
    LOW = 0
    MODERATE = 1
    HIGH = 2
    IMMEDIATE = 3


@dataclass
class SafetyAssessment:
    level: RiskLevel
    categories: list[str] = field(default_factory=list)

    @property
    def show_resources(self) -> bool:
        return self.level >= RiskLevel.HIGH


def _c(p: str) -> re.Pattern:
    return re.compile(p)


# Figures of speech that contain alarming words but almost never signal risk.
# They are removed before matching.
_HYPERBOLE = [
    _c(r"\bkill(ing|ed)? it\b"),
    _c(r"\bkill(ing)? (some |the )?time\b"),
    _c(r"\bkill (the|this) (process|app|task|lights?|music|engine|bug)\b"),
    _c(r"\bdying (to|for)\b"),
    _c(r"\b(i )?(could|would) kill for\b"),
    _c(r"\b(is|are|it's|was|were) killing me\b"),
    _c(r"\b(dead|half dead) (tired|on my feet)\b"),
    _c(r"\b(died|dying|die) (of )?laugh(ing|ter)?\b"),
    _c(r"\b(bored|scared|worried) to death\b"),
    _c(r"\bkiller (app|workout|feature|deal|idea)\b"),
    _c(r"\bsuicide squad\b"),
    _c(r"\bi'?d rather die than\b"),
    _c(r"\bi am dead (lol|haha|lmao)\b"),
    _c(r"\bthat'?s (so )?(dead|deadly)\b"),
]

# Explicit denials are removed so "I'm not suicidal, just tired" stays LOW.
_NEGATED = [
    _c(r"\b(i am not|i'm not|im not|not that i am|i would never|i'd never|i will never|i never want to|i don't want to)"
       r" (going to |gonna )?(suicidal|kill myself|hurt myself|harm myself|end my life)\b"),
    _c(r"\bno(t)? (thoughts|thinking) (of|about) (suicide|killing myself|hurting myself)\b"),
]

_EDUCATIONAL = _c(r"\bsuicide (prevention|awareness|rates?|statistics|research)\b")

_PAST = _c(
    r"\b(used to|years ago|months ago|back when|when i was (younger|a kid|a teen|a teenager|in school|in college)"
    r"|in the past|a long time ago|i was suicidal|i had been|once tried|in high school)\b"
)

_THIRD_PARTY_SUBJECT = _c(
    r"\b(my|a|our) (friend|best friend|brother|sister|mom|mum|dad|mother|father|son|daughter|partner|"
    r"boyfriend|girlfriend|husband|wife|colleague|coworker|cousin|roommate|flatmate|classmate)\b"
    r"|\b(he|she|they) (is|are|was|were|has|have|said|says|keeps?|wants?|tried)\b"
)
_THIRD_PARTY_SELF_HARM = _c(
    r"\b(kill|hurt|harm|cut) (himself|herself|themselves|themself)\b"
    r"|\b(he|she|they)( is| are|'s|'re)? (suicidal|going to end (it|his life|her life|their life))\b"
    r"|\b(friend|brother|sister|mom|mum|dad|mother|father|son|daughter|partner|boyfriend|girlfriend|husband|wife|"
    r"colleague|coworker|cousin|roommate)\b.{0,40}\b(suicidal|wants to die|tried to kill)\b"
)
_FIRST_PERSON_SUICIDAL = _c(r"\b(i am|i feel|i've been|i have been|i'm feeling|feeling) (so |really |very )?suicidal\b")

_IMMINENCE = r"(now|right now|tonight|today|this evening|in a (few|couple of) (minutes|hours)|soon)"
_SELF_ACT = r"(kill myself|end (it|it all|my life)|take my (own )?life|jump|overdose|do it)"

_RULES: list[tuple[RiskLevel, str, re.Pattern]] = [
    # --- IMMEDIATE ---------------------------------------------------------
    (RiskLevel.IMMEDIATE, "self_harm_imminent",
     _c(rf"\b(going to|about to|gonna|will|planning to|ready to)\s+{_SELF_ACT}\b.{{0,40}}\b{_IMMINENCE}\b")),
    (RiskLevel.IMMEDIATE, "self_harm_imminent",
     _c(rf"\b{_IMMINENCE}\b.{{0,30}}\b(i am|i'm|i will|i'll)\s+(going to |gonna )?{_SELF_ACT}\b")),
    (RiskLevel.IMMEDIATE, "overdose",
     _c(r"\b(took|taken|swallowed|just took|have taken|i've taken)\s+(a bunch of|all (of )?my|too many|loads of|"
        r"a lot of|an overdose|a whole (bottle|pack|packet) of|the whole (bottle|pack|packet) of)\s*\w*\s*"
        r"(pills|tablets|medication|meds|paracetamol|sleeping pills)?\b")),
    (RiskLevel.IMMEDIATE, "self_harm_means",
     _c(r"\b(i have|i've got|i am holding|i'm holding|holding|i bought)\s+(a|the|some)\s+(gun|knife|rope|blade|razor|pills)\b"
        rf".{{0,60}}\b{_SELF_ACT}\b")),
    (RiskLevel.IMMEDIATE, "self_harm_imminent",
     _c(r"\b(standing|sitting|i am|i'm) on (the|a) (edge|ledge|bridge|roof|railway|tracks)\b")),
    (RiskLevel.IMMEDIATE, "medical_emergency",
     _c(r"\b(won't|wont|can't|cannot) stop (the )?bleeding\b")),
    (RiskLevel.IMMEDIATE, "danger_from_others",
     _c(r"\b(he|she|they|someone|somebody)('s| is| are)? (here|outside|at the door|breaking in|trying to (get|break) in)\b"
        r".{0,60}\b(hurt|kill|gun|knife|weapon|scared)\b")),
    # --- HIGH ----------------------------------------------------------------
    (RiskLevel.HIGH, "suicidal_ideation",
     _c(r"\b(want|wanna|wish|plan|planning|thinking about|thought about|think about|considering|going)( to)? "
        r"(kill(ing)? myself|end(ing)? my (own )?life|tak(e|ing) my (own )?life|commit(ting)? suicide)\b")),
    (RiskLevel.HIGH, "suicidal_ideation", _c(r"\bkill myself\b|\bkms\b")),
    (RiskLevel.HIGH, "suicidal_ideation", _c(r"\bsuicid(e|al)\b")),
    (RiskLevel.HIGH, "suicidal_ideation",
     _c(r"\b(want to|wish i could|wish to|would like to|ready to) (just )?die\b|\bi want to be dead\b|\bi wish i (was|were) dead\b")),
    (RiskLevel.HIGH, "suicidal_ideation", _c(r"\bend it all\b|\bend my life\b")),
    (RiskLevel.HIGH, "suicidal_ideation",
     _c(r"\b(don't|do not|dont) want to (live|be alive|exist)( anymore| any more)?\b")),
    (RiskLevel.HIGH, "suicidal_ideation", _c(r"\bbetter off (dead|without me)\b")),
    (RiskLevel.HIGH, "suicidal_ideation", _c(r"\bno (reason|point) (to|in) (live|living|being alive|go on|going on)\b")),
    (RiskLevel.HIGH, "self_harm",
     _c(r"\b(hurt|harm|cut|cutting|burn|burning|hurting|harming) myself\b|\bself[- ]?harm(ing)?\b")),
    (RiskLevel.HIGH, "harm_to_others",
     _c(r"\b(going to|want to|gonna|planning to|plan to|will)\s+(kill|hurt|stab|shoot|attack)\s+"
        r"(him|her|them|someone|somebody|people|everyone|my \w+)\b.{0,40}\b(gun|knife|weapon|plan|tonight|tomorrow|for real|seriously)\b")),
    (RiskLevel.HIGH, "harm_to_others", _c(r"\bshoot up (the|my|a)\b")),
    (RiskLevel.HIGH, "abuse",
     _c(r"\b(he|she|they|my (husband|wife|partner|boyfriend|girlfriend|dad|father|mom|mum|mother|parents?|"
        r"stepdad|stepmom|brother|sister|boss|uncle))\s+(hits|hit|beats|beat|chokes|choked|strangled|hurts|hurt|"
        r"abuses|abused|threatens|threatened to (kill|hurt))\s+me\b")),
    (RiskLevel.HIGH, "abuse", _c(r"\b(being|been|was|got) (abused|assaulted|raped|sexually assaulted)\b")),
    (RiskLevel.HIGH, "danger", _c(r"\b(i am|i'm|i feel) (not safe|unsafe|in danger|scared for my life)\b")),
    # --- MODERATE ------------------------------------------------------------
    (RiskLevel.MODERATE, "hopelessness",
     _c(r"\b(can't|cannot|cant) (go on|do this anymore|take (it|this) anymore|keep going)\b")),
    (RiskLevel.MODERATE, "hopelessness",
     _c(r"\bhopeless\b|\bno hope\b|\bwhat'?s the point (of|in) (anything|it all|living|life|trying)\b")),
    (RiskLevel.MODERATE, "passive_ideation",
     _c(r"\bwish i (could |would |can )?(just )?(disappear|wasn'?t here|weren'?t here|was never born|could sleep forever|didn'?t wake up|"
        r"wouldn'?t wake up)\b|\b(don't|dont) want to wake up\b|\bdisappear forever\b")),
    (RiskLevel.MODERATE, "passive_ideation",
     _c(r"\b(nobody|no one|no-one) would (even |really |ever )?(miss|care|notice)\b|\beveryone would be better off\b"
        r"|\bif i (was|were) gone\b")),
    (RiskLevel.MODERATE, "hopelessness", _c(r"\b(i am|i'm|i feel like) (such )?a burden\b|\bi feel trapped\b")),
    (RiskLevel.MODERATE, "harm_to_others",
     _c(r"\b(want to|wanna|going to|gonna)\s+(hurt|attack|stab|shoot)\s+(him|her|them|someone|somebody|people)\b")),
    (RiskLevel.MODERATE, "panic", _c(r"\bpanic attack\b|\b(i )?(can't|cannot) breathe\b")),
]


def normalize(text: str) -> str:
    t = text.lower()
    t = t.replace("’", "'").replace("‘", "'")
    t = re.sub(r"\bi'm\b", "i am", t)
    t = re.sub(r"\bim\b", "i am", t)
    t = re.sub(r"\bgonna\b", "going to", t)
    t = re.sub(r"\bwanna\b", "want to", t)
    t = re.sub(r"\s+", " ", t).strip()
    return t


def _strip(patterns: list[re.Pattern], text: str) -> str:
    for p in patterns:
        text = p.sub(" ", text)
    return text


def classify(text: str, moderation: ModerationResult | None = None) -> SafetyAssessment:
    t = normalize(text)
    t = _strip(_HYPERBOLE, t)
    t = _strip(_NEGATED, t)

    level = RiskLevel.LOW
    categories: list[str] = []

    if _EDUCATIONAL.search(t) and not _FIRST_PERSON_SUICIDAL.search(t):
        t = _EDUCATIONAL.sub(" ", t)
        categories.append("topic_mention")

    third_party = bool(_THIRD_PARTY_SELF_HARM.search(t)) and not _FIRST_PERSON_SUICIDAL.search(t)
    if third_party:
        categories.append("third_party_concern")
        level = RiskLevel.MODERATE
        t = _THIRD_PARTY_SELF_HARM.sub(" ", t)
        # "suicidal" about someone else should not then match the first-person rule.
        if _THIRD_PARTY_SUBJECT.search(t):
            t = re.sub(r"\bsuicid(e|al)\b", " ", t)

    for rule_level, category, pattern in _RULES:
        if pattern.search(t):
            if category not in categories:
                categories.append(category)
            level = max(level, rule_level)

    # Talking about the past is still worth a gentle check-in, but not a crisis
    # response. Imminent danger is never downgraded.
    if level == RiskLevel.HIGH and _PAST.search(t) and "abuse" not in categories:
        level = RiskLevel.MODERATE
        categories.append("past_experience")

    if moderation is not None:
        cats = moderation.categories
        if cats.get("self-harm/intent") or cats.get("self-harm/instructions"):
            if level < RiskLevel.HIGH:
                level = RiskLevel.HIGH
                categories.append("moderation_self_harm_intent")
        elif cats.get("self-harm") and level < RiskLevel.MODERATE:
            level = RiskLevel.MODERATE
            categories.append("moderation_self_harm")

    return SafetyAssessment(level=level, categories=categories)


# ---------------------------------------------------------------------------
# Crisis resources. Keep these few and verified; the app always also says
# "contact your local emergency number".
# ---------------------------------------------------------------------------

CRISIS_RESOURCES: dict[str, dict] = {
    "US": {"emergency": "911", "lines": [{"name": "988 Suicide & Crisis Lifeline", "phone": "988", "text": "988"}]},
    "CA": {"emergency": "911", "lines": [{"name": "9-8-8 Suicide Crisis Helpline", "phone": "988", "text": "988"}]},
    "GB": {"emergency": "999", "lines": [{"name": "Samaritans", "phone": "116 123"},
                                          {"name": "Shout", "text": "85258"}]},
    "IE": {"emergency": "112", "lines": [{"name": "Samaritans", "phone": "116 123"},
                                          {"name": "Text About It", "text": "50808"}]},
    "IN": {"emergency": "112", "lines": [{"name": "Tele-MANAS", "phone": "14416"}]},
    "AU": {"emergency": "000", "lines": [{"name": "Lifeline", "phone": "13 11 14"}]},
    "NZ": {"emergency": "111", "lines": [{"name": "Need to talk? 1737", "phone": "1737", "text": "1737"}]},
    "INTL": {"emergency": None, "lines": [], "directory": "https://findahelpline.com"},
}


def resources_for(region: str | None) -> dict:
    key = (region or "INTL").upper()
    res = dict(CRISIS_RESOURCES.get(key, CRISIS_RESOURCES["INTL"]))
    res["region"] = key if key in CRISIS_RESOURCES else "INTL"
    res.setdefault("directory", "https://findahelpline.com")
    return res


def _resource_sentence(res: dict) -> str:
    parts = []
    if res.get("emergency"):
        parts.append(f"the local emergency number ({res['emergency']})")
    else:
        parts.append("the local emergency number")
    for line in res.get("lines", []):
        how = []
        if line.get("phone"):
            how.append(f"call {line['phone']}")
        if line.get("text"):
            how.append(f"text {line['text']}")
        parts.append(f"{line['name']} ({' or '.join(how)})")
    return ", ".join(parts)


def guidance_for(assessment: SafetyAssessment, region: str | None) -> str | None:
    """System guidance injected into the live voice session for this turn."""
    res = resources_for(region)
    lvl = assessment.level
    if lvl == RiskLevel.LOW:
        return None
    if lvl == RiskLevel.MODERATE:
        if "third_party_concern" in assessment.categories:
            return (
                "SAFETY NOTE: The user may be worried about someone else's safety. Acknowledge how hard that is. "
                "If that person might be in immediate danger, say clearly that the best step is to contact "
                f"emergency services or a crisis line for them: {_resource_sentence(res)}. "
                "Do not take on the role of crisis counsellor."
            )
        if "panic" in assessment.categories:
            return (
                "SAFETY NOTE: The user may be having intense anxiety or panic. Slow down. Use short, calm sentences. "
                "Offer one simple grounding step at a time (for example slow breathing with a longer out-breath). "
                "If they mention chest pain, fainting, or symptoms that feel physically dangerous, tell them to "
                "contact emergency services."
            )
        return (
            "SAFETY NOTE: The user said something that may reflect hopelessness or passive thoughts of not wanting "
            "to be here. Slow down. Reflect what you heard warmly. Then ask one gentle, direct question, such as "
            "whether they are having any thoughts of hurting themselves or ending their life. Do not lecture."
        )
    if lvl == RiskLevel.HIGH:
        return (
            "SAFETY PRIORITY: The user may be at risk of harm. Respond with calm warmth and without judgement. "
            "Ask directly whether they are safe right now. Clearly encourage them to reach real human support now: "
            f"a trusted person nearby, or {_resource_sentence(res)}. "
            "Mention that a support card with these numbers is on their screen. Do not ask them to promise anything, "
            "do not suggest they rely only on you, and do not try to act as their therapist. Keep it short."
        )
    return (
        "SAFETY EMERGENCY: The user may be in immediate danger. In one or two short sentences, tell them to contact "
        f"emergency services right now: {_resource_sentence(res)}, or to get a person nearby to help them now. "
        "Tell them the call button is on their screen. Do not continue the normal conversation, do not ask them to "
        "promise anything, and do not suggest staying with you instead of getting help."
    )
