package edu.buffalo.cse.cse486586.simpledht;

import java.util.Comparator;

/**
 * Created by jesal on 3/31/15.
 */
public class HashComparator implements Comparator<String> {

    @Override
    public int compare(String lhs, String rhs) {
        return lhs.compareTo(rhs);
    }
}
