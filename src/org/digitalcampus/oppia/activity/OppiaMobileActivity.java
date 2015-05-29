/* 
 * This file is part of OppiaMobile - http://oppia-mobile.org/
 * 
 * OppiaMobile is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * OppiaMobile is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with OppiaMobile. If not, see <http://www.gnu.org/licenses/>.
 */

package org.digitalcampus.oppia.activity;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.Callable;

import org.bright.future.oppia.mobile.learning.R;
import org.digitalcampus.oppia.adapter.CourseListAdapter;
import org.digitalcampus.oppia.adapter.SectionListAdapter;
import org.digitalcampus.oppia.application.DatabaseManager;
import org.digitalcampus.oppia.application.DbHelper;
import org.digitalcampus.oppia.application.MobileLearning;
import org.digitalcampus.oppia.exception.InvalidXMLException;
import org.digitalcampus.oppia.listener.ScanMediaListener;
import org.digitalcampus.oppia.model.Activity;
import org.digitalcampus.oppia.model.Course;
import org.digitalcampus.oppia.model.Lang;
import org.digitalcampus.oppia.task.Payload;
import org.digitalcampus.oppia.task.ScanMediaTask;
import org.digitalcampus.oppia.utils.CourseXMLReader;
import org.digitalcampus.oppia.utils.FileUtils;
import org.digitalcampus.oppia.utils.UIUtils;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;

public class OppiaMobileActivity extends AppActivity implements OnSharedPreferenceChangeListener, ScanMediaListener {

