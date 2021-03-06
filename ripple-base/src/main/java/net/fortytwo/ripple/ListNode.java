package net.fortytwo.ripple;

/**
 * The head of an abstract linked list.
 *
 * @author Joshua Shinavier (http://fortytwo.net)
 */
public abstract class ListNode<T> {
    /**
     * @return the value at the head of the list, or <code>null</code> if this is a nil list
     */
    public abstract T getFirst();

    /**
     * @return the rest of the list, or <code>null</code> if this is a nil list
     */
    public abstract ListNode<T> getRest();

    /**
     * @return whether this is a nil list node, being the terminator of any number of lists
     */
    public abstract boolean isNil();

    public boolean equals(final ListNode<T> other) {
        ListNode<T> thisCur = this;
        ListNode<T> otherCur = other;

        while (!thisCur.isNil()) {
            if (otherCur.isNil()) {
                return false;
            }

            if (!thisCur.getFirst().equals(otherCur.getFirst())) {
                return false;
            }

            thisCur = thisCur.getRest();
            otherCur = otherCur.getRest();
        }

        return otherCur.isNil();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("(");

        ListNode<T> cur = this;
        while (!cur.isNil()) {
            sb.append(cur.getFirst());

            cur = cur.getRest();
            if (!cur.isNil()) {
                sb.append(", ");
            }
        }

        sb.append(")");
        return sb.toString();
    }
}

