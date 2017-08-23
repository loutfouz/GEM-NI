import re
from nodebox.graphics import Geometry, Path, Transform

def cook(generations,x,y,angle,angleScale,length,thicknessScale,lengthScale,premise,rule1,rule2,rule3):
    #segment = self.segment
    #if segment is None:
    p = Path()
    p.rect(0, -length/2, 2, length)
    segment = p.asGeometry()
    # Parse all rules
    ruleArgs = [rule1,rule2,rule3]
    rules = {}
    #rulenum = 1
    #while hasattr(cook,"rule%i" % rulenum):
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
    # Now run the simulation
    g = Geometry()
    stack = []
    angleStack = []
    t = Transform()
    t.translate(x, y)
    angle = angle
    for letter in full_rule:
        if re.search('[a-zA-Z]',letter): # Move forward and draw
            newShape = t.map(segment)
            g.extend(newShape)
            t.translate(0, -length)
        elif letter == '+': # Rotate right
            t.rotate(angle)
        elif letter == '-': # Rotate left
            t.rotate(-angle)
        elif letter == '[': # Push state (start branch)
            stack.append(Transform(t))
            angleStack.append(angle)
        elif letter == ']': # Pop state (end branch)
            t = stack.pop()
            angle = angleStack.pop()
        elif letter == '"': # Multiply length
            t.scale(1.0, lengthScale/100.0)
        elif letter == '!': # Multiply thickness
            t.scale(thicknessScale/100.0, 1.0)
        elif letter == ';': # Multiply angle
            angle *= angleScale/100.0
        elif letter == '_': # Divide length
            t.scale(1.0, 1.0/(lengthScale/100.0))
        elif letter == '?': # Divide thickness
            t.scale(1.0/(thicknessScale/100.0), 1.0)
        elif letter == '@': # Divide angle
            angle /= angleScale/100.0
    return g