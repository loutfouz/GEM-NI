import re
from nodebox.graphics import Geometry, Path, Transform

#the segment is not supplied as argument and generated as path
def lpath(x,y,angle,angleScale,length,thicknessScale,lengthScale,full_rule):

    p = Path()
    p.rect(0, -length/2, 2, length)
    segment = p.asGeometry()

    # Now run the simulation
    g = Geometry()
    stack = []
    angleStack = []
    t = Transform()
    t.translate(x, y)
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