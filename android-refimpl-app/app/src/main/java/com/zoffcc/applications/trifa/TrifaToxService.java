/**
 * [TRIfA], Java part of Tox Reference Implementation for Android
 * Copyright (C) 2017 Zoff <zoff@zoff.cc>
 * <p>
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin Street, Fifth Floor,
 * Boston, MA  02110-1301, USA.
 */

package com.zoffcc.applications.trifa;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.RemoteViews;

import java.util.List;

import info.guardianproject.iocipher.VirtualFileSystem;

import static com.zoffcc.applications.trifa.MainActivity.add_friend_real;
import static com.zoffcc.applications.trifa.MainActivity.cache_fnum_pubkey;
import static com.zoffcc.applications.trifa.MainActivity.cache_pubkey_fnum;
import static com.zoffcc.applications.trifa.MainActivity.change_notification;
import static com.zoffcc.applications.trifa.MainActivity.get_my_toxid;
import static com.zoffcc.applications.trifa.MainActivity.notification_view;
import static com.zoffcc.applications.trifa.MainActivity.set_all_friends_offline;
import static com.zoffcc.applications.trifa.MainActivity.tox_friend_get_connection_status;
import static com.zoffcc.applications.trifa.MainActivity.tox_friend_get_public_key__wrapper;
import static com.zoffcc.applications.trifa.MainActivity.tox_self_get_name;
import static com.zoffcc.applications.trifa.MainActivity.tox_self_get_name_size;
import static com.zoffcc.applications.trifa.MainActivity.tox_self_get_status_message;
import static com.zoffcc.applications.trifa.MainActivity.tox_self_get_status_message_size;
import static com.zoffcc.applications.trifa.MainActivity.tox_self_set_name;
import static com.zoffcc.applications.trifa.MainActivity.tox_self_set_status_message;
import static com.zoffcc.applications.trifa.TRIFAGlobals.ADD_BOTS_ON_STARTUP;
import static com.zoffcc.applications.trifa.TRIFAGlobals.ECHOBOT_TOXID;
import static com.zoffcc.applications.trifa.TRIFAGlobals.GROUPBOT_TOXID;
import static com.zoffcc.applications.trifa.TRIFAGlobals.global_my_name;
import static com.zoffcc.applications.trifa.TRIFAGlobals.global_my_status_message;
import static com.zoffcc.applications.trifa.TRIFAGlobals.global_my_toxid;

public class TrifaToxService extends Service
{
    static int ONGOING_NOTIFICATION_ID = 1030;
    static final String TAG = "trifa.ToxService";
    Notification notification2 = null;
    NotificationManager nmn2 = null;
    static Thread ToxServiceThread = null;
    static boolean stop_me = false;
    static OrmaDatabase orma = null;
    static VirtualFileSystem vfs = null;
    static boolean is_tox_started = false;
    static boolean global_toxid_text_set = false;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        Log.i(TAG, "onStartCommand");
        // this gets called all the time!
        MainActivity.tox_service_fg = this;
        return START_STICKY;
    }

    @Override
    public void onCreate()
    {
        Log.i(TAG, "onCreate");
        // serivce is created ---
        start_me();
        super.onCreate();
    }

    void change_notification_fg(int a_TOXCONNECTION)
    {
        Log.i(TAG, "change_notification_fg");

        NotificationCompat.Builder b = new NotificationCompat.Builder(this);
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        if (a_TOXCONNECTION == 0)
        {
            notification_view.setImageViewResource(R.id.image, R.drawable.circle_red);
            b.setSmallIcon(R.drawable.circle_red);
            notification_view.setTextViewText(R.id.title, "Tox Service: " + "OFFLINE");
        }
        else
        {
            if (a_TOXCONNECTION == 1)
            {
                notification_view.setImageViewResource(R.id.image, R.drawable.circle_green);
                b.setSmallIcon(R.drawable.circle_green);
                notification_view.setTextViewText(R.id.title, "Tox Service: " + "ONLINE [TCP]");
            }
            else // if (a_TOXCONNECTION__f == 2)
            {
                notification_view.setImageViewResource(R.id.image, R.drawable.circle_green);
                b.setSmallIcon(R.drawable.circle_green);
                notification_view.setTextViewText(R.id.title, "Tox Service: " + "ONLINE [UDP]");
            }
        }
        notification_view.setTextViewText(R.id.text, "");

        b.setContentIntent(pendingIntent);
        b.setContent(notification_view);
        notification2 = b.build();
        nmn2.notify(ONGOING_NOTIFICATION_ID, notification2);
    }


    void stop_me(boolean exit_app)
    {
        Log.i(TAG, "stop_me");
        stopSelf();
        if (exit_app)
        {
            try
            {
                Log.i(TAG, "stop_me:001");
                Thread t = new Thread()
                {
                    @Override
                    public void run()
                    {
                        Log.i(TAG, "stop_me:002");
                        long i = 0;
                        while (is_tox_started)
                        {
                            i++;
                            if (i > 40)
                            {
                                break;
                            }

                            Log.i(TAG, "stop_me:003");

                            try
                            {
                                Thread.sleep(150);
                            }
                            catch (Exception e)
                            {
                                e.printStackTrace();
                            }
                        }

                        try
                        {
                            // orma.unmount();
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }

                        try
                        {
                            if (vfs.isMounted())
                            {
                                vfs.unmount();
                            }
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }

                        Log.i(TAG, "stop_me:004");

                        try
                        {
                            Thread.sleep(500);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }

                        Log.i(TAG, "stop_me:005");

                        nmn2.cancel(ONGOING_NOTIFICATION_ID);
                        MainActivity.exit();

                        Log.i(TAG, "stop_me:099");
                    }
                };
                t.start();
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
    }

    void stop_tox_fg()
    {
        Log.i(TAG, "stop_tox_fg");
        Runnable myRunnable = new Runnable()
        {
            @Override
            public void run()
            {
                stop_me = true;
                ToxServiceThread.interrupt();
                try
                {
                    ToxServiceThread.join();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                stop_me = false; // reset flag again!
                change_notification(0); // set to offline
                set_all_friends_offline();
                is_tox_started = false;

                // nmn2.cancel(ONGOING_NOTIFICATION_ID); // -> cant remove notification because of foreground service
                // ** // MainActivity.exit();
            }
        };
        MainActivity.main_handler_s.post(myRunnable);
    }

    void tox_thread_start_fg()
    {
        Log.i(TAG, "tox_thread_start_fg");

        ToxServiceThread = new Thread()
        {
            @Override
            public void run()
            {
                // ------ correct startup order ------
                boolean old_is_tox_started = is_tox_started;
                Log.i(TAG, "is_tox_started:==============================");
                Log.i(TAG, "is_tox_started=" + is_tox_started);
                Log.i(TAG, "is_tox_started:==============================");

                is_tox_started = true;

                Runnable myRunnable = new Runnable()
                {
                    @Override
                    public void run()
                    {
                        if (!global_toxid_text_set)
                        {
                            global_toxid_text_set = true;
                            MainActivity.mt.setText(MainActivity.mt.getText() + "\n" + "my_ToxId=" + get_my_toxid());
                        }
                    }
                };
                MainActivity.main_handler_s.post(myRunnable);

                if (!old_is_tox_started)
                {
                    MainActivity.init_tox_callbacks();
                    MainActivity.update_savedata_file();
                }
                // ------ correct startup order ------

                cache_pubkey_fnum.clear();
                cache_fnum_pubkey.clear();

                // TODO --------
                String my_tox_id_local = get_my_toxid();
                global_my_toxid = my_tox_id_local;
                if (tox_self_get_name_size() > 0)
                {
                    global_my_name = tox_self_get_name().substring(0, (int) tox_self_get_name_size());
                    Log.i(TAG, "AAA:003:" + global_my_name + " size=" + tox_self_get_name_size());
                }
                else
                {
                    tox_self_set_name("TRIfA " + my_tox_id_local.substring(my_tox_id_local.length() - 5, my_tox_id_local.length()));
                    global_my_name = ("TRIfA " + my_tox_id_local.substring(my_tox_id_local.length() - 5, my_tox_id_local.length()));
                    Log.i(TAG, "AAA:005");
                }

                if (tox_self_get_status_message_size() > 0)
                {
                    global_my_status_message = tox_self_get_status_message().substring(0, (int) tox_self_get_status_message_size());
                    Log.i(TAG, "AAA:008:" + global_my_status_message + " size=" + tox_self_get_status_message_size());
                }
                else
                {
                    tox_self_set_status_message("this is TRIfA");
                    global_my_status_message = "this is TRIfA";
                    Log.i(TAG, "AAA:010");
                }
                Log.i(TAG, "AAA:011");

                MainActivity.update_savedata_file();

                // TODO --------

                MainActivity.friends = MainActivity.tox_self_get_friend_list();
                Log.i(TAG, "loading_friend:number_of_friends=" + MainActivity.friends.length);

                int fc = 0;
                boolean exists_in_db = false;
                MainActivity.friend_list_fragment.clear_friends();
                for (fc = 0; fc < MainActivity.friends.length; fc++)
                {
                    Log.i(TAG, "loading_friend:" + fc + " friendnum=" + MainActivity.friends[fc]);
                    Log.i(TAG, "loading_friend:" + fc + " pubkey=" + tox_friend_get_public_key__wrapper(MainActivity.friends[fc]));

                    FriendList f;
                    List<FriendList> fl = orma.selectFromFriendList().tox_public_key_stringEq(tox_friend_get_public_key__wrapper(MainActivity.friends[fc])).toList();

                    Log.i(TAG, "loading_friend:" + fc + " db entry size=" + fl);

                    if (fl.size() > 0)
                    {
                        f = fl.get(0);
                        Log.i(TAG, "loading_friend:" + fc + " db entry=" + f);
                    }
                    else
                    {
                        f = null;
                    }

                    if (f == null)
                    {
                        Log.i(TAG, "loading_friend:c is null");

                        f = new FriendList();
                        f.tox_public_key_string = "" + (long) ((Math.random() * 10000000d));
                        try
                        {
                            f.tox_public_key_string = tox_friend_get_public_key__wrapper(MainActivity.friends[fc]);
                        }
                        catch (Exception e)
                        {
                            e.printStackTrace();
                        }
                        f.name = "friend #" + fc;
                        exists_in_db = false;
                        Log.i(TAG, "loading_friend:c is null fnew=" + f);
                    }
                    else
                    {
                        Log.i(TAG, "loading_friend:found friend in DB " + f.tox_public_key_string + " f=" + f);
                        exists_in_db = true;
                    }

                    try
                    {
                        // get the real "live" connection status of this friend
                        // the value in the database may be old (and wrong)
                        f.TOX_CONNECTION = tox_friend_get_connection_status(MainActivity.friends[fc]);
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }

                    if (MainActivity.friend_list_fragment != null)
                    {
                        MainActivity.friend_list_fragment.add_friends(f);
                    }

                    if (exists_in_db == false)
                    {
                        Log.i(TAG, "loading_friend:1:insertIntoFriendList:" + " f=" + f);
                        orma.insertIntoFriendList(f);
                        Log.i(TAG, "loading_friend:2:insertIntoFriendList:" + " f=" + f);
                    }
                    else
                    {
                        Log.i(TAG, "loading_friend:1:updateFriendList:" + " f=" + f);
                        orma.updateFriendList().tox_public_key_stringEq(tox_friend_get_public_key__wrapper(MainActivity.friends[fc])).name(f.name).status_message(f.status_message).TOX_CONNECTION(f.TOX_CONNECTION).TOX_USER_STATUS(f.TOX_USER_STATUS).execute();
                        Log.i(TAG, "loading_friend:1:updateFriendList:" + " f=" + f);
                    }

                    FriendList f_check;
                    List<FriendList> fl_check = orma.selectFromFriendList().tox_public_key_stringEq(tox_friend_get_public_key__wrapper(MainActivity.friends[fc])).toList();
                    Log.i(TAG, "loading_friend:check:" + " db entry=" + fl_check);
                    try
                    {
                        Log.i(TAG, "loading_friend:check:" + " db entry=" + fl_check.get(0));
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                        Log.i(TAG, "loading_friend:check:EE:" + e.getMessage());
                    }

                }

                // --------------- bootstrap ---------------
                // --------------- bootstrap ---------------
                // --------------- bootstrap ---------------
                if (!old_is_tox_started)
                {
                    MainActivity.bootstrap();
                }
                // --------------- bootstrap ---------------
                // --------------- bootstrap ---------------
                // --------------- bootstrap ---------------

                long tox_iteration_interval_ms = MainActivity.tox_iteration_interval();
                Log.i(TAG, "tox_iteration_interval_ms=" + tox_iteration_interval_ms);

                MainActivity.tox_iterate();

                if (ADD_BOTS_ON_STARTUP)
                {
                    boolean need_add_bots = true;
                    List<TRIFADatabaseGlobals> dbg = null;
                    try
                    {
                        dbg = orma.selectFromTRIFADatabaseGlobals().keyEq("ADD_BOTS_ON_STARTUP_done").toList();
                    }
                    catch (Exception e)
                    {
                        dbg = null;
                    }

                    if (dbg != null)
                    {
                        if (dbg.size() > 0)
                        {
                            if (dbg.get(0).value.equals("true"))
                            {
                                need_add_bots = false;
                                Log.i(TAG, "need_add_bots=false");
                            }
                        }
                    }

                    if (need_add_bots)
                    {
                        Log.i(TAG, "need_add_bots:start");
                        add_friend_real(ECHOBOT_TOXID);
                        add_friend_real(GROUPBOT_TOXID);
                        try
                        {
                            TRIFADatabaseGlobals g_opts = new TRIFADatabaseGlobals();
                            g_opts.key = "ADD_BOTS_ON_STARTUP_done";
                            g_opts.value = "true";
                            orma.insertIntoTRIFADatabaseGlobals(g_opts);
                            Log.i(TAG, "need_add_bots=true (INSERT)");
                        }
                        catch (android.database.sqlite.SQLiteConstraintException e)
                        {
                            e.printStackTrace();
                            try
                            {
                                orma.updateTRIFADatabaseGlobals().keyEq("ADD_BOTS_ON_STARTUP_done").value("true").execute();
                                Log.i(TAG, "need_add_bots=true (UPDATE)");
                            }
                            catch (Exception e2)
                            {
                                e2.printStackTrace();
                            }
                        }
                    }
                }


                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                while (!stop_me)
                {
                    try
                    {
                        if (tox_iteration_interval_ms < 10)
                        {
                            Log.i(TAG, "(tox_iteration_interval_ms < 10ms!!):" + tox_iteration_interval_ms + "ms");
                            Thread.sleep(10);
                        }
                        else
                        {
                            // Log.i(TAG, "(tox_iteration_interval_ms):" + tox_iteration_interval_ms + "ms");
                            Thread.sleep(tox_iteration_interval_ms);
                        }
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }
                    MainActivity.tox_iterate();

                }
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------
                // ------- MAIN TOX LOOP ---------------------------------------------------------------


                try
                {
                    Thread.sleep(100); // wait a bit, for "something" to finish up in the native code
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                try
                {
                    MainActivity.tox_kill();
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

                try
                {
                    Thread.sleep(100); // wait a bit, for "something" to finish up in the native code
                }
                catch (Exception e)
                {
                    e.printStackTrace();
                }

            }
        };

        ToxServiceThread.start();
    }

    void start_me()
    {
        Log.i(TAG, "start_me");
        Intent notificationIntent = new Intent(this, MainActivity.class);
        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

        // -- notification ------------------
        // -- notification ------------------
        nmn2 = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        notification_view = new RemoteViews(getPackageName(), R.layout.custom_notification);
        Log.i(TAG, "contentView=" + notification_view);
        notification_view.setImageViewResource(R.id.image, R.drawable.circle_red);
        notification_view.setTextViewText(R.id.title, "Tox Service: " + "OFFLINE");
        notification_view.setTextViewText(R.id.text, "");

        NotificationCompat.Builder b = new NotificationCompat.Builder(this);
        b.setContent(notification_view);
        b.setContentIntent(pendingIntent);
        b.setSmallIcon(R.drawable.circle_red);
        notification2 = b.build();
        // -- notification ------------------
        // -- notification ------------------

        startForeground(ONGOING_NOTIFICATION_ID, notification2);
    }

    @Override
    public boolean onUnbind(Intent intent)
    {
        Log.i(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void unbindService(ServiceConnection conn)
    {
        Log.i(TAG, "unbindService");
        super.unbindService(conn);
    }

    @Override
    public void onDestroy()
    {
        Log.i(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        Log.i(TAG, "onBind");
        return null;
    }

    // ------------------------------

}