	public static final String TAG = OppiaMobileActivity.class.getSimpleName();
	private SharedPreferences prefs;
	private ArrayList<Course> courses;
	private Course tempCourse;
	private long userId = 0;
	private int ujjwalComponent = 0;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.activity_main);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.registerOnSharedPreferenceChangeListener(this);
		PreferenceManager.setDefaultValues(this, R.xml.prefs, false);
		
		// set preferred lang to the default lang
		if (prefs.getString("prefLanguage", "").equals("")) {
			Editor editor = prefs.edit();
			editor.putString("prefLanguage", Locale.getDefault().getLanguage());
			editor.commit();
		}
		
		Bundle bundle = this.getIntent().getExtras();
		if (bundle != null) {
			ujjwalComponent = bundle.getInt(MobileLearning.UJJWAL_COMPONENT_TAG);
		}
	}

	@Override
	public void onStart() {
		super.onStart();
		DbHelper db = new DbHelper(this);
		userId = db.getUserId(prefs.getString("prefUsername", ""));
        courses = db.getAllCourses();
		DatabaseManager.getInstance().closeDatabase();
		displayCourses(userId);		
	}

	@Override
	public void onResume(){
		super.onResume();
		this.updateReminders();
	}
	
	@Override
	public void onPause(){
		super.onPause();
	}
	
	private void displayCourses(long userId) {

		DbHelper db = new DbHelper(this);
		if (ujjwalComponent == MobileLearning.CLIENT_COUNSELLING_COMPONENT){
			Log.d(TAG,"in counselling");
			courses = db.getCounsellingCourses(userId);
		} else if (ujjwalComponent == MobileLearning.MOBILE_LEARNING_COMPONENT){
			Log.d(TAG,"in learning");
			courses = db.getLearningCourses(userId);
		} else {
            courses = new ArrayList<Course>();
			Log.d(TAG,"in nothing");
		}
		DatabaseManager.getInstance().closeDatabase();
		
		LinearLayout llLoading = (LinearLayout) this.findViewById(R.id.loading_courses);
		llLoading.setVisibility(View.GONE);
		LinearLayout llNone = (LinearLayout) this.findViewById(R.id.no_courses);
		
		if (courses.size() == 0){
			llNone.setVisibility(View.VISIBLE);
			Button manageBtn = (Button) this.findViewById(R.id.manage_courses_btn);
			manageBtn.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					startActivity(new Intent(OppiaMobileActivity.this, TagSelectActivity.class));
				}
			});
		} else if (courses.size() < MobileLearning.DOWNLOAD_COURSES_DISPLAY) {
			llNone.setVisibility(View.VISIBLE);
			TextView tv = (TextView) this.findViewById(R.id.manage_courses_text);
			tv.setText(R.string.more_courses);
			Button manageBtn = (Button) this.findViewById(R.id.manage_courses_btn);
			manageBtn.setOnClickListener(new View.OnClickListener() {
				public void onClick(View v) {
					startActivity(new Intent(OppiaMobileActivity.this, TagSelectActivity.class));
				}
			});
		} else {
			TextView tv = (TextView) this.findViewById(R.id.manage_courses_text);
			tv.setText(R.string.no_courses);
			llNone.setVisibility(View.GONE);
		}

		CourseListAdapter mla = new CourseListAdapter(this, courses);
		ListView listView = (ListView) findViewById(R.id.course_list);
		listView.setAdapter(mla);
		registerForContextMenu(listView);

		listView.setOnItemClickListener(new OnItemClickListener() {

			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				Course c = (Course) view.getTag();
				Bundle tb;
				Intent i;
				try {
					CourseXMLReader cxr;
					cxr = new CourseXMLReader(c.getCourseXMLLocation(), OppiaMobileActivity.this);
					ArrayList<Activity> activities = cxr.getBaselineActivities(c.getCourseId());
					if(activities.size() >0){
						i = new Intent(OppiaMobileActivity.this, CourseActivity.class);
						tb = new Bundle();
						tb.putSerializable(Course.TAG, c);
						tb.putSerializable(Activity.TAG,activities);
						tb.putBoolean(CourseActivity.BASELINE_TAG, true);
					} else {
						i = new Intent(OppiaMobileActivity.this, CourseIndexActivity.class);
						tb = new Bundle();
						tb.putSerializable(Course.TAG, c);
						tb.putBoolean(CourseActivity.BASELINE_TAG, false);
					}
					tb.putSerializable(SectionListAdapter.TAG_PLACEHOLDER,0);
					i.putExtras(tb);
					startActivity(i);
				} catch (InvalidXMLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				
			}
		});

		this.updateReminders();
		
		// scan media
		this.scanMedia();
	}

	private void updateReminders(){
		if(prefs.getBoolean("prefShowScheduleReminders", false)){
			DbHelper db = new DbHelper(OppiaMobileActivity.this);
			int max = Integer.valueOf(prefs.getString("prefNoScheduleReminders", "2"));
			long userId = db.getUserId(prefs.getString("prefUsername", ""));
			ArrayList<Activity> activities = db.getActivitiesDue(max, userId);
			DatabaseManager.getInstance().closeDatabase();

			this.drawReminders(activities);
		} else {
			LinearLayout ll = (LinearLayout) findViewById(R.id.schedule_reminders);
			ll.setVisibility(View.GONE);
		}		
	}
	
	private void scanMedia() {
		long now = System.currentTimeMillis()/1000;
		if (prefs.getLong("prefLastMediaScan", 0)+3600 > now) {
			LinearLayout ll = (LinearLayout) this.findViewById(R.id.home_messages);
			ll.setVisibility(View.GONE);
			return;
		}
		ScanMediaTask task = new ScanMediaTask(this);
		Payload p = new Payload(this.courses);
		task.setScanMediaListener(this);
		task.execute(p);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getSupportMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		UIUtils.showUserData(menu,this);
	    return super.onPrepareOptionsMenu(menu);
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		Log.d(TAG,"selected:" + item.getItemId());
		switch (item.getItemId()) {
			case R.id.menu_about:
				startActivity(new Intent(this, AboutActivity.class));
				return true;
			case R.id.menu_download:
				startActivity(new Intent(this, TagSelectActivity.class));
				return true;
			case R.id.menu_settings:
				Intent i = new Intent(this, PrefsActivity.class);
				Bundle tb = new Bundle();
				ArrayList<Lang> langs = new ArrayList<Lang>();
				for(Course m: courses){
					langs.addAll(m.getLangs());
				}
				tb.putSerializable("langs", langs);
				i.putExtras(tb);
				startActivity(i);
				return true;
			case R.id.menu_language:
				createLanguageDialog();
				return true;
			case R.id.menu_search:
				startActivity(new Intent(this, SearchActivity.class));
				return true;
			case R.id.menu_logout:
				logout();
				return true;
		}
		return true;
	}

	private void createLanguageDialog() {
		ArrayList<Lang> langs = new ArrayList<Lang>();
		for(Course m: courses){
			langs.addAll(m.getLangs());
		}
		
		UIUtils ui = new UIUtils();
    	ui.createLanguageDialog(this, langs, prefs, new Callable<Boolean>() {	
			public Boolean call() throws Exception {
				OppiaMobileActivity.this.onStart();
				return true;
			}
		});
	}
	
	private void logout() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false);
		builder.setTitle(R.string.logout);
		builder.setMessage(R.string.logout_confirm);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// wipe user prefs
				Editor editor = prefs.edit();
				editor.putString("prefUsername", "");
				editor.putString("prefApiKey", "");
				editor.putInt("prefBadges", 0);
				editor.putInt("prefPoints", 0);
                editor.putLong("lastClientSync", 0L);
                editor.commit();

				// restart the app
				OppiaMobileActivity.this.startActivity(new Intent(OppiaMobileActivity.this, StartUpActivity.class));
				OppiaMobileActivity.this.finish();

			}
		});
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				return; // do nothing
			}
		});
		builder.show();
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		getMenuInflater().inflate(R.menu.course_context_menu, menu);
	}

	@Override
	public boolean onContextItemSelected(android.view.MenuItem item) {
		android.widget.AdapterView.AdapterContextMenuInfo info = (android.widget.AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
		tempCourse = (Course) info.targetView.getTag();
		switch (item.getItemId()) {
			case R.id.course_context_delete:
				confirmCourseDelete();
				return true;
			case R.id.course_context_reset:
				confirmCourseReset();
				return true;
		}
		return true;
	}

	private void confirmCourseDelete() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false);
		builder.setTitle(R.string.course_context_delete);
		builder.setMessage(R.string.course_context_delete_confirm);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				// remove db records
				DbHelper db = new DbHelper(OppiaMobileActivity.this);
				db.deleteCourse(tempCourse.getCourseId());
				DatabaseManager.getInstance().closeDatabase();

				// remove files
				File f = new File(tempCourse.getLocation());
				FileUtils.deleteDir(f);
				Editor e = prefs.edit();
				e.putLong("prefLastMediaScan", 0);
				e.commit();
				displayCourses(userId);
			}
		});
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				tempCourse = null;
			}
		});
		builder.show();
	}

	private void confirmCourseReset() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setCancelable(false);
		builder.setTitle(R.string.course_context_reset);
		builder.setMessage(R.string.course_context_reset_confirm);
		builder.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				DbHelper db = new DbHelper(OppiaMobileActivity.this);
				long userId = db.getUserId(prefs.getString("prefUsername", ""));
				db.resetCourse(tempCourse.getCourseId(),userId);
				DatabaseManager.getInstance().closeDatabase();
				displayCourses(userId);
			}
		});
		builder.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
			public void onClick(DialogInterface dialog, int which) {
				tempCourse = null;
			}
		});
		builder.show();
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if(key.equalsIgnoreCase("prefServer")){
			Editor editor = sharedPreferences.edit();
			if(!sharedPreferences.getString("prefServer", "").endsWith("/")){
				String newServer = sharedPreferences.getString("prefServer", "").trim()+"/";
				editor.putString("prefServer", newServer);
		    	editor.commit();
			}
		}
		if(key.equalsIgnoreCase("prefShowScheduleReminders") || key.equalsIgnoreCase("prefNoScheduleReminders")){
			displayCourses(userId);
		}
		if(key.equalsIgnoreCase("prefPoints")
				|| key.equalsIgnoreCase("prefBadges")){
			supportInvalidateOptionsMenu();
		}
	}

	public void scanStart() {
		TextView tv = (TextView) this.findViewById(R.id.home_message);
		tv.setText(this.getString(R.string.info_scan_media_start));
	}

	public void scanProgressUpdate(String msg) {
		TextView tv = (TextView) this.findViewById(R.id.home_message);
		tv.setText(this.getString(R.string.info_scan_media_checking, msg));
	}

	public void scanComplete(Payload response) {
		Editor e = prefs.edit();
		LinearLayout ll = (LinearLayout) this.findViewById(R.id.home_messages);
		TextView tv = (TextView) this.findViewById(R.id.home_message);
		Button btn = (Button) this.findViewById(R.id.message_action_button);
		
		if (response.getResponseData().size() > 0) {
			ll.setVisibility(View.VISIBLE);
			tv.setText(this.getString(R.string.info_scan_media_missing));
			btn.setText(this.getString(R.string.scan_media_download_button));
			btn.setTag(response.getResponseData());
			btn.setOnClickListener(new OnClickListener() {

				public void onClick(View view) {
					@SuppressWarnings("unchecked")
					ArrayList<Object> m = (ArrayList<Object>) view.getTag();
					Intent i = new Intent(OppiaMobileActivity.this, DownloadMediaActivity.class);
					Bundle tb = new Bundle();
					tb.putSerializable(DownloadMediaActivity.TAG, m);
					i.putExtras(tb);
					startActivity(i);
				}
			});
			e.putLong("prefLastMediaScan", 0);
			e.commit();
		} else {
			ll.setVisibility(View.GONE);
			tv.setText("");
			btn.setText("");
			btn.setOnClickListener(null);
			btn.setTag(null);
			long now = System.currentTimeMillis()/1000;
			e.putLong("prefLastMediaScan", now);
			e.commit();
		}
	}
}