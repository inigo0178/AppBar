package de.ubuntudroid.appbar;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

import android.app.ActivityManager;
import android.app.ActivityManager.RecentTaskInfo;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.IBinder;
import android.util.Log;
import de.ubuntudroid.appbar.helpers.SystemPackages;

public class AppBarService extends Service {

	private static final int APP_BAR_NOTIFICATION = 0;
	private NotificationManager nm;
	private ActivityManager am;
	private PackageManager pm;
	
	private final Vector<Integer> resIds = new Vector<Integer>();

	@Override
	public IBinder onBind(Intent intent) {
		/* not implemented as other Activities/Apps should not be able to connect to this service
		 * (except for the configurator?)
		 */
		return null;
	}
	
	@Override
	public void onCreate() {
		nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
		pm = (PackageManager) getPackageManager();
		
		resIds.add(R.id.app1);
		resIds.add(R.id.app2);
		resIds.add(R.id.app3);
		resIds.add(R.id.app4);
		resIds.add(R.id.app5);
		//this.setForeground(true);
		super.onCreate();
	}
	
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				while(true){
					reloadNotification();
					try {
						Thread.sleep(3000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		}).start();
	}

	private void prepareButtonBitmaps(AppBarNotification appBarNotification, Collection<Bitmap> appIcons) {
//		int apps = appIcons.size();
//		if (apps == 0){
//			return;
//		}
		
		Iterator<Bitmap> iconIt = appIcons.iterator();
		Iterator<Integer> resIt = resIds.iterator();
		while (iconIt.hasNext()){
			appBarNotification.setImageViewBitmap(resIt.next(), iconIt.next());
		}
	}
	
	private void prepareButtonClickListeners(AppBarNotification appBarNotification, Collection<PendingIntent> appPendingIntents) {
//		int apps = appPendingIntents.size();
//		if (apps == 0){
//			return;
//		}
		
		Iterator<PendingIntent> piIt = appPendingIntents.iterator();
		Iterator<Integer> resIt = resIds.iterator();
		while (piIt.hasNext()){
			appBarNotification.setOnClickPendingIntent(resIt.next(), piIt.next());
		}
	}

	private Map<PendingIntent, Bitmap> getFiveRecentTasks(List<RecentTaskInfo> recentTasks) {
		Map<PendingIntent, Bitmap> fiveRecentTasks = new LinkedHashMap<PendingIntent, Bitmap>();
		
        ActivityInfo homeInfo = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).resolveActivityInfo(pm, 0);
		
		int i = 0;
		for (RecentTaskInfo rti: recentTasks){
			if (i > 4){
				return fiveRecentTasks;
			}
			
			Intent baseIntent = new Intent(rti.baseIntent);
			if (baseIntent != null){
//				Set<String> categories = baseIntent.getCategories();
//				if ((categories != null && categories.contains(Intent.CATEGORY_LAUNCHER)) || 
//						checkForSystemApp(baseIntent)){
//					fiveRecentTasks.add(rti);
//					i++;
//				}
				if (rti.origActivity != null){
					baseIntent.setComponent(rti.origActivity);
				}
				if (homeInfo != null) {
	                if (homeInfo.packageName.equals(
	                        baseIntent.getComponent().getPackageName())
	                        && homeInfo.name.equals(
	                                baseIntent.getComponent().getClassName())) {
	                    continue;
	                }
	            }
				baseIntent.setFlags((baseIntent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
	                    | Intent.FLAG_ACTIVITY_NEW_TASK);
				baseIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
				PendingIntent pi = PendingIntent.getActivity(getApplicationContext(), 0, baseIntent, 0);
				
				Bitmap icon;
				try {
					icon = ((BitmapDrawable) pm.getApplicationIcon(rti.baseIntent.getComponent().getPackageName())).getBitmap();
					fiveRecentTasks.put(pi, icon);
					i++;
				} catch (NameNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}
		}
		return fiveRecentTasks;
	}

	private boolean checkForSystemApp(Intent intent) {
		if (intent.getComponent() == null){
			return false;
		}
		
		return SystemPackages.isSystemPackage(intent.getComponent().getPackageName());
	}

	private List<Bitmap> getRecentTaskIcons(List<RecentTaskInfo> recentTasks) {
		List<Bitmap> icons = new ArrayList<Bitmap>();
		for (RecentTaskInfo rti : recentTasks){
			try {
				icons.add(((BitmapDrawable) getPackageManager().getApplicationIcon(rti.baseIntent.getComponent().getPackageName())).getBitmap());
			} catch (NameNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return icons;
	}

//	private List<PendingIntent> getRecentTaskPendingIntents(Set<Intent> recentTasks) {
//		List<PendingIntent> appPendingIntents = new ArrayList<PendingIntent>();
//		int i = 0;
//		for (RecentTaskInfo rti : recentTasks){
//			Intent baseIntent = rti.baseIntent;
//			//TODO: don't restart app but just bring it to front
////			baseIntent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
////			baseIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
//			baseIntent.setFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
//			appPendingIntents.add(PendingIntent.getActivity(getApplicationContext(), 0, baseIntent, 0));
//			i++;
//		}
//		return appPendingIntents;
//	}
	
	private void reloadNotification() {
		Log.v("AppBar", "Reloading running app list...");
		
		List<RecentTaskInfo> recentTasks = am.getRecentTasks(50, 0x0002);
		Map<PendingIntent, Bitmap> fiveRecentTasks = getFiveRecentTasks(recentTasks);
//		List<Bitmap> appIcons = getRecentTaskIcons(fiveRecentTasks);
//		List<PendingIntent> appPendingIntents = getRecentTaskPendingIntents(fiveRecentTasks.keySet());
		
		AppBarNotification appBarNotification = new AppBarNotification(getPackageName(), R.layout.app_bar_notification);
		prepareButtonBitmaps(appBarNotification, fiveRecentTasks.values());
		prepareButtonClickListeners(appBarNotification, fiveRecentTasks.keySet());
		
		Notification notification = new Notification();
		notification.contentView = appBarNotification;
		notification.flags |= Notification.FLAG_ONGOING_EVENT;  
		notification.flags |= Notification.FLAG_NO_CLEAR;
		
		Intent notificationIntent = new Intent();
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);
		notification.contentIntent = contentIntent;
		notification.icon = R.drawable.icon;
		//TODO: no icon
		nm.notify(APP_BAR_NOTIFICATION, notification);
	}
}
