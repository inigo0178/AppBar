/**
 * AppBar - a simple application switcher for Android
 * Copyright (C) 2012  Sven Bendel (ubuntudroid@googlemail.com)
 *
 * This file is part of AppBar.
 *
 * AppBar is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * AppBar is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with AppBar. If not, see <http://www.gnu.org/licenses/>.
 */

package de.ubuntudroid.appbar;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.locks.ReentrantLock;

import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.os.IBinder;
import android.util.Log;

public class AppBarService extends Service {

	public static final String START_ACTIVITY_INTENT = "de.ubuntudroid.appbar.START_ACTIVITY_INTENT";
	public static final String ACTIVITY_ID_EXTRA = "de.ubuntudroid.appbar.ACTIVITY_ID_EXTRA"; 
	
	private static final int APP_BAR_NOTIFICATION = 0;
	
	private NotificationManager nm;
	private ActivityManager am;
	private PackageManager pm;
	private long firstNotificationTime = -1;
	private Map<Integer, Intent> buttonIntents = new LinkedHashMap<Integer, Intent>();
	private BroadcastReceiver screenStateChangedBroadcastReceiver;
	
	private static AppBarService instance;
	
	private final Vector<Integer> resIds = new Vector<Integer>();
	
	private ReentrantLock updateNotificationLock = new ReentrantLock();

	private volatile Thread notificationUpdaterThread;

	public static AppBarService getInstance() {
		return instance;
	}
	
	@Override
	public IBinder onBind(Intent intent) {
		/* not implemented as other Activities/Apps should not be able to connect to this service
		 * (except for the configurator?)
		 */
		return null;
	}
	
	@Override
	public void onCreate() {
		super.onCreate();
		
		instance = this;
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		pm = (PackageManager) getPackageManager();
		
		resIds.add(R.id.app1);
		resIds.add(R.id.app2);
		resIds.add(R.id.app3);
		resIds.add(R.id.app4);
		resIds.add(R.id.app5);
		
		// register receiver that handles screen on and screen off logic
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        screenStateChangedBroadcastReceiver = new ScreenBroadcastReceiver();
        registerReceiver(screenStateChangedBroadcastReceiver, filter);
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		startNotificationUpdating();
	}
	
	@Override
	public void onDestroy() {
		stopNotificationUpdating();
		unregisterReceiver(screenStateChangedBroadcastReceiver);
		super.onDestroy();
	}
	
	public synchronized void startNotificationUpdating() {
		if (notificationUpdaterThread != null) {
			stopNotificationUpdating();
		}
		notificationUpdaterThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				while(Thread.currentThread() == notificationUpdaterThread){
					reloadNotification();
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						// we expect exceptions here if service is destroyed on screen off
					}
				}
			}
		});
		notificationUpdaterThread.start();
	}
	
	public synchronized void stopNotificationUpdating() {
		if (notificationUpdaterThread != null) {
			Thread toBeKilled = notificationUpdaterThread;
			notificationUpdaterThread = null;
			toBeKilled.interrupt();
		}
		nm.cancel(APP_BAR_NOTIFICATION);
	}

	private void prepareButtonBitmaps(AppBarNotification appBarNotification, Collection<Bitmap> appIcons) {
		Iterator<Bitmap> iconIt = appIcons.iterator();
		Iterator<Integer> resIt = buttonIntents.keySet().iterator();
		while (iconIt.hasNext()){
			appBarNotification.setImageViewBitmap(resIt.next(), iconIt.next());
		}
	}
	
	private void prepareButtonClickListeners(AppBarNotification appBarNotification) {
		Iterator<Integer> resIt = buttonIntents.keySet().iterator();
		
		while (resIt.hasNext()){
			Integer resId = resIt.next();
			appBarNotification.setOnClickPendingIntent(resId, PendingIntent.getBroadcast(getApplicationContext(), resId, new Intent().putExtra(ACTIVITY_ID_EXTRA, resId).setAction(START_ACTIVITY_INTENT), 0));
		}
	}

	private List<Bitmap> getFiveRecentTaskBitmapsAndUpdateButtonIntents(List<RecentTaskInfo> recentTasks) {
		List<Bitmap> fiveRecentTaskBitmaps = new LinkedList<Bitmap>();
		buttonIntents.clear();
		
        ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).resolveActivityInfo(pm, 0);
		
		int i = 0;
		for (RecentTaskInfo rti: recentTasks){
			if (i > 4){
				return fiveRecentTaskBitmaps;
			}
			
			Intent baseIntent = new Intent(rti.baseIntent);
			
			if (baseIntent.getAction() == null) {
				continue;
			}
			
			if (baseIntent != null){
				if (rti.origActivity != null){
					baseIntent.setComponent(rti.origActivity);
				}
				if (homeInfo != null) {
	                if (homeInfo.packageName.equals(baseIntent.getComponent().getPackageName())
	                        && homeInfo.name.equals(baseIntent.getComponent().getClassName())) {
	                    continue;
	                }
	            }
				baseIntent.setFlags((baseIntent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
	                    | Intent.FLAG_ACTIVITY_NEW_TASK);
				baseIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
				
				buttonIntents.put(resIds.get(i), baseIntent);
				
				Bitmap icon;
				try {
					icon = ((BitmapDrawable) pm.getApplicationIcon(rti.baseIntent.getComponent().getPackageName())).getBitmap();
					fiveRecentTaskBitmaps.add(icon);
					i++;
				} catch (NameNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		return fiveRecentTaskBitmaps;
	}

	private void reloadNotification() {
		updateNotificationLock.lock();
		Log.v("AppBar", "Reloading running app list...");
		
		List<RecentTaskInfo> recentTasks = am.getRecentTasks(50, 0x0002);
		List<Bitmap> fiveRecentTasksBitmaps = getFiveRecentTaskBitmapsAndUpdateButtonIntents(recentTasks);
		
		AppBarNotification appBarNotification = new AppBarNotification(getPackageName(), R.layout.app_bar_notification);
		prepareButtonBitmaps(appBarNotification, fiveRecentTasksBitmaps);
		prepareButtonClickListeners(appBarNotification);
		
		Notification notification = new Notification();
		notification.contentView = appBarNotification;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;  
		notification.flags |= Notification.FLAG_NO_CLEAR;
		
		Intent notificationIntent = new Intent();
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.contentIntent = contentIntent;
		notification.icon = R.drawable.ic_stat_notify_icon;
		
		if (firstNotificationTime == -1) {
			firstNotificationTime = System.currentTimeMillis();
		}
		notification.when = firstNotificationTime;
		
		//TODO: no icon
		nm.notify(APP_BAR_NOTIFICATION, notification);
		updateNotificationLock.unlock();
	}
	
	public void collapseStatusBar() {
		try {
			Object service = this.getSystemService("statusbar"); 
			Class <?> statusBarManager = Class.forName("android.app.StatusBarManager"); 
			Method collapse = statusBarManager.getMethod("collapse"); 
			collapse.invoke (service); 
		} catch (Exception e) {
			e.printStackTrace();
		} 
	}
	
	public void startActivity(int appId) {
		updateNotificationLock.lock();
		if (appId != -1) {
			startActivity(buttonIntents.get(appId));
			collapseStatusBar();
		}
		updateNotificationLock.unlock();
	}
	
}
