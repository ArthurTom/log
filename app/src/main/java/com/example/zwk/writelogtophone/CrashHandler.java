package com.example.zwk.writelogtophone;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Build;
import android.os.Environment;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * UncaughtException处理类,当程序发生Uncaught异常的时候,有该类来接管程序,并记录发送错误报告.
 *
 * @author user
 */
public class CrashHandler implements UncaughtExceptionHandler {
	public static final String TAG = "CrashHandler";
	// 系统默认的UncaughtException处理类
	private UncaughtExceptionHandler mDefaultHandler;
	// CrashHandler实例
	private static CrashHandler INSTANCE = new CrashHandler();
	// 程序的Context对象
	private Context mContext;
	// 用来存储设备信息和异常信息
	private Map<String, String> infos = new HashMap<String, String>();

	// 用于格式化日期,作为日志文件名的一部分
	private DateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
	//, mContext.getResources().getConfiguration().locale

	// 用于保存设置的邮箱信息
    //private MailSenderInfo mMailInfo = new MailSenderInfo();

	/**
	 * 保证只有一个CrashHandler实例
	 */
	private CrashHandler() {
	}

	/**
	 * 获取CrashHandler实例 ,单例模式
	 */
	public static CrashHandler getInstance() {
		return INSTANCE;
	}

	/**
	 * 初始化
	 */
	public void init(Context context) {
		mContext = context;
		// 获取系统默认的UncaughtException处理器
		mDefaultHandler = Thread.getDefaultUncaughtExceptionHandler();
		// 设置该CrashHandler为程序的默认处理器
		Thread.setDefaultUncaughtExceptionHandler(this);
//		setDefaultMailInfo();
	}

