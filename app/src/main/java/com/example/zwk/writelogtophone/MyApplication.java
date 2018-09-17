package com.example.zwk.writelogtophone;

import android.app.Application;
import android.content.Context;
import android.os.Environment;


public class MyApplication extends Application {
    public static Context mContext;
    private String path = null;
    public static String PATH = null;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = getApplicationContext();
        path = Environment.getExternalStorageDirectory().getPath();
        path = Environment.getExternalStorageDirectory().getAbsolutePath();
        PATH = path + "/zwkBug/zwkLog";   // 日志log的存储的目录。
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(this);
    }

}
