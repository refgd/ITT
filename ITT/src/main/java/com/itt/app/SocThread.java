package com.itt.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class SocThread extends Thread {
    private String ip = "50.195.116.150";
    //private String ip = "131.125.78.63";
    private int port = 8500;
    private String TAG = "socket thread";
    private int timeout = 10000;

    public Socket client = null;
    PrintWriter out;
    BufferedReader in;
    public boolean isRun = true;
    Handler inHandler;
    Handler outHandler;
    Handler connHandler;
    Context ctx;
    private String TAG1 = "===Send===";
    SharedPreferences sp;

    public SocThread(Handler handlerin, Handler handlerout, Handler handlerconn, Context context) {
        inHandler = handlerin;
        outHandler = handlerout;
        connHandler = handlerconn;
        ctx = context;
        Log.i(TAG, "create socket thread");
    }

    /**
     * 连接socket服务器
     */
    public void conn() {

        Message msg = connHandler.obtainMessage();
        try {
            initdate();
            Log.i(TAG, "connect……");
            client = new Socket(ip, port);
            client.setSoTimeout(timeout);
            Log.i(TAG, "connect success");
            in = new BufferedReader(new InputStreamReader(
                    client.getInputStream()));
            out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(
                    client.getOutputStream())), true);
            Log.i(TAG, "Output stream created ");
            msg.obj = "connect success";
            msg.what = 1;
            connHandler.sendMessage(msg);
        } catch (UnknownHostException e) {
            Log.i(TAG, "Connect error: UnknownHostException");
            e.printStackTrace();
            msg.obj = "Connect error: UnknownHostException";
            msg.what = 1;
            connHandler.sendMessage(msg);
            conn();
        } catch (IOException e) {
            Log.i(TAG, "Connect io error");

            msg.obj = "Connect io error";
            msg.what = 1;
            connHandler.sendMessage(msg);
            e.printStackTrace();
        } catch (Exception e) {
            Log.i(TAG, "Connect error Exception" + e.getMessage());

            msg.obj = "Connect error Exception" + e.getMessage();
            msg.what = 1;
            connHandler.sendMessage(msg);
            e.printStackTrace();
        }
    }

    public void initdate() {
        sp = ctx.getSharedPreferences("SP", ctx.MODE_PRIVATE);
        ip = sp.getString("ipstr", ip);
        port = Integer.parseInt(sp.getString("port", String.valueOf(port)));

        Log.i(TAG, "get ip port:" + ip + ";" + port);
    }

    /**
     * get data
     */
    @Override
    public void run() {
        Log.i(TAG, "socket start");
        conn();
        Log.i(TAG, "1.run");
        String line = "";
        while (isRun) {
            try {
                if (client != null) {
                    Log.i(TAG, "2.check data");
                    while ((line = in.readLine()) != null) {
                        Log.i(TAG, "3.getdata" + line + " len=" + line.length());
                        Log.i(TAG, "4.start set Message");
                        Message msg = inHandler.obtainMessage();
                        msg.obj = line;
                        inHandler.sendMessage(msg);
                        Log.i(TAG1, "5.send to handler");
                    }

                } else {
                    Log.i(TAG, "no connection");
                    conn();
                }
            } catch (Exception e) {
                Log.i(TAG, "receive data error: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * send data
     *
     * @param mess
     */
    public void Send(String mess) {
        try {
            if (client != null) {
                Log.i(TAG1, "send to " + mess
                        + client.getInetAddress().getHostAddress() + ":"
                        + String.valueOf(client.getPort()));
                out.println(mess);
                out.flush();
                Log.i(TAG1, "send success");
                Message msg = outHandler.obtainMessage();
                msg.obj = mess;
                msg.what = 1;
                outHandler.sendMessage(msg);
            } else {
                Log.i(TAG, "client not exist");
                Message msg = outHandler.obtainMessage();
                msg.obj = mess;
                msg.what = 0;
                outHandler.sendMessage(msg);
                Log.i(TAG, "connect not exist");
                conn();
            }

        } catch (Exception e) {
            Log.i(TAG1, "send error");
            e.printStackTrace();
        } finally {
            Log.i(TAG1, "send done");

        }
    }

    /**
     * close connect
     */
    public void close() {
        try {
            if (client != null) {
                Log.i(TAG, "close in");
                in.close();
                Log.i(TAG, "close out");
                out.close();
                Log.i(TAG, "close client");
                client.close();
            }
        } catch (Exception e) {
            Log.i(TAG, "close err");
            e.printStackTrace();
        }

    }
}