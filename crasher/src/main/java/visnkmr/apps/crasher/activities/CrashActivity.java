package visnkmr.apps.crasher.activities;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
//import android.support.annotation.Nullable;
//import android.support.v4.content.ContextCompat;
//import android.support.v7.app.ActionBar;
//import android.support.v7.app.AppCompatActivity;
//import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.OutputStream;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

//import visnkmr.apps.buttons.Button;
//import visnkmr.apps.crasher.BuildConfig;
import visnkmr.apps.crasher.R;
//import visnkmr.apps.crasher.utils.ColorUtils;
import visnkmr.apps.crasher.utils.CrashUtils;

public class CrashActivity extends Activity implements View.OnClickListener {

    public static final String EXTRA_NAME = "visnkmr.apps.crasher.EXTRA_NAME";
    public static final String EXTRA_MESSAGE = "visnkmr.apps.crasher.EXTRA_MESSAGE";
    public static final String EXTRA_STACK_TRACE = "visnkmr.apps.crasher.EXTRA_STACK_TRACE";

    public static final String EXTRA_EMAIL = "visnkmr.apps.crasher.EXTRA_EMAIL";
    public static final String EXTRA_DEBUG_MESSAGE = "visnkmr.apps.crasher.EXTRA_DEBUG_MESSAGE";
    public static final String EXTRA_COLOR = "visnkmr.apps.crasher.EXTRA_COLOR";

//    private Toolbar toolbar;
//    private ActionBar actionBar;
    private TextView name;
    private TextView message;
    private TextView description;
    private Button copy;
    private Button share;
    private Button email;
    private Button submitCrash;
    private View stackTraceHeader;
    private ImageView stackTraceArrow;
    private TextView stackTrace;

    private String body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_crash);

//        toolbar = findViewById(R.id.toolbar);
        name = findViewById(R.id.name);
        message = findViewById(R.id.message);
        description = findViewById(R.id.description);

        copy = findViewById(R.id.copy);
        copy.setOnClickListener(this);

        share = findViewById(R.id.share);
        share.setOnClickListener(this);

        email = findViewById(R.id.email);
        submitCrash = findViewById(R.id.submit_crash);
        // submitCrash.setVisibility(View.VISIBLE);
        submitCrash.setOnClickListener(this);
        stackTraceHeader = findViewById(R.id.stackTraceHeader);
        stackTraceArrow = findViewById(R.id.stackTraceArrow);
        stackTrace = findViewById(R.id.stackTrace);

//        setSupportActionBar(toolbar);
//        actionBar = getSupportActionBar();

//        int color = getIntent().getIntExtra(EXTRA_COLOR, ContextCompat.getColor(this, R.color.colorPrimary));
//        int colorDark = ColorUtils.darkColor(color);
//        boolean isColorDark = ColorUtils.isColorDark(color);

//        toolbar.setBackgroundColor(color);
//        toolbar.setTitleTextColor(isColorDark ? Color.WHITE : Color.BLACK);

//        copy.setBackgroundColor(colorDark);
//        copy.setTextColor(colorDark);
//        copy.setOnClickListener(this);

//        share.setBackgroundColor(colorDark);
//        share.setTextColor(colorDark);
//        share.setOnClickListener(this);

