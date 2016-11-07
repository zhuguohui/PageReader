package com.zhuguohui.pagereader;

import android.content.Context;
import android.graphics.Color;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;

import com.iflytek.cloud.SpeechConstant;
import com.iflytek.cloud.SpeechError;
import com.iflytek.cloud.SpeechSynthesizer;
import com.iflytek.cloud.SynthesizerListener;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    Button btn_read;
    TextView tv_content;
    ScrollView scroll_news;
    String content;
    private SpeechSynthesizer mTts;
    SpannableStringBuilder styled;
    PowerManager.WakeLock mWakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        btn_read = (Button) findViewById(R.id.btn_read);
        tv_content = (TextView) findViewById(R.id.tv_content);
        scroll_news = (ScrollView) findViewById(R.id.sroll_news);
        content = tv_content.getText().toString();

        spanRed = new ForegroundColorSpan(Color.RED);
        styled = new SpannableStringBuilder(tv_content.getText());

        tv_content.setText(styled);
        initSpeech();
        final ViewTreeObserver vto = tv_content.getViewTreeObserver();
        vto.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
              //  Log.i("zzz", "set Layout");
                mLayout = tv_content.getLayout();
                tv_content.getViewTreeObserver().removeOnGlobalLayoutListener(this);
            }
        });

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "My Tag");
    }


    //初始化
    private void initSpeech() {
        //1.创建SpeechSynthesizer对象, 第二个参数：本地合成时传InitListener
        mTts = SpeechSynthesizer.createSynthesizer(this, null);
        //2.合成参数设置，详见《科大讯飞MSC API手册(Android)》SpeechSynthesizer 类
        mTts.setParameter(SpeechConstant.VOICE_NAME, "xiaoyan");//设置发音人
        //mTts.setParameter(SpeechConstant.VOICE_NAME, "aisxrong");//设置发音人
        mTts.setParameter(SpeechConstant.SPEED, "50");//设置语速
        mTts.setParameter(SpeechConstant.VOLUME, "80");//设置音量，范围0~100
        mTts.setParameter(SpeechConstant.ENGINE_TYPE, SpeechConstant.TYPE_CLOUD); //设置云端
        //设置播放器音频流类型
        mTts.setParameter(SpeechConstant.STREAM_TYPE, "3");
        // 设置播放合成音频打断音乐播放，默认为true
        mTts.setParameter(SpeechConstant.KEY_REQUEST_FOCUS, "true");

        // 设置音频保存路径，保存音频格式支持pcm、wav，设置路径为sd卡请注意WRITE_EXTERNAL_STORAGE权限
        // 注：AUDIO_FORMAT参数语记需要更新版本才能生效
        mTts.setParameter(SpeechConstant.AUDIO_FORMAT, "wav");
        mTts.setParameter(SpeechConstant.TTS_AUDIO_PATH, Environment.getExternalStorageDirectory() + "/msc/tts.wav");
    }

    @Override
    protected void onResume() {
        super.onResume();
        mWakeLock.acquire();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mWakeLock.release();
    }

    public void read(View view) {
        //

        //3.开始合成
        if (null != mTts) {
            if (mTts.isSpeaking()) {
                mTts.stopSpeaking();
                btn_read.setText("开始读报");
            } else {
                btn_read.setText("停止读报");
                mTts.startSpeaking(tv_content.getText().toString(), mSynListener);
            }


        }

    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        mTts.stopSpeaking();
        // 退出时释放连接
        mTts.destroy();
    }

    //合成监听器
    private SynthesizerListener mSynListener = new SynthesizerListener() {

        @Override
        public void onSpeakBegin() {

        }

        @Override
        public void onBufferProgress(int i, int i1, int i2, String s) {

        }

        @Override
        public void onSpeakPaused() {

        }

        @Override
        public void onSpeakResumed() {

        }


        @Override
        public void onSpeakProgress(int percent, int beginPos, int endPos) {
            //更新文字颜色
            updateText(beginPos, endPos);
            //自动滚屏
            autoScroll(beginPos);
        }

        @Override
        public void onCompleted(SpeechError speechError) {
            if (!haveRecord) {
                //记录最后一句话的时间
                int size = content.length() - lastBegin;
                int avTime = (int) (System.currentTimeMillis() - lastTime) / size;
                readTimeMap.put(lastBegin, avTime);
                haveRecord = true;
            }
            reset();
        }

        @Override
        public void onEvent(int i, int i1, int i2, Bundle bundle) {

        }
    };

    private void reset() {
        //重置
        mHandler.stopUpdate();
        btn_read.setText("开始读报");
        styled.removeSpan(spanRed);
        tv_content.setText(styled);
        scroll_news.smoothScrollTo(0, 0);
        lastBegin = -1;
    }

    ForegroundColorSpan spanRed = null;
    long lastTime = 0;
    int lastBegin = -1;
    //是否记录过时间线
    boolean haveRecord = false;
    Map<Integer, Integer> readTimeMap = new HashMap<>();
    private static final int MSG_UPDATE = 1;
    private static final int MSG_UPDATE_LOOP = 2;
    UpdateHandler mHandler = new UpdateHandler();

    class UpdateHandler extends Handler {
        boolean needUpdate = false;
        int begin = 0;
        int end = 0;
        int avTime = 0;
        int index = 0;

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_UPDATE:
                    index=0;
                    needUpdate = true;
                    //起始位置
                    begin = msg.arg1;
                    //结束位置
                    end = msg.arg2;
                    //平均用时
                    Integer integer = readTimeMap.get(begin);
                    if (integer == null || integer == 0) {
                        needUpdate = false;
                    } else {
                        avTime = integer;
                        sendEmptyMessage(MSG_UPDATE_LOOP);
                    }
                    break;
                case MSG_UPDATE_LOOP:
                    if (!needUpdate) {
                        removeMessages(MSG_UPDATE_LOOP);
                        return;
                    }
                    index++;
                    int newEnd = begin + index;
                    if (newEnd > end) {
                        stopUpdate();
                    } else {
                        styled.setSpan(spanRed, begin, newEnd, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        tv_content.setText(styled);
                        sendEmptyMessageDelayed(MSG_UPDATE_LOOP, avTime);
                    }
                    break;
            }
        }

        public void stopUpdate() {
            needUpdate = false;
            removeMessages(MSG_UPDATE_LOOP);
        }
    }


    private void updateText(int beginPos, int endPos) {
        if (lastBegin != beginPos) {
            if (beginPos == 0) {
                //第一次,记录开始时间
                lastTime = System.currentTimeMillis();
            }

            styled.removeSpan(spanRed);
            if (!haveRecord) {
                //整句更新
                styled.setSpan(spanRed, beginPos, endPos, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                tv_content.setText(styled);
                if (beginPos != 0) {
                    //从第二句开始,记录上一句的平均用时
                    //计算平均用时
                    long now = System.currentTimeMillis();
                    long useTime = now - lastTime;
                    int averageTime = (int) (useTime / (beginPos - lastBegin));
                    Log.i("zzz", "save begin=" + lastBegin + " avTime=" + averageTime);
                    readTimeMap.put(lastBegin, averageTime);
                    lastTime = now;
                }

            } else {
                //逐字更新
                Message msg = new Message();
                msg.what = MSG_UPDATE;
                msg.arg1 = beginPos;
                msg.arg2 = endPos;
                mHandler.sendMessage(msg);
            }

            lastBegin = beginPos;
        }
    }


    // 自动滚屏相关代码

    Layout mLayout;
    int lastLine = 0;

    private void autoScroll(int beginPos) {
        int line = getLine(beginPos);
        //如果行数发生变化
        if (line != lastLine) {
            //保持3行的高度
            if (line >= 3) {
                scroll_news.smoothScrollTo(0, tv_content.getTop() + mLayout.getLineTop(line - 3));
            }
            lastLine = line;
        }
    }


    private int getLine(int staPos) {
        int lineNumber = 0;
        if (mLayout != null) {
            int line = mLayout.getLineCount();
            for (int i = 0; i < line - 1; i++) {
                if (staPos <= mLayout.getLineStart(i)) {
                    lineNumber = i;
                    break;
                }
            }
        }
        return lineNumber;

    }
}
