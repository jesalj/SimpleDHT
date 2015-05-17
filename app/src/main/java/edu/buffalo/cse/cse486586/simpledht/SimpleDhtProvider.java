package edu.buffalo.cse.cse486586.simpledht;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MergeCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();

    public static MessageOpenHelper dbHelper;
    public static SQLiteDatabase db;
    public static String TABLE = "messages";
    public static final Uri providerUri = Uri.parse("content://edu.buffalo.cse.cse486586.simpledht.provider");

    static  String MONITOR_NODE = "5554";
    static final int SERVER_PORT = 10000;
    static ArrayList<String> ringNodes = new ArrayList<>();
    static Cursor pubCursor;
    static Boolean queryResult = true;
    static int N = 0;
    static int starRecvd = 0;

    static String node = null;
    static String hashNode = null;
    static String pNode = null;
    static String pHashNode = null;
    static String sNode = null;
    static String sHashNode = null;

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        if (selection.equals("\"*\"") || selection.equals("\"@\"")) {
            db.delete(TABLE, null, null);
            Log.v("delete", selection);
        } else {
            String hashS = "";
            try {
                hashS = genHash(selection);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Exception in hashing delete selection: " + selection, e);
            }

            String originNode;
            if (selectionArgs == null || selectionArgs.length < 1) {
                originNode = node;
            } else {
                originNode = selectionArgs[0];
                if (originNode.equals(node))
                    return 0;
            }

            if (hashS.length() > 0) {
                if (isInRange(hashS)) {
                    db.delete(TABLE, "key=\"" + selection + "\"", null);
                    Log.v("delete", selection);
                } else {
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                            "delete", originNode, selection);
                }
            }
        }

        return 0;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        // TODO Auto-generated method stub
        String hashK = "";
        try {
            hashK = genHash(values.getAsString("key"));
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Exception in hashing insert key: " + values.getAsString("key"), e);
        }

        String originNode;
        if (values.containsKey("origin")) {
            originNode = values.getAsString("origin");
            values.remove("origin");
            if (originNode.equals(node))
                return uri;
        } else {
            originNode = node;
        }

        if (isInRange(hashK)) {
            long row = db.insert(TABLE, null, values);
            Log.v("insert", "node-" + node + " | " + values.toString());
        } else {
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                    "insert", originNode, values.getAsString("key"), values.getAsString("value"));
        }

        return uri;
    }

    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub
        dbHelper = new MessageOpenHelper(getContext());
        db = dbHelper.getWritableDatabase();

        TelephonyManager tel = (TelephonyManager) getContext().getSystemService(Context.TELEPHONY_SERVICE);
        node = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        try {
            hashNode = genHash(node);
        } catch (NoSuchAlgorithmException e) {
            Log.e(TAG, "Exception in hashing for node: " + node, e);
        }

        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "join_request", node);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
            return false;
        }

        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
        SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
        builder.setTables(TABLE);
        Cursor c = null;

        String originNode;
        if (sortOrder == null || sortOrder.equals("")) {
            originNode = node;
        } else {
            originNode = sortOrder;
            if (originNode.equals(node))
                return c;
        }

        if (selection.equals("\"*\"")) {

            Cursor localCursor = builder.query(db, projection, null, selectionArgs, null, null, null);

            if (!originNode.equals(node)) {
                ArrayList<HashMap<String, String>> cMap = getMapFromCursor(localCursor);
                //System.out.println("cMap size: " + cMap.size() + ", localCursor count: " + localCursor.getCount());
                StringBuilder tupleBuilder = new StringBuilder();
                for (int id = 0; id < cMap.size(); id++) {
                    HashMap<String, String> cm = cMap.get(id);
                    tupleBuilder.append(cm.get("key"));
                    tupleBuilder.append(":");
                    tupleBuilder.append(cm.get("value"));
                    if (id < cMap.size()-1)
                        tupleBuilder.append("_");
                }
                //System.out.println("tupleBuilder: " + tupleBuilder.toString());
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                        "query", originNode, selection);
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                        "star_response", originNode, tupleBuilder.toString());
            } else {
                queryResult = false;

                ClientTask ct = new ClientTask();
                ct.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                        "query", originNode, selection);
                try {
                    Integer u = ct.get();
                } catch (InterruptedException e) {
                    Log.e(TAG, "InterruptedException in ct.get() on node-" + node);
                } catch (ExecutionException e) {
                    Log.e(TAG, "ExecutionException in ct.get() on node-" + node);
                }

                Cursor[] ca = new Cursor[]{localCursor, pubCursor};
                c = new MergeCursor(ca);
            }

        } else if (selection.equals("\"**\"") || selection.equals("\"@\"")) {
            c = builder.query(db, projection, null, selectionArgs, null, null, null);
        } else {
            String hashS = "";
            try {
                hashS = genHash(selection);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Exception in hashing query selection: " + selection, e);
            }

            if (isInRange(hashS)) {
                c = builder.query(db, projection, "key=\"" + selection + "\"", selectionArgs, null, null, null);

                if (!originNode.equals(node)) {
                    ArrayList<HashMap<String, String>> cMap = getMapFromCursor(c);
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                            "query_response", originNode, cMap.get(0).get("key"), cMap.get(0).get("value"));
                }
            } else {
                queryResult = false;
                if (!originNode.equals(node)) {
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                            "query", originNode, selection);
                } else {
                    ClientTask clientTask = new ClientTask();
                    clientTask.executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                            "query", originNode, selection);
                    try {
                        Integer r = clientTask.get();
                    } catch (InterruptedException e) {
                        Log.e(TAG, "InterruptedException in clientTask.get() on node-" + node);
                    } catch (ExecutionException e) {
                        Log.e(TAG, "ExecutionException in clientTask.get() on node-" + node);
                    }

                    c = pubCursor;
                }
            }
        }
        if (c != null) {
            c.moveToFirst();
        }
        Log.v("query", selection);

        return c;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {

        public ArrayList<HashMap<String, String>> starRespMap = new ArrayList<>();

        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];
            Socket client = null;

            while (!serverSocket.isClosed()) {
                try {
                    client = serverSocket.accept();
                    ObjectInputStream ois = new ObjectInputStream(client.getInputStream());
                    Message msg = (Message) ois.readObject();
                    /*if (!msg.getType().startsWith("join")) {
                        System.out.println("---------------------\nNODE:" + node + "\n" + msg.toString() + "\n---------------------");
                    }*/

                    if (msg.getType().equals("join_request") && node.equals(MONITOR_NODE)) {
                        publishProgress (
                                msg.getType(),
                                msg.getNode()
                        );
                    } else if (msg.getType().equals("join_response") && msg.getNode().equals(MONITOR_NODE)) {
                        publishProgress(
                                msg.getType(),
                                msg.getNode(),
                                msg.getData().get("pred"),
                                msg.getData().get("succ"),
                                msg.getData().get("phash"),
                                msg.getData().get("shash"),
                                msg.getData().get("nhash"),
                                msg.getData().get("n")
                        );
                    } else if (msg.getType().equals("insert")) {
                        publishProgress(
                                msg.getType(),
                                msg.getNode(),
                                msg.getData().get("key"),
                                msg.getData().get("value")
                        );
                    }  else if (msg.getType().equals("delete") && msg.getNode().equals(pNode)) {
                        publishProgress(
                                msg.getType(),
                                msg.getNode(),
                                msg.getData().get("selection")
                        );
                    } else if (msg.getType().equals("query")) {
                        publishProgress(
                                msg.getType(),
                                msg.getNode(),
                                msg.getData().get("selection")
                        );
                    } else if (msg.getType().equals("query_response")) {
                        publishProgress(
                                msg.getType(),
                                msg.getNode(),
                                msg.getData().get("key"),
                                msg.getData().get("value")
                        );
                    } else if (msg.getType().equals("star_response")) {
                        publishProgress(
                                msg.getType(),
                                msg.getNode(),
                                msg.getData().get("result")
                        );
                    }
                } catch (IOException ioe) {
                    Log.e(TAG, "IOException in ServerTask(): ", ioe);
                } catch (ClassNotFoundException e) {
                    Log.e(TAG, "ClassNotFoundException in ServerTask(): ", e);
                }
            }

            return null;
        }

        protected void onProgressUpdate(String...strings) {

            if (strings[0].equals("join_request")) {

                ringNodes.add(strings[1]);
                formRing();

            } else if (strings[0].equals("join_response")) {

                pNode = strings[2];
                sNode = strings[3];
                pHashNode = strings[4];
                sHashNode = strings[5];
                hashNode = strings[6];
                N = Integer.parseInt(strings[7]);

            } else if (strings[0].equals("insert")) {

                ContentValues cv = new ContentValues();
                cv.put("origin", strings[1]);
                cv.put("key", strings[2]);
                cv.put("value", strings[3]);
                insert(providerUri, cv);

            } else if (strings[0].equals("delete")) {

                String[] selArgs = new String[]{strings[1]};
                delete(providerUri, strings[2], selArgs);

            } else if (strings[0].equals("query")) {

                query(providerUri, null, strings[2], null, strings[1]);

            } else if (strings[0].equals("query_response")) {

                ArrayList<HashMap<String, String>> cMap = new ArrayList<>();
                HashMap<String, String> map = new HashMap<>();
                map.put("key", strings[2]);
                map.put("value", strings[3]);
                cMap.add(map);
                pubCursor = getCursorFromMap(cMap);
                queryResult = true;

            } else if (strings[0].equals("star_response")) {

                starRecvd++;
                System.out.println("STAR RESPONSE node-" + node + " | from: node-" + strings[1] + ", strRecvd: " + starRecvd + ", N: " + N);
                if (starRecvd < N && strings[2].length() > 0) {
                    String[] tuples = strings[2].split("_");
                    for (String tuple : tuples) {
                        System.out.println("tuple before split: " + tuple);
                        String[] tupleAr = tuple.split(":");
                        System.out.println("tupleAr size " + tupleAr.length);
                        /*for (String ta : tupleAr) {
                            System.out.println("tupleAr[]= " + ta);
                        }*/
                        HashMap<String, String> tempMap = new HashMap<>();
                        tempMap.put("key", tupleAr[0]);
                        tempMap.put("value", tupleAr[1]);
                        starRespMap.add(tempMap);
                    }
                }

                if (starRecvd == N-1) {
                    System.out.println("starRespMap size: " + starRespMap.size());
                    if (starRespMap.size() > 0) {
                        pubCursor = getCursorFromMap(starRespMap);
                        if (pubCursor != null) {
                            System.out.println("pubCursor -> " + pubCursor.getCount());
                            //pubCursor.moveToFirst();
                        }
                    }
                    starRespMap.clear();
                    starRecvd = 0;
                    queryResult = true;
                }

            }

            return;
        }
    }

    private class ClientTask extends AsyncTask<String, Void, Integer> {

        private ArrayList<HashMap<String, String>> mapList;

        public ClientTask() {
            mapList = null;
        }

        public ClientTask(ArrayList<HashMap<String, String>> ml) {
            mapList = ml;
        }

        @Override
        protected Integer doInBackground(String... msgs) {
            if (msgs[0].equals("join_request")) {

                Message msg = new Message("join_request", msgs[1], null, mapList);
                int remotePort = Integer.parseInt(MONITOR_NODE) * 2;

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException in sending join request from node-" + node + " to node-" + MONITOR_NODE, e);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask IOException in sending join request from node-" + node + " to node-" + MONITOR_NODE, e);
                    MONITOR_NODE = node;
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, "join_request", msgs[1]);
                }

            } else if (msgs[0].equals("join_response") && msgs[1].equals(MONITOR_NODE)) {
                // ["join_response", node, predNode, succNode, sendToNode, pHashNode, sHashNode, sendHashToNode]
                HashMap<String, String> predSuccMap = new HashMap<>();
                predSuccMap.put("pred", msgs[2]);
                predSuccMap.put("succ", msgs[3]);
                predSuccMap.put("phash", msgs[5]);
                predSuccMap.put("shash", msgs[6]);
                predSuccMap.put("nhash", msgs[7]);
                predSuccMap.put("n", msgs[8]);
                Message msg = new Message("join_response", msgs[1], predSuccMap, mapList);
                int remotePort = Integer.parseInt(msgs[4]) * 2;

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException in sending join response from node-" + node + " to node-" + msgs[4], e);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask IOException in sending join response from node-" + node + " to node-" + msgs[4], e);
                }

            } else if (msgs[0].equals("insert")) {

                if (sNode == null) {
                    return 1;
                }
                HashMap<String, String> insertMap = new HashMap<>();
                insertMap.put("key", msgs[2]);
                insertMap.put("value", msgs[3]);
                Message msg = new Message("insert", msgs[1], insertMap, mapList);
                int remotePort = Integer.parseInt(sNode) * 2;

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException in sending insert from node-" + node + " to node-" + sNode, e);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask IOException in sending insert from node-" + node + " to node-" + sNode, e);
                }

            } else if (msgs[0].equals("delete")) {

                if (sNode == null) {
                    return 1;
                }
                HashMap<String, String> deleteMap = new HashMap<>();
                deleteMap.put("selection", msgs[2]);
                Message msg = new Message("delete", msgs[1], deleteMap, mapList);
                int remotePort = Integer.parseInt(sNode) * 2;

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException in sending delete from node-" + node + " to node-" + sNode, e);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask IOException in sending delete from node-" + node + " to node-" + sNode, e);
                }

            } else if (msgs[0].equals("query")) {

                if (sNode == null) {
                    return 1;
                }
                HashMap<String, String> queryMap = new HashMap<>();
                queryMap.put("selection", msgs[2]);
                Message msg = new Message("query", msgs[1], queryMap, mapList);
                int remotePort = Integer.parseInt(sNode) * 2;

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException in sending query (selection: " + msgs[2] + ") from node-" + node + " to node-" + sNode, e);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask IOException in sending query (selection: " + msgs[2] + ") from node-" + node + " to node-" + sNode, e);
                }

                if (msgs[1].equals(node)) {
                    while (!queryResult) {
                        // wait for query response on ServerTask
                    }
                }

            } else if (msgs[0].equals("query_response")) {

                HashMap<String, String> queryResponseMap = new HashMap<>();
                queryResponseMap.put("key", msgs[2]);
                queryResponseMap.put("value", msgs[3]);
                Message msg = new Message("query_response", node, queryResponseMap, mapList);
                int remotePort = Integer.parseInt(msgs[1]) * 2;

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException in sending query response from node-" + node + " to node-" + msgs[1], e);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask IOException in sending query response from node-" + node + " to node-" + msgs[1], e);
                }

            } else if (msgs[0].equals("star_response")) {

                HashMap<String, String> starResponseMap = new HashMap<>();
                starResponseMap.put("result", msgs[2]);
                Message msg = new Message("star_response", node, starResponseMap, mapList);
                int remotePort = Integer.parseInt(msgs[1]) * 2;

                try {
                    Socket socket = new Socket(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}), remotePort);
                    ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                    out.flush();
                    out.writeObject(msg);
                    out.flush();
                    out.close();
                    socket.close();
                } catch (UnknownHostException e) {
                    Log.e(TAG, "ClientTask UnknownHostException in sending star response from node-" + node + " to node-" + msgs[1], e);
                } catch (IOException e) {
                    Log.e(TAG, "ClientTask IOException in sending star response from node-" + node + " to node-" + msgs[1], e);
                }

            }

            return 0;
        }
    }

    public void formRing() {

        if (ringNodes.size() < 1)
            return;

        if (ringNodes.size() == 1) {
            String n = ringNodes.get(0);
            String hn = "";
            try {
                hn = genHash(n);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Exception in hashing single node-" + n, e);
            }
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                    "join_response", node, null, null, n, null, null, hn, String.valueOf(ringNodes.size()));
            return;
        }

        TreeMap<String, String> ringHashNodes = new TreeMap<>();
        for (String n : ringNodes) {
            String hn;
            try {
                hn = genHash(n);
                ringHashNodes.put(hn, n);
            } catch (NoSuchAlgorithmException e) {
                Log.e(TAG, "Exception in hashing ring node-" + n, e);
            }
        }

        ArrayList<String> rNodes = new ArrayList<>();
        ArrayList<String> hNodes = new ArrayList<>();
        for (String hn : ringHashNodes.keySet()) {
            rNodes.add(ringHashNodes.get(hn));
            hNodes.add(hn);
        }

        if (rNodes.size() == 2) {
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                    "join_response", node, rNodes.get(1), rNodes.get(1), rNodes.get(0), hNodes.get(1), hNodes.get(1), hNodes.get(0), String.valueOf(rNodes.size()));
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                    "join_response", node, rNodes.get(0), rNodes.get(0), rNodes.get(1), hNodes.get(0), hNodes.get(0), hNodes.get(1), String.valueOf(rNodes.size()));
        } else if (rNodes.size() > 2) {
            for (int i = 0; i < rNodes.size(); i++) {
                String sendToNode = rNodes.get(i);
                String sendHashToNode = hNodes.get(i);
                String predNode;
                String succNode;
                String pHashNode;
                String sHashNode;
                if (i == 0) {
                    predNode = rNodes.get(rNodes.size()-1);
                    succNode = rNodes.get(i+1);
                    pHashNode = hNodes.get(hNodes.size()-1);
                    sHashNode = hNodes.get(i+1);
                } else if (i == rNodes.size()-1) {
                    predNode = rNodes.get(i-1);
                    succNode = rNodes.get(0);
                    pHashNode = hNodes.get(i-1);
                    sHashNode = hNodes.get(0);
                } else {
                    predNode = rNodes.get(i-1);
                    succNode = rNodes.get(i+1);
                    pHashNode = hNodes.get(i-1);
                    sHashNode = hNodes.get(i+1);
                }
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,
                        "join_response", node, predNode, succNode, sendToNode, pHashNode, sHashNode, sendHashToNode, String.valueOf(rNodes.size()));
            }
        }

    }

    public boolean isInRange(String hkey) {

        if (hkey == null || hkey.length() < 1) {
            return false;
        }

        if (pHashNode == null && sHashNode == null) {
            return true;
        }

        /*if (pNode.equals(sNode)) {
            return true;
        }*/

        if (hkey.compareTo(pHashNode) > 0 && hkey.compareTo(hashNode) < 0) {
            return true;
        }

        if (pHashNode.compareTo(hashNode) > 0 && sHashNode.compareTo(hashNode) > 0 && hkey.compareTo(pHashNode) > 0) {
            return true;
        }

        if (pHashNode.compareTo(hashNode) > 0 && sHashNode.compareTo(hashNode) > 0 && hkey.compareTo(hashNode) < 0) {
            return true;
        }

        return false;
    }

    public ArrayList<HashMap<String, String>> getMapFromCursor(Cursor cr) {

        ArrayList<HashMap<String, String>> crMap = new ArrayList<>();
        int keyIndex = cr.getColumnIndex("key");
        int valueIndex = cr.getColumnIndex("value");

        while (cr.moveToNext()) {
            HashMap<String, String> map = new HashMap<>();
            map.put("key", cr.getString(keyIndex));
            map.put("value", cr.getString(valueIndex));
            crMap.add(map);
        }

        return crMap;
    }

    public Cursor getCursorFromMap(ArrayList<HashMap<String, String>> cMap) {

        if (cMap.size() == 1) {
            // insert
            HashMap<String, String> map = cMap.get(0);
            ContentValues cvs = new ContentValues();
            cvs.put("key", map.get("key"));
            cvs.put("value", map.get("value"));
            long row = db.insert(TABLE, null, cvs);

            // query
            SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
            builder.setTables(TABLE);
            Cursor c = builder.query(db, null, "key=\"" + map.get("key") + "\"", null, null, null, null);
            c.moveToFirst();

            // delete
            db.delete(TABLE, "key=\"" + map.get("key") + "\"", null);

            return c;
        } else if (cMap.size() > 1) {
            ArrayList<String> keySet = new ArrayList<>();

            // insert
            for (HashMap<String, String> map : cMap) {
                keySet.add(map.get("key"));
                ContentValues cvs = new ContentValues();
                cvs.put("key", map.get("key"));
                cvs.put("value", map.get("value"));
                long row = db.insert(TABLE, null, cvs);
            }

            // query
            Cursor[] m = new Cursor[keySet.size()];
            int idx = 0;
            for (String k : keySet) {
                SQLiteQueryBuilder builder = new SQLiteQueryBuilder();
                builder.setTables(TABLE);
                Cursor c = builder.query(db, null, "key=\"" + k + "\"", null, null, null, null);
                c.moveToFirst();
                m[idx] = c;
                idx++;
            }
            Cursor c = new MergeCursor(m);
            c.moveToFirst();

            // delete
            for (String k : keySet) {
                db.delete(TABLE, "key=\"" + k + "\"", null);
            }

            return c;
        }

        return null;
    }

}
