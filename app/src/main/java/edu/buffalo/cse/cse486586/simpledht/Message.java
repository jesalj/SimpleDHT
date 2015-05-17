package edu.buffalo.cse.cse486586.simpledht;

import android.database.Cursor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by jesal on 3/29/15.
 */
public class Message implements Serializable {

    private String type;
    private String node;
    private HashMap<String, String> datamap;
    private ArrayList<HashMap<String, String>> cursormap;

    public Message(String t, String n, HashMap<String, String> dm, ArrayList<HashMap<String, String>> cm) {
        type = t;
        node = n;
        datamap = dm;
        cursormap = cm;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public HashMap<String, String> getData() {
        return datamap;
    }

    public void setData(HashMap<String, String> datamap) {
        this.datamap = datamap;
    }

    public String getNode() {
        return node;
    }

    public void setNode(String node) {
        this.node = node;
    }

    public ArrayList<HashMap<String, String>> getCursorMap() {
        return cursormap;
    }

    public void setCursorMap(ArrayList<HashMap<String, String>> cursormap) {
        this.cursormap = cursormap;
    }

    @Override
    public String toString() {
        return "Message (\n" + "\ttype: " + type + "\n\tnode: " + node + "\n\tdatamap: " + datamap + "\n\tcursormap: " + cursormap + "\n)";
    }

}
