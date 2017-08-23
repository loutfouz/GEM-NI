import re
from nodebox.graphics import Geometry, Path, Transform

def lrules(generations,premise,rule1,rule2,rule3):
   # Parse all rules
    ruleArgs = [rule1,rule2,rule3]
    rules = {}
    for full_rule in ruleArgs:
        #full_rule = getattr("rule%i" % rulenum)
        if len(full_rule) > 0:
            if len(full_rule) < 3 or full_rule[1] != '=':
                raise ValueError("Rule %s should be in the format A=FFF" % full_rule)
            rule_key = full_rule[0]
            rule_value = full_rule[2:]
            rules[rule_key] = rule_value
        #rulenum += 1
    # Expand the rules up to the number of generations
    full_rule = premise
    for gen in xrange(int(round(generations))):
        tmp_rule = ""
        for letter in full_rule:
            if letter in rules:
                tmp_rule += rules[letter]
            else:
                tmp_rule += letter
        full_rule = tmp_rule
    return full_rule