//        email.setBackgroundColor(colorDark);
//        email.setTextColor(colorDark);
        if (getIntent().hasExtra(EXTRA_EMAIL)) {
            email.setOnClickListener(this);
        } else email.setVisibility(View.GONE);

        /*if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(String.format(Locale.getDefault(), getString(R.string.title_crasher_crashed), getString(R.string.app_name)));
            actionBar.setHomeAsUpIndicator(ImageUtils.getVectorDrawable(this, R.drawable.ic_crasher_back, isColorDark ? Color.WHITE : Color.BLACK));
        }*/

       /* if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
             getWindow().setStatusBarColor(colorDark);
             getWindow().setNavigationBarColor(colorDark);
         }*/

        String stack = getIntent().getStringExtra(EXTRA_STACK_TRACE);
        stackTrace.setText(stack);
        stackTrace.setVisibility(View.VISIBLE);
        String stackCause = CrashUtils.getCause(this, stack);

        String nameString = getIntent().getStringExtra(EXTRA_NAME) + (stackCause != null ? " at " + stackCause : "");
        String messageString = getIntent().getStringExtra(EXTRA_NAME);

        name.setText(nameString);
        if (messageString != null && messageString.length() > 0)
            message.setText(messageString);
        else message.setVisibility(View.GONE);

        description.setText(String.format(Locale.getDefault(), getString(R.string.msg_crasher_crashed), getString(R.string.app_name)));

        stackTrace.setText(stack);
        // Auto-submit crash log after activity loads
        submitCrashLog(stack);
        stackTraceHeader.setOnClickListener(this);
        /*if (BuildConfig.DEBUG)
            stackTraceHeader.callOnClick();*/

        body = nameString + "\n" + (messageString != null ? messageString : "") + "\n\n" + stack
                + "\n\nAndroid Version: " + Build.VERSION.SDK_INT
                + "\nDevice Manufacturer: " + Build.MANUFACTURER
                + "\nDevice Model: " + Build.MODEL
                + "\n\n" + (getIntent().hasExtra(EXTRA_DEBUG_MESSAGE) ? getIntent().getStringExtra(EXTRA_DEBUG_MESSAGE) : "");
    }

    private void submitCrashLog(String stackTraceText) {
        new Thread(() -> {
            try {
                JSONObject jsonParam = new JSONObject();
                jsonParam.put("stack_trace", stackTraceText);
                jsonParam.put("model", android.os.Build.MODEL);
                jsonParam.put("oemname", android.os.Build.MANUFACTURER);
                jsonParam.put("osapilevel", String.valueOf(android.os.Build.VERSION.SDK_INT));

                java.net.URL url = new java.net.URL("https://visssample.theworkpc.com/v2/crash");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(10000);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = jsonParam.toString().getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                runOnUiThread(() -> {
                    if (responseCode == 200) {
                        Toast.makeText(CrashActivity.this, "Crash log submitted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(CrashActivity.this, "Failed to submit crash log", Toast.LENGTH_SHORT).show();
                    }
                });
                conn.disconnect();
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(CrashActivity.this, "Error submitting crash log", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            finishAffinity();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.copy) {
            Toast.makeText(CrashActivity.this,"Text copied to clipboard.",Toast.LENGTH_LONG).show();
            Object service = getSystemService(CLIPBOARD_SERVICE);
            if (service instanceof android.content.ClipboardManager)
                ((android.content.ClipboardManager) service).setPrimaryClip(ClipData.newPlainText(name.getText().toString(), stackTrace.getText().toString()));
            else if (service instanceof android.text.ClipboardManager)
                ((android.text.ClipboardManager) service).setText(stackTrace.getText().toString());
        } else if (v.getId() == R.id.share) {
            Intent sharingIntent = new Intent(Intent.ACTION_SEND);
            sharingIntent.setType("text/plain");
            sharingIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Subject Here");
            sharingIntent.putExtra(android.content.Intent.EXTRA_TEXT, body);
            startActivity(Intent.createChooser(sharingIntent, "Share text via"));

            /*Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, body);

            startActivity(Intent.createChooser(intent, getString(R.string.title_crasher_share)));*/
        } else if (v.getId() == R.id.email) {

            Intent intent = new Intent(Intent.ACTION_SENDTO);
            intent.setType("text/plain");
            intent.setData(Uri.parse("mailto:" + getIntent().getStringExtra(EXTRA_EMAIL)));
            intent.putExtra(Intent.EXTRA_EMAIL, getIntent().getStringExtra(EXTRA_EMAIL));
            intent.putExtra(Intent.EXTRA_SUBJECT, String.format(Locale.getDefault(), getString(R.string.title_crasher_exception), name.getText().toString(), getString(R.string.app_name)));
            intent.putExtra(Intent.EXTRA_TEXT, body);

            startActivity(Intent.createChooser(intent, getString(R.string.title_crasher_send_email)));
        } else if (v.getId() == R.id.submit_crash) {
            // Submit crash log manually
            submitCrashLog(stackTrace.getText().toString());
        } else if (v.getId() == R.id.stackTraceHeader) {
            if (stackTrace.getVisibility() == View.GONE) {
                stackTrace.setVisibility(View.VISIBLE);
                stackTraceArrow.animate().scaleY(-1).start();
            } else {
                stackTrace.setVisibility(View.GONE);
                stackTraceArrow.animate().scaleY(1).start();
            }
        }
    }
}