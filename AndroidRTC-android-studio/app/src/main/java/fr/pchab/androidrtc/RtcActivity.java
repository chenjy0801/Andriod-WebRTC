package fr.pchab.androidrtc;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.view.WindowManager.LayoutParams;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.Toast;
import org.json.JSONException;
import org.webrtc.MediaStream;
import org.webrtc.VideoRenderer;
import org.webrtc.VideoRendererGui;
import fr.pchab.webrtcclient.WebRtcClient;
import fr.pchab.webrtcclient.PeerConnectionParameters;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RtcActivity extends Activity implements WebRtcClient.RtcListener, AdapterView.OnItemClickListener{
    private final static int VIDEO_CALL_SENT = 666;
    private static final String VIDEO_CODEC_VP9 = "VP9";
    private static final String AUDIO_CODEC_OPUS = "opus";
    // Local preview screen position before call is connected.
    private static final int LOCAL_X_CONNECTING = 0;
    private static final int LOCAL_Y_CONNECTING = 0;
    private static final int LOCAL_WIDTH_CONNECTING = 100;
    private static final int LOCAL_HEIGHT_CONNECTING = 100;
    // Local preview screen position after call is connected.
    private static final int LOCAL_X_CONNECTED = 72;
    private static final int LOCAL_Y_CONNECTED = 72;
    private static final int LOCAL_WIDTH_CONNECTED = 25;
    private static final int LOCAL_HEIGHT_CONNECTED = 25;
    // Remote video screen position
    private static final int REMOTE_X = 0;
    private static final int REMOTE_Y = 0;
    private static final int REMOTE_WIDTH = 100;
    private static final int REMOTE_HEIGHT = 100;
    private VideoRendererGui.ScalingType scalingType = VideoRendererGui.ScalingType.SCALE_ASPECT_FILL;
    private GLSurfaceView vsv;
    private VideoRenderer.Callbacks localRender;
    private VideoRenderer.Callbacks remoteRender;
    private WebRtcClient client;
    private String mSocketAddress;
    private String callerId;


    private ListView itemlist = null;
    private List<Map<String, Object>> list;
    private SimpleAdapter adapter;

    Handler myHandler = new Handler()
    {
        public void handleMessage(android.os.Message msg)
        {
            if (msg.what == 123) {
                String callId = msg.getData().getString("CALLID");
                Map<String, Object> map = new HashMap<String, Object>();
                map.put("ID", callId);
                //判断ID是否存在
                if(list.contains(map)) {
                    list.remove(map);
                } else {
                    list.add(map);
                }
                adapter.notifyDataSetChanged();//当有消息时刷新
            }

            if (msg.what == 456) {
                List<Map<String, Object>> listtemp = (List<Map<String, Object>>) msg.obj;
                for(int i = 0; i < listtemp.size(); i++) {
                    list.add(listtemp.get(i));
                }
                adapter.notifyDataSetChanged();//当有消息时刷新
            }
        };
    };


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                LayoutParams.FLAG_FULLSCREEN
                        | LayoutParams.FLAG_KEEP_SCREEN_ON
                        | LayoutParams.FLAG_DISMISS_KEYGUARD
                        | LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | LayoutParams.FLAG_TURN_SCREEN_ON);
        setContentView(R.layout.main);
        mSocketAddress = "http://" + getResources().getString(R.string.host);
        mSocketAddress += (":" + getResources().getString(R.string.port) + "/");


        itemlist = (ListView) findViewById(R.id.list);

        vsv = (GLSurfaceView) findViewById(R.id.glview_call);
        vsv.setPreserveEGLContextOnPause(true);
        vsv.setKeepScreenOn(true);
        VideoRendererGui.setView(vsv, new Runnable() {
            @Override
            public void run() {
                init();
            }
        });

        list = new ArrayList<Map<String, Object>>(2);
        //初始化适配器
        adapter = new SimpleAdapter(this, list, R.layout.item, new String[] {"ID"}, new int[] {R.id.tvId});
        itemlist.setAdapter(adapter);//设置适配器
        itemlist.setSelection(0);
        itemlist.setOnItemClickListener(this);


        // local and remote render
        remoteRender = VideoRendererGui.create(
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType, false);
        localRender = VideoRendererGui.create(
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING, scalingType, true);

        final Intent intent = getIntent();
        final String action = intent.getAction();

        if (Intent.ACTION_VIEW.equals(action)) {
            final List<String> segments = intent.getData().getPathSegments();
            callerId = segments.get(0);
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String iditem = list.get(position).get("ID").toString();
        try {
            answer(iditem);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    private void init() {
        Point displaySize = new Point();
        getWindowManager().getDefaultDisplay().getSize(displaySize);
        PeerConnectionParameters params = new PeerConnectionParameters(
                true, false, displaySize.x, displaySize.y, 30, 1, VIDEO_CODEC_VP9, true, 1, AUDIO_CODEC_OPUS, true);

        client = new WebRtcClient(this, mSocketAddress, params, VideoRendererGui.getEGLContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        vsv.onPause();
        if(client != null) {
            client.onPause();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        vsv.onResume();
        if(client != null) {
            client.onResume();
        }
    }

    @Override
    public void onDestroy() {
        if(client != null) {
            client.onDestroy();
        }
        super.onDestroy();
    }

    //当有新的主播刚加入或者刚断开通话时调用
    @Override
    public void onAddList(String callId) {
        Bundle bundle = new Bundle();
        bundle.putString("CALLID", callId);
        Message msg = new Message();
        msg.setData(bundle);
        msg.what = 123;
        myHandler.sendMessage(msg);
    }

    //获取正在直播且不在通话的主播列表
    @Override
    public void onInitList(List<Map<String, Object>> list) {
        Message msg = new Message();
        msg.obj = list;
        msg.what = 456;
        myHandler.sendMessage(msg);
    }


    //一开始
    @Override
    public void onCallReady() {
        client.start("android_testtt");
    }

    public void answer(String callerId) throws JSONException {
        client.sendMessage(callerId, "init", null);
        client.startCall("android_testtt", callerId);
    }

    @Override
    public void onStatusChanged(final String newStatus) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(getApplicationContext(), newStatus, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public void onLocalStream(MediaStream localStream) {
        localStream.videoTracks.get(0).addRenderer(new VideoRenderer(localRender));
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }

    @Override
    public void onAddRemoteStream(MediaStream remoteStream, int endPoint) {
        remoteStream.videoTracks.get(0).addRenderer(new VideoRenderer(remoteRender));
        VideoRendererGui.update(remoteRender,
                REMOTE_X, REMOTE_Y,
                REMOTE_WIDTH, REMOTE_HEIGHT, scalingType);
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTED, LOCAL_Y_CONNECTED,
                LOCAL_WIDTH_CONNECTED, LOCAL_HEIGHT_CONNECTED,
                scalingType);
    }

    @Override
    public void onRemoveRemoteStream(int endPoint) {
        VideoRendererGui.update(localRender,
                LOCAL_X_CONNECTING, LOCAL_Y_CONNECTING,
                LOCAL_WIDTH_CONNECTING, LOCAL_HEIGHT_CONNECTING,
                scalingType);
    }


}