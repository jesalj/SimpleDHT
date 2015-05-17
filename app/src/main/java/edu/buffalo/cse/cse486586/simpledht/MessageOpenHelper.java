package edu.buffalo.cse.cse486586.simpledht;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/**
 * Created by jesal on 3/27/15.
 */
public class MessageOpenHelper extends SQLiteOpenHelper {

    private static SQLiteDatabase sqldb;
    private static int DB_VERSION = 1;
    private static String DB_NAME = "messagestore";
    private static String TABLE_NAME = "messages";
    private static String TEMP_TABLE_NAME = "temp";
    private static String KEY_COLUMN = "key";
    private static String VALUE_COLUMN = "value";

    private static String create = "CREATE TABLE " + TABLE_NAME + " (" +
            KEY_COLUMN + " TEXT PRIMARY KEY, " +
            VALUE_COLUMN + " TEXT);";
    private static String createTemp = "CREATE TABLE " + TEMP_TABLE_NAME + " (" +
            KEY_COLUMN + " TEXT PRIMARY KEY, " +
            VALUE_COLUMN + " TEXT);";

    public MessageOpenHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL(create);
        db.execSQL(createTemp);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int x, int y) {
        int db_ver = db.getVersion();
        db_ver += 1;
        DB_VERSION = db_ver;
        db.setVersion(db_ver+1);
    }
}