	/**
	 * 当UncaughtException发生时会转入该函数来处理
	 */
	@Override
	public void uncaughtException(Thread thread, Throwable ex) {
		if (!handleException(ex) && mDefaultHandler != null) {
			// 如果用户没有处理则让系统默认的异常处理器来处理
			mDefaultHandler.uncaughtException(thread, ex);
		} else {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				Log.e(TAG, "error : ", e);
			}
//			Intent startMain = new Intent("app_launcher");
//			startMain.addCategory(Intent.CATEGORY_DEFAULT);
//			startMain.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
//			mContext.startActivity(startMain);

			AlarmManager alarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
			Intent intent = new Intent(mContext, MainActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP);
			PendingIntent restartIntent = PendingIntent.getActivity(mContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);
			alarmManager.set(AlarmManager.RTC, System.currentTimeMillis()+1000 , restartIntent);
			System.exit(0);

		}
	}

	/**
	 * 自定义错误处理,收集错误信息 发送错误报告等操作均在此完成.
	 *
	 * @param ex exception  错误的信息
	 * @return true:如果处理了该异常信息;否则返回false.
	 */
	private boolean handleException(final Throwable ex) {
		if (ex == null) {
			return false;
		}
		ex.printStackTrace();

		// 收集设备参数信息
		collectDeviceInfo(mContext);
		// 保存日志文件
		final String content = saveCrashInfo2File(ex);

		Log.e(TAG, content);
		
		// 使用Toast来显示异常信息
		new Thread() {
			@Override
			public void run() {
				try {
//					if (NetworkUtil.isNetworkAvailable(mContext)) {
////						sendCrashInfo(content, ex);
//					}
					Looper.prepare();
					Toast toast = Toast.makeText(mContext, "app出现了异常！！！！", Toast.LENGTH_SHORT);
					toast.setGravity(Gravity.CENTER, 0, 0);
//					toast.getView().setBackgroundColor(Color.RED);
					toast.show();
					//清空消息队列
//					BaseHandler handler = new BaseHandler();
//					handler.clear();

					Looper.loop();
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}.start();

		return true;
	}

	/**
	 * 收集设备参数信息
	 *
	 * @param ctx context
	 */
	public void collectDeviceInfo(Context ctx) {
		try {
			//获取app信息版本信息
			PackageManager pm = ctx.getPackageManager();
			PackageInfo pi = pm.getPackageInfo(ctx.getPackageName(), PackageManager.GET_ACTIVITIES);
			if (pi != null) {
				String versionName = pi.versionName == null ? "null" : pi.versionName;
				String versionCode = pi.versionCode + "";
				infos.put("versionName", versionName);
				infos.put("versionCode", versionCode);
			}
		} catch (NameNotFoundException e) {
			Log.e(TAG, "an error occured when collect package info", e);
		}
		Field[] fields = Build.class.getDeclaredFields();
		for (Field field : fields) {
			try {
				field.setAccessible(true);
				infos.put(field.getName(), field.get(null).toString());
//				Log.i(TAG, field.getName() + " : " + field.get(null));
			}
			catch (Exception e)
			{
				Log.e(TAG, "an error occured when collect crash info", e);
			}
		}
	}

	/**
	 * 保存错误信息到文件中
	 *
	 * @param ex exception
	 * @return 返回错误信息字符串, 便于将文件传送到服务器
	 */
	public String saveCrashInfo2File(Throwable ex) {
		StringBuffer sb = new StringBuffer();
		for (Map.Entry<String, String> entry : infos.entrySet()) {
			String key = entry.getKey();
			String value = entry.getValue();
			sb.append(key).append("=").append(value).append("\n");
		}

		Writer writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		ex.printStackTrace(printWriter);
		Throwable cause = ex.getCause();
		while (cause != null) {
			cause.printStackTrace(printWriter);
			cause = cause.getCause();
		}
		printWriter.close();
		String result = writer.toString();
		sb.append(result);
		try {
			if (com.example.zwk.writelogtophone.Log.DEBUG) {
				// long timestamp = System.currentTimeMillis();
				String time = formatter.format(new Date());
				String fileName = "crash_" + time + /* "-" + timestamp + */".log";
				if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					// 报错异常存储的地址/log/crash/日期.log文件
					String path = Environment.getExternalStorageDirectory().getAbsolutePath() +"/log"+ "/crash/";
					File dir = new File(path);
					if (!dir.exists()) {
						dir.mkdirs();
					}
					FileOutputStream fos = new FileOutputStream(path + fileName);
					// 先判断可以用的空间和写入文件的大小，然后写入文件
//					if (MemorySpaceCheck.getSDAvailableSize() > sb.toString().getBytes().length) {
//						fos.write(sb.toString().getBytes());
//					}
					fos.close();
				}
			}
			return sb.toString();
		} catch (Exception e) {
			Log.e(TAG, "an error occured while writing file...", e);
		}
		return null;
	}

/*	private void sendCrashInfo(String content, Throwable ex) {
		if (!Log.DEBUG) {
			mMailInfo.setContent(content);
			if (mMailInfo.getSubject() == null) {
				mMailInfo.setSubject(ex.getMessage());
			}
			SendEmailUtil.sendText(mMailInfo);
		}
	}*/

	/**
	 * 设置用于接收crash信息的邮箱信息
	 *
	 * @param mailInfo 邮箱信息
	 */
//	public void setMailInfo(MailSenderInfo mailInfo) {
//		mMailInfo.setMailServerHost(mailInfo.getMailServerHost());
//		mMailInfo.setMailServerPort(mailInfo.getMailServerPort());
//		mMailInfo.setValidate(mailInfo.isValidate());
//		mMailInfo.setUserName(mailInfo.getUserName()); // 你的邮箱地址
//		mMailInfo.setPassword(mailInfo.getPassword());// 您的邮箱密码
//		mMailInfo.setFromAddress(mailInfo.getFromAddress());
//		mMailInfo.setToAddress(mailInfo.getToAddress());
//		mMailInfo.setSubject(mailInfo.getSubject());
//	}

//	private void setDefaultMailInfo() {
//		mMailInfo.setMailServerHost("smtp.163.com");
//		mMailInfo.setMailServerPort("25");
//		mMailInfo.setValidate(true);
//		mMailInfo.setUserName("whq1987cs@126.com"); // 你的邮箱地址
//		mMailInfo.setPassword("7070567");// 您的邮箱密码
//		mMailInfo.setFromAddress("whq1987cs@126.com");
//		mMailInfo.setToAddress("whq1987cs@126.com");
//	}
}
