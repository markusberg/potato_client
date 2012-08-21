package nu.kelvin.potato;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Map;
import java.util.TreeMap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

@SuppressWarnings("deprecation")
public class MainActivity extends Activity {
	private TextView otpView;
	private EditText enterPin;
	private Button buttonOk;
	private ProgressBar progressBar;
	private Spinner profileSpinner;

	private CountDownTimer timeout;
	private long timeCountDownStart;

	private TreeMap<String, String> profileTree;
	private int profileSelected;

	SecureRandom prng;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		// Log.i("onCreate", "Create main activity");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		otpView = (TextView) findViewById(R.id.otpView);
		enterPin = (EditText) findViewById(R.id.enterPin);
		buttonOk = (Button) findViewById(R.id.buttonOk);
		progressBar = (ProgressBar) findViewById(R.id.progressBar);
		profileSpinner = (Spinner) findViewById(R.id.profileSelector);
		timeCountDownStart = 0L;

		loadPreferences();
		populateProfileSpinner();
		addProfileSpinnerListener();
		addPinListener();
		updateUtcView();
	}

	@Override
	public void onStop() {
		super.onStop();
		// Log.i("onStop", "App has been stopped");
	}

	@Override
	public void onPause() {
		super.onPause();
		// Log.i("onPause", "App has been sent to background");
	}

	@Override
	public void onResume() {
		super.onResume();
		// Log.i("onResume", "The app is coming back");
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		// Save whatever we need to persist
		// Log.i("onSaveInstanceState", "save pin and otp, etc.");
		outState.putString("pin", enterPin.getText().toString());
		outState.putString("otp", otpView.getText().toString());
		outState.putLong("timeCountDownStart", timeCountDownStart);
	}

	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		// Log.i("onRestoreInstanceState", "restore pin and otp, etc.");
		// Retrieve the data stored onSaveInstanceState
		timeCountDownStart = savedInstanceState.getLong("timeCountDownStart");

		if (timeCountDownStart != 0L) {
			enterPin.setText(savedInstanceState.getString("pin"));
			otpView.setText(savedInstanceState.getString("otp"));
			otpView.setVisibility(View.VISIBLE);
			countDownStart(timeCountDownStart);
		}
	}

	/**
	 * Commenting this out. Doesn't update the action bar...
	 * 
	 * @Override public boolean onPrepareOptionsMenu(Menu menu) { MenuItem
	 *           actionDelete = menu.findItem(R.id.action_delete);
	 *           actionDelete.setEnabled(!profileTree.isEmpty());
	 * 
	 *           super.onPrepareOptionsMenu(menu); return true; }
	 */

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	/**
	 * Handle menu selections, present dialog boxes in response to menu
	 * selections, etc.
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle item selection
		AlertDialog.Builder builder;
		AlertDialog alert;

		switch (item.getItemId()) {
		case R.id.action_add:
			dialogAddProfile();
			return true;

		case R.id.action_delete:
			// Log.i("action_delete", "delete current profile");
			if (profileTree.isEmpty()) {
				return false;
			}
			builder = new AlertDialog.Builder(this);
			builder.setTitle("Delete profile")
					.setMessage("Are you sure you want to delete this profile?")
					.setCancelable(true)
					.setPositiveButton("Delete",
							new DialogInterface.OnClickListener() {
								// click listener on the alert box
								public void onClick(DialogInterface dialog,
										int id) {
									// The button was clicked
									// Remove the currently selected profile
									profileTree.remove((String) profileSpinner
											.getSelectedItem());
									saveProfiles();
									clearSensitiveData();
									populateProfileSpinner();
								}
							});
			alert = builder.create();
			alert.show();
			return true;

		case R.id.action_about:
			builder = new AlertDialog.Builder(this);

			final SpannableString textAbout = new SpannableString(
					"Potato client is an mOTP (mobile One-Time-Password) client app for Android devices.\nhttp://kelvin.nu/software/potato\nhttp://motp.sf.net/");
			Linkify.addLinks(textAbout, Linkify.WEB_URLS);
			builder.setTitle("About").setMessage(textAbout);
			alert = builder.create();
			alert.show();
			return true;
		default:
			return true;
		}
	}

	/**
	 * Define and open the add profile dialog
	 */
	public void dialogAddProfile() {
		initRNG();

		final Dialog dialog = new Dialog(this);
		dialog.setContentView(R.layout.add_profile);
		dialog.setTitle("Add profile");

		// Generate secret
		final byte[] randomBytes = new byte[8];
		prng.nextBytes(randomBytes);

		// Display secret in ui
		TextView secret = (TextView) dialog.findViewById(R.id.profile_secret);
		secret.setText("Secret: \n" + readable(toHex(randomBytes)));

		// Get the profileName TextEdit
		final EditText profileName = (EditText) dialog
				.findViewById(R.id.profile_enter_name);

		// Add listener to TextEdit to ensure that the selected name is unique
		final Button buttonSave = (Button) dialog
				.findViewById(R.id.profile_save);
		profileName.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				/**
				 * checking profileTree.isEmpty() to work around a bug in older
				 * versions of Android (<3) where enterPin.setEnabled(false) has
				 * no effect.
				 */
				if (profileName.getText().toString() != ""
						&& profileTree.get((String) profileName.getText()
								.toString()) == null) {
					buttonSave.setEnabled(true);
				} else {
					buttonSave.setEnabled(false);
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// TODO Auto-generated method stub
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				// TODO Auto-generated method stub
			}
		});

		buttonSave.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				// Perform action on click
				String name = profileName.getText().toString();
				profileTree.put(name, toHex(randomBytes));
				profileSelected = profileTree.headMap(name).size();
				clearSensitiveData();

				populateProfileSpinner();
				saveProfiles();
				dialog.dismiss();
			}
		});

		dialog.show();
	}

	/**
	 * Initialize the prng SecureRandom variable if it has not already been
	 * initialized
	 */
	public void initRNG() {
		// This operation is supposedly expensive, so we only init prng if the
		// user wants to create a new profile.
		if (prng == null) {
			try {
				prng = SecureRandom.getInstance("SHA1PRNG");
			} catch (NoSuchAlgorithmException e) {
				// ignore
			}
		}
	}

	/**
	 * Listen to changes in the profileSpinner
	 */
	public void addProfileSpinnerListener() {
		// Log.i("addProfileSpinnerListener",
		// "Add listener to profile spinner");
		profileSpinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parentView, View view,
					int pos, long id) {
				// Log.i("onItemSelectedListener", "Profile " +
				// Integer.toString(pos) + " was selected");
				// This is triggered a lot, but not always when a new item
				// has been selected. Need to check for that:
				if (pos != profileSelected) {
					// The selected profile really has changed
					profileSelected = pos;
					saveProfileSelected();
					clearSensitiveData();
				}
			}

			public void onNothingSelected(AdapterView<?> parentView) {
				// Log.i("onNothingSelected", "No profile was selected");
				// enterPin.setEnabled(false);
			}
		});
	}

	/**
	 * Set a listener on the enterPin EditText to only enable the Ok-button once
	 * a four-digit PIN has been entered.
	 */
	public void addPinListener() {
		enterPin.addTextChangedListener(new TextWatcher() {
			public void afterTextChanged(Editable s) {
				/**
				 * checking profileTree.isEmpty() to work around a bug in older
				 * versions of Android where enterPin.enable(false) has no
				 * effect.
				 */
				if (enterPin.getText().toString().length() == 4
						&& !profileTree.isEmpty()) {
					buttonOk.setEnabled(true);
				} else {
					buttonOk.setEnabled(false);
				}
			}

			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
				// TODO Auto-generated method stub
			}

			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				// TODO Auto-generated method stub
			}
		});
	}

	/**
	 * Fetch profiles and the selected profile from saved prefs
	 */
	public void loadPreferences() {
		// Log.i("loadPreferences", "Loading preferences from disk");
		SharedPreferences preferences = getSharedPreferences("Profiles",
				MODE_PRIVATE);
		profileTree = new TreeMap<String, String>();
		Map<String, ?> profileDump = preferences.getAll();

		for (Map.Entry<String, ?> entry : profileDump.entrySet()) {
			profileTree.put(entry.getKey(), (String) entry.getValue());
		}

		/**
		 * temporary hard-coded profiles if there's nothing stored
		 * 
		 * if (profileTree.isEmpty()) { profileTree.put("Earth",
		 * "b0bbf8eb606254dd"); profileTree.put("Mercury", "b0bbf8eb606254db");
		 * profileTree.put("Venus", "b0bbf8eb606254dc"); }
		 */

		// Open the main app preferences
		preferences = getSharedPreferences("Main", MODE_PRIVATE);
		profileSelected = preferences.getInt("profileSelected", 0);
	}

	/**
	 * Save profiles to prefs
	 */
	public void saveProfiles() {
		SharedPreferences preferences = getSharedPreferences("Profiles",
				MODE_PRIVATE);

		SharedPreferences.Editor prefEditor = preferences.edit();
		prefEditor.clear();
		for (String k : profileTree.keySet()) {
			prefEditor.putString(k, profileTree.get(k));
		}
		prefEditor.commit();
	}

	/**
	 * Populate the profileSpinner, and select the correct profile
	 */
	public void populateProfileSpinner() {
		// Log.i("populateProfileSpinner", "Add all profiles to spinner");

		ArrayList<String> profileList;
		if (profileTree.isEmpty()) {
			profileList = new ArrayList<String>();
			profileList.add("(no profile)");
		} else {
			profileList = new ArrayList<String>(profileTree.keySet());
		}
		ArrayAdapter<String> aa = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, new ArrayList<String>(
						profileList));

		// Specify the layout to use when the list of choices appears
		aa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner
		profileSpinner.setAdapter(aa);

		// Log.i("setSelectedProfile", "Set the currently selected profile");
		profileSelected = (profileSelected == profileTree.size() ? profileSelected - 1
				: profileSelected);
		profileSelected = (profileSelected < 0 ? 0 : profileSelected);

		// Disable pin-entry, delete-button, and profilespinner
		// if there are no profiles
		if (profileTree.isEmpty()) {
			enterPin.setEnabled(false);
			profileSpinner.setEnabled(false);
		} else {
			profileSpinner.setSelection(profileSelected);
			enterPin.setEnabled(true);
			profileSpinner.setEnabled(true);
		}
	}

	/**
	 * Save the currently selected profile to prefs
	 */
	public void saveProfileSelected() {
		SharedPreferences profileSettings = getSharedPreferences("Main",
				MODE_PRIVATE);
		SharedPreferences.Editor prefEditor = profileSettings.edit();
		prefEditor.putInt("profileSelected", profileSelected);
		prefEditor.commit();
	}

	/**
	 * Copy the current one-time-password to the clipboard. This is a callback
	 * for onclick on the password TextView.
	 * 
	 * @param view
	 */
	@SuppressLint("NewApi")
	public void copyToClipboard(View view) {
		// Gets a handle to the clipboard service.
		ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
		cm.setText(otpView.getText().toString());
		Toast.makeText(this, "One-time-password copied to clipboard",
				Toast.LENGTH_SHORT).show();
	}

	/**
	 * Perform all the necessary motp calculations called when the user clicks
	 * the Ok button
	 **/
	public void generateOtp(View view) {
		String pin = enterPin.getText().toString();
		Calendar now = Calendar.getInstance();

		String selection = (String) profileSpinner.getSelectedItem();
		String secret = (String) profileTree.get(selection);

		String epoch = Long.toString(now.getTimeInMillis());
		epoch = epoch.substring(0, epoch.length() - 4);

		String hash = md5(epoch + secret + pin);
		String otp = hash.substring(0, 6);

		otpView.setText(otp);
		otpView.setVisibility(View.VISIBLE);
		countDownStart(0L);
	}

	/**
	 * Start the countdown timer after the otp has been generated. When the
	 * timer runs down, all sensitive fields are cleared.
	 */
	public void countDownStart(long timeStart) {
		// Log.i("countDownStart", "Start the countdown.");
		try {
			timeout.cancel();
		} catch (NullPointerException e) {
			// ignore
		}

		timeCountDownStart = Calendar.getInstance().getTimeInMillis();
		int secondsLeft = 60;
		if (timeStart != 0L) {
			// Resume the timer, likely after a screen rotate.
			// Adjust values accordingly
			secondsLeft = (int) (60 - (timeCountDownStart - timeStart) / 1000);
			timeCountDownStart = timeStart;
		}
		progressBar.setProgress(secondsLeft * 2);

		timeout = new CountDownTimer(secondsLeft * 1000, 500) {
			public void onTick(long millisUntilFinished) {
				progressBar.setProgress((int) millisUntilFinished / 500);
			}

			public void onFinish() {
				// Log.i("onFinish", "Countdown timer has finished");
				clearSensitiveData();
			}
		}.start();

		progressBar.setVisibility(View.VISIBLE);
	}

	/**
	 * Clear all fields of sensitive data.
	 */
	public void clearSensitiveData() {
		// Log.i("clearSensitiveData",
		// "wipe pin, current otp, countdownbar, etc.");
		enterPin.setText("");
		otpView.setText("");
		otpView.setVisibility(View.INVISIBLE);
		progressBar.setVisibility(View.INVISIBLE);
		timeCountDownStart = 0L;
		try {
			timeout.cancel();
		} catch (NullPointerException e) {
			// ignore
		}
	}

	/**
	 * Update the display of our current UTC offset i.e. UTC+1 or UTC-1:30
	 */
	public void updateUtcView() {
		TextView utcView = (TextView) findViewById(R.id.utcView);
		Calendar now = Calendar.getInstance();
		Integer offsetMinutes = (now.get(Calendar.ZONE_OFFSET) + now
				.get(Calendar.DST_OFFSET)) / 60000;
		String offsetPrefix = offsetMinutes < 0 ? "-" : "+";
		offsetMinutes = Math.abs(offsetMinutes);

		if (offsetMinutes % 60 == 0) {
			utcView.setText(String.format("UTC%s%d", offsetPrefix,
					offsetMinutes / 60));
		} else {
			utcView.setText(String.format("UTC%s%d:%d", offsetPrefix,
					offsetMinutes / 60, offsetMinutes % 60));
		}
	}

	/**
	 * Take a String and return a hex
	 * 
	 * @param s
	 *            String of bytes to be converted
	 * @return Simple hex-encoded string, for example: a0e23b
	 */
	public static String md5(String s) {
		try {
			MessageDigest md = MessageDigest.getInstance("MD5");
			md.update(s.getBytes(), 0, s.length());
			return toHex(md.digest());
		} catch (NoSuchAlgorithmException e) {
			e.printStackTrace();
		}
		return "";
	}

	/**
	 * Convert a byte array to a hex-string. For md5 strings for example
	 * 
	 * @param hashValue
	 *            Input bytearray
	 * @return ascii hexcode representation of input
	 */
	public static String toHex(byte[] hashValue) {
		StringBuilder hexString = new StringBuilder();
		for (int i = 0; i < hashValue.length; i++) {
			String hex = Integer.toHexString(0xFF & hashValue[i]);
			if (hex.length() == 1) {
				hexString.append('0');
			}
			hexString.append(hex);
		}
		return hexString.toString();
	}

	/**
	 * Increase readability of String by inserting spaces every 4 characters
	 * 
	 * @param unreadable
	 *            String that needs formatting
	 */
	public static String readable(String unreadable) {
		String s = "";

		for (int i = 0; i < unreadable.length(); i += 4) {
			s += unreadable.substring(i, i + 4) + " ";
		}
		return s;
	}

}
