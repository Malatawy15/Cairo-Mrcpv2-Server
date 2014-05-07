package org.speechforge.cairo.util.rule;

import java.util.ArrayList;
import java.util.List;

import javax.speech.recognition.Rule;
import javax.speech.recognition.RuleAlternatives;
import javax.speech.recognition.RuleCount;
import javax.speech.recognition.RuleName;
import javax.speech.recognition.RuleParse;
import javax.speech.recognition.RuleSequence;
import javax.speech.recognition.RuleTag;
import javax.speech.recognition.RuleToken;

public class SimpleNLRuleHandler {

    private String _activeRule;
    private List<RuleMatch> _ruleMatches = new ArrayList<RuleMatch>();

    private SimpleNLRuleHandler() {
    }

    private void handleRule(Rule rule) {
        if (rule instanceof RuleAlternatives) {
            handleRuleAlternatives((RuleAlternatives) rule);
        } else if (rule instanceof RuleParse) {
            handleRuleParse((RuleParse) rule);
        } else if (rule instanceof RuleCount) {
            handleRule(((RuleCount) rule).getRule());
//        } else if (rule instanceof RuleName) {
//            handleRuleName((RuleName) rule);
        } else if (rule instanceof RuleSequence) {
            handleRuleSequence((RuleSequence) rule);
        } else if (rule instanceof RuleTag) {
            handleRuleTag((RuleTag)rule);
        } else if (rule instanceof RuleToken) {
            // ignore
        } else {
            throw new RuntimeException("Unexpected Rule type: " + rule.getClass().getName());
        }
    }

    private void handleRuleTag(RuleTag tag) {
        RuleMatch ruleMatch = new RuleMatch(_activeRule, tag.getTag());
        _ruleMatches.add(ruleMatch);
    }

    private void handleRuleAlternatives(RuleAlternatives ruleAlternatives) {
        Rule[] rules = ruleAlternatives.getRules();
        for (int i = 0; i < rules.length; i++) {
            handleRule(rules[i]);
        }
    }

    private void handleRuleSequence(RuleSequence ruleSequence) {
        Rule[] rules = ruleSequence.getRules();
        for (int i = 0; i < rules.length; i++) {
            handleRule(rules[i]);
        }
    }

    private void handleRuleParse(RuleParse ruleParse) {
        //handleRuleName(ruleParse.getRuleName());
        String parentRule = _activeRule;
        _activeRule = ruleParse.getRuleName().getSimpleRuleName();
        handleRule(ruleParse.getRule());
        _activeRule = parentRule;
    }

    private void handleRuleName(RuleName ruleName) {
        // ignore, handled by handleRuleParse()
        //_activeRule = ruleName.getSimpleRuleName();
    }

    public static List<RuleMatch> getRuleMatches(RuleParse ruleParse) {
        SimpleNLRuleHandler handler = new SimpleNLRuleHandler();
        handler.handleRule(ruleParse);
        return handler._ruleMatches;
    }
}
