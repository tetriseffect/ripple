package net.fortytwo.ripple.libs.stack;

import net.fortytwo.flow.Sink;
import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.PrimitiveStackMapping;
import net.fortytwo.ripple.model.RippleList;

/**
 * A primitive which consumes a list and an item and produces a Boolean value of
 * true if the item is contained in the list, otherwise false.
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public class Has extends PrimitiveStackMapping {
    private static final String[] IDENTIFIERS = {
            StackLibrary.NS_2013_03 + "has",
            StackLibrary.NS_2008_08 + "has",
            StackLibrary.NS_2007_08 + "has",
            StackLibrary.NS_2007_05 + "has"};

    public String[] getIdentifiers() {
        return IDENTIFIERS;
    }

    public Has() {
        super();
    }

    public Parameter[] getParameters() {
        return new Parameter[]{
                new Parameter("l", "a list", true),
                new Parameter("x", null, true)};
    }

    public String getComment() {
        return "l x  =>  b  -- where b is true if List l contains a member which is equal to x, otherwise false";
    }

    private boolean has(RippleList l, final Object v, final ModelConnection mc) {
        while (!l.isNil()) {
            if (0 == mc.getComparator().compare(l.getFirst(), v)) {
                return true;
            }

            l = l.getRest();
        }

        return false;
    }

    public void apply(final RippleList arg,
                      final Sink<RippleList> solutions,
                      final ModelConnection mc) throws RippleException {
        RippleList stack = arg;

        Object l;

        final Object x = stack.getFirst();
        stack = stack.getRest();
        l = stack.getFirst();
        final RippleList rest = stack.getRest();

        Sink<RippleList> listSink = list -> solutions.accept(
                rest.push(has(list, x, mc)));

        mc.toList(l, listSink);
    }
}

