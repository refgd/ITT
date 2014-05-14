package com.itt.app;

import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;


public class MainActivity extends ActionBarActivity {

    private LocationManager locationManager;
    private TextView infoOut;
    private TextView infoSend;
    private TextView infoRecv;

    private String provider;

    private String imei;

    private String TAG = "socket thread";
    
    SocThread socketThread;

    Handler mhandler;
    Handler mhandlerSend;
    private Context ctx;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = MainActivity.this;

        infoOut = (TextView) findViewById(R.id.infoTxt);
        infoSend = (TextView) findViewById(R.id.sendTxt);
        infoRecv = (TextView) findViewById(R.id.respTxt);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        TelephonyManager mngr = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        imei = mngr.getDeviceId();

        boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // check if enabled and if not send user to the GSP settings
        // Better solution would be to display a dialog and suggesting to
        // go to the settings
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        // Define the criteria how to select the locatioin provider -> use
        // default
        Criteria criteria = new Criteria();
        provider = locationManager.getBestProvider(criteria, false);
        Location location = locationManager.getLastKnownLocation(provider);

        // Initialize the location fields
        if (location != null) {
            updateLocation(location);

            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, locationListener);

        } else {
            infoOut.setText("Location not available");
        }

        mhandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                try {
                    Log.i(TAG, "mhandler: msg=" + msg.what);
                    if (msg.obj != null) {
                        String s = msg.obj.toString();
                        if (s.trim().length() > 0) {
                            Log.i(TAG, "mhandler: obj=" + s);
                            infoRecv.setText("Server:" + s);
                        } else {
                            Log.i(TAG, "no data");
                        }
                    }
                } catch (Exception ee) {
                    Log.i(TAG, "loading error");
                    ee.printStackTrace();
                }
            }
        };
        mhandlerSend = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                try {
                    Log.i(TAG, "mhandlerSend: msg.what=" + msg.what);
                    String s = msg.obj.toString();
                    if (msg.what == 1) {
                        Log.i(TAG, "ME: " + s + "\nsend success");
                    } else {
                        Log.i(TAG, "ME: " + s + "\nsend fail");
                    }
                } catch (Exception ee) {
                    Log.i(TAG, "loading error");
                    ee.printStackTrace();
                }
            }
        };
        startSocket();

        Button btnsend = (Button) findViewById(R.id.btn_send);

        btnsend.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(final View v) {
                //Log.i(TAG, "Sending data");

                String str = infoSend.getText().toString();

                socketThread.Send(str);

            }
        });

    }

    public void startSocket() {
        socketThread = new SocThread(mhandler, mhandlerSend, ctx);
        socketThread.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final LocationListener locationListener = new LocationListener() {

        public void onLocationChanged(Location location) {

            updateLocation(location);

        }

        public void onProviderDisabled(String provider){

            updateLocation(null);

            Log.i(TAG, "Provider now is disabled..");

        }

        public void onProviderEnabled(String provider){

            Log.i(TAG, "Provider now is enabled..");

        }

        public void onStatusChanged(String provider, int status,Bundle extras){ }

    };


    public void updateLocation(Location location) {
        Double lati = location.getLatitude();
        Double loit = location.getLongitude();
        SimpleDateFormat s = new SimpleDateFormat("yyMMddHHmmss");
        String ms = s.format(new Date());
        Log.i("TIME", ms);
        String outData = imei+",AAA,35,"+lati+","+loit+","+ms+",A,10,11,0,217,1.1,37,36118,846208,310|260|7DA1|8B2B,0000,000A|0002||02D6|00FE,*A7\r\n";
        String pData = "$$g" + outData.length() + "," + outData;
        infoSend.setText(pData);

        String str = "Latitude: "+lati+"\nLongitude: "+loit+"\nimei: "+imei;
        infoOut.setText(str);
    }
}
