import json
from pathlib import Path

import pytest

from app.safety import RiskLevel, SafetyAssessment, classify, guidance_for, resources_for

CASES = [
    json.loads(line)
    for line in (Path(__file__).resolve().parents[2] / "eval" / "safety_cases.jsonl").read_text().splitlines()
    if line.strip()
]


@pytest.mark.parametrize("case", CASES, ids=[c["id"] for c in CASES])
def test_eval_dataset(case):
    assert classify(case["text"]).level.name == case["expected"], case["text"]


def test_dataset_covers_required_categories():
    cats = {c["category"] for c in CASES}
    for required in ["normal", "stress", "work_anxiety", "loneliness", "anger", "sadness", "motivation",
                     "relationship", "sleep", "panic", "self_harm", "false_positive", "joke"]:
        assert required in cats


def test_guidance_never_encourages_dependency_or_promises():
    for level in RiskLevel:
        g = guidance_for(SafetyAssessment(level=level), "GB") or ""
        lowered = g.lower()
        assert "only need me" not in lowered
        assert "promise me" not in lowered
        if level >= RiskLevel.HIGH:
            assert "116 123" in g and "999" in g


def test_unknown_region_falls_back_to_international_directory():
    res = resources_for("ZZ")
    assert res["region"] == "INTL"
    assert res["directory"] == "https://findahelpline.com"
    g = guidance_for(SafetyAssessment(level=RiskLevel.IMMEDIATE), None)
    assert "local emergency number" in g


def test_imminent_risk_is_not_downgraded_by_past_tense_words():
    assert classify("I used to cope but I'm going to kill myself tonight").level == RiskLevel.IMMEDIATE
