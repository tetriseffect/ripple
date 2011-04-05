package net.fortytwo.ripple.model.keyval;

import net.fortytwo.ripple.RippleException;
import net.fortytwo.ripple.io.RipplePrintStream;
import net.fortytwo.ripple.model.ModelConnection;
import net.fortytwo.ripple.model.RDFValue;
import net.fortytwo.ripple.model.RippleList;
import net.fortytwo.ripple.model.RippleValue;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * User: josh
 * Date: Aug 18, 2010
 * Time: 1:38:43 PM
 */
public class JSONValue implements KeyValueValue {
    private final JSONObject jsonObject;

    public JSONValue(final JSONObject jsonObject) {
        this.jsonObject = jsonObject;
    }

    public RDFValue toRDF(final ModelConnection mc) throws RippleException {
        // FIXME: add a data type
        return mc.value(jsonObject.toString());
    }

    public boolean isActive() {
        return false;
    }

    public void printTo(final RipplePrintStream p) throws RippleException {
        p.print(jsonObject.toString());
    }

    public RippleValue getValue(final String key,
                                final ModelConnection mc) throws RippleException {
        Object o = jsonObject.opt(key);
        if (null == o) {
            return null;
        } else {
            return toRippleValue(o, mc);
        }
    }

    public Collection<String> getKeys() {
        List<String> keys = new LinkedList<String>();
        Iterator i = jsonObject.keys();
        while (i.hasNext()) {
            Object key = i.next();
            if (key instanceof String) {
                keys.add((String) key);
            }
        }

        return keys;
    }

    /**
     * @param o  an object of type JSONObject, JSONArray, boolean, double, integer, long, or String
     * @param mc a Ripple ModelConnection
     * @return a corresponding RippleValue
     * @throws RippleException if something goes awry
     */
    public static RippleValue toRippleValue(final Object o,
                                            final ModelConnection mc) throws RippleException {
        if (o instanceof Boolean) {
            return mc.value((Boolean) o);
        } else if (o instanceof Double) {
            return mc.value((Double) o);
        } else if (o instanceof Integer) {
            return mc.value((Integer) o);
        } else if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            RippleList l = mc.list();
            for (int i = a.length() - 1; i >= 0; i--) {
                try {
                    l = l.push(toRippleValue(a.get(i), mc));
                } catch (JSONException e) {
                    throw new RippleException(e);
                }
            }
            return l;
        } else if (o instanceof JSONObject) {
            return new JSONValue((JSONObject) o);
        } else if (o instanceof Long) {
            return mc.value((Long) o);
        } else if (o instanceof String) {
            return mc.value((String) o);
        } else {
            throw new RippleException("tried to convert object of unfamiliar type: " + o);
        }
    }

    @Override
    public String toString() {
        return jsonObject.toString();
    }

    public int compareTo(final KeyValueValue other) {
        if (other instanceof JSONValue) {
            return this.jsonObject.toString().compareTo(other.toString());
        } else {
            return this.getClass().getName().compareTo(other.getClass().getName());
        }
    }
}
