/*
 * Copyright (C) 2008-2009 Google Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 * 
 * Some Hangul InputMethod Code added by www.kandroid.org
 * 
 */

package capstone.kookmin.sksss.test2;

import android.app.ActionBar;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.RequiresApi;
import android.support.v7.view.menu.MenuView;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.util.StringBuilderPrinter;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.CorrectionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedText;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethod;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Array;
import java.net.Socket;
import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.text.Editable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.w3c.dom.Text;


/**
 * Example of writing an input method for a soft keyboard.  This code is
 * focused on simplicity over completeness, so it should in no way be considered
 * to be a complete soft keyboard implementation.  Its purpose is to provide
 * a basic example for how you would get started writing an input method, to
 * be fleshed out as appropriate.
 */
public class SoftKeyboard extends InputMethodService
        implements KeyboardView.OnKeyboardActionListener {
    static final boolean DEBUG = false;

    /**
     * This boolean indicates the optional example code for performing
     * processing of hard keys in addition to regular text generation
     * from on-screen interaction.  It would be used for input methods that
     * perform language translations (such as converting text entered on 
     * a QWERTY keyboard to Chinese), but may not be used for input methods
     * that are primarily intended to be used for on-screen text entry.
     */
    static final boolean PROCESS_HARD_KEYS = true;
    static final int correctWord_MAX = 3;   //하나의 오타 단어에 대한 추천 단어의 최대 개수
    static final int MAX_TEXT = 10000;  //최대 텍스트 길이

    //서버에서 받은 데이터 종류에 대한 메시지 핸들러
    static final int MSG_REQUEST_RECEIVE = 0;
    //옵션창으로 전환할 keycode
    static final int CODE_OPTION_VIEW = -8;
    static final int CODE_AUTO_CORRECTION = -9;
    static final int CODE_AUTO_SPACING = -10;

    private KeyboardView mInputView;
//    private CandidateView mCandidateView;
//    private TextView mTextView;
    private CompletionInfo[] mCompletions;

    //수정 가능한 String
    private StringBuilder mComposing = new StringBuilder();
    //updateSelection() 전의 텍스트 데이터
    //private String beforeTextData = "";
    //updateSelection() 현재의 텍스트 데이터
    //private String nowTextData;
    private StringBuilder test = new StringBuilder();/////////////////////////
    private StringBuilder test2 = new StringBuilder();
    private List<String[]> textListSeparated = new ArrayList<String[]>();   //서버에서 받은 오타 단어별 수정 리스트 저장
    private List<correctionButtonInform> cBtnList = new ArrayList<correctionButtonInform>();    //단어 수정 버튼 클릭시 수행되는 과정에서 필요한 정보를 담은 클래스
    private List<int[]> correctionTextPosition = new ArrayList<int[]>();

    private boolean isAutoCorrect = false;
    private boolean isAutoSpacing = false;
    //현재 키보드
    private Keyboard mCurrentKeyboard;
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

//    private InputMethodManager imm;
    private Keyboard mSymbolsKeyboard;
    private Keyboard mSymbolsShiftedKeyboard;
    private Keyboard mSymbolsEnKeyboard;
    private Keyboard mSymbolsEnShiftedKeyboard;
    private Keyboard mQwertyKeyboard;
    private Keyboard mOptionKeyboard;

    private Keyboard mHangulKeyboard; // Hangul Code
    private Keyboard mHangulShiftedKeyboard; // Hangul Code
    private Keyboard mSejongKeyboard;

    private Keyboard mCurKeyboard;

    private String mWordSeparators;

    //서버 관련
    private static final String ip = "203.246.112.165";
    private static final int port = 8100;
    private MessegeHandler mHandler = new MessegeHandler(this);
    private TcpClient tcp = new TcpClient(this, ip, port, mHandler);
    Thread t;
    private static int NETWORK_DELAY = 2000; //서버 데이터 전송에 과부하를 막기위한 네트워크 딜레이(자동 오타수정, 띄어쓰기 기능 수행시)
    private long oldSendTime;
    //자동완성 및 DB 관련 -*-
    ArrayAdapter<String> myAdapter;
    DatabaseHandler databaseH;
    String[] item = new String[]{"please Search.."};
    //end -*-

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mWordSeparators = getResources().getString(R.string.word_separators);
//        imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        //
//        String[] a = new String[3];
//        a[0] = "천";
//        a[1] = "하하";
//        a[2] = "ㅋㅋㅋ";
//        String[] b = new String[2];
//        b[0] = "ㅎ";
//        b[1] = "zz";
//
//        cBtnList.add(new correctionButtonInform(1,3,"나는",a));
//        cBtnList.add(new correctionButtonInform(2,4,"나는",a));
//        cBtnList.add(new correctionButtonInform(3,5,"나는",a));
//        cBtnList.add(new correctionButtonInform(3,5,"크크",b));
//        cBtnList.add(new correctionButtonInform(4,6,"크크",b));
        //
        mCurrentKeyboard = mQwertyKeyboard;

        //DB Source -*-
        try {
            InputStreamReader inputreader = new InputStreamReader(this.getAssets().open("dic.csv"), "euc-kr");
            BufferedReader buffereader = new BufferedReader(inputreader);
            List<String> list = new ArrayList<String>();
            String line;

            do {
                line = buffereader.readLine();
                list.add(line);
            } while(line!=null);

            databaseH = new DatabaseHandler(this);

            databaseH.insert(list);

//            myAutoComplete = (CustomAutoCompleteView) findViewById(R.id.myautocomplete);
//
//            myAutoComplete
//                    .addTextChangedListener(new CustomAutoCompleteTextChangedListener(
//                            this));
//
//            myAdapter = new ArrayAdapter<String>(this,
//                    android.R.layout.simple_dropdown_item_1line, item);
//
//            myAutoComplete.setAdapter(myAdapter);

//			myAutoComplete.setTokenizer(new SpaceTokenizer());

        } catch (NullPointerException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

        //DB Source end -*-
        //tcp.start();
    }

    //-*-DB
    public String[] getItemsFromDb(String searchTerm) {

        List<MyObject> products = databaseH.read(searchTerm);

        int rowCount = products.size();
        String[] item = new String[rowCount];
        int x = 0;

        for (MyObject record : products) {

            item[x] = record.objectName;
            x++;
        }

        return item;
    }
    //-*-

    /**
     * This is the point where you can do all of your UI initialization.  It
     * is called after creation and any configuration change.
     */
    //UI초기화 부분, 생성 or 구성변경후 호출
    @Override public void onInitializeInterface() {
        if (mQwertyKeyboard != null) {
            // Configuration changes can happen after the keyboard gets recreated,
            // so we need to be able to re-build the keyboards if the available
            // space has changed.
            //기존의 키보드가 바뀐 경우, 또한 넓이가 다르면 다시 빌드해야함(가로세로?)
            int displayWidth = getMaxWidth();
            if (displayWidth == mLastDisplayWidth) return;
            mLastDisplayWidth = displayWidth;
        }
        mQwertyKeyboard = new Keyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new Keyboard(this, R.xml.symbols);
        mSymbolsEnKeyboard = new Keyboard(this, R.xml.symbols_en);
        mSymbolsShiftedKeyboard = new Keyboard(this, R.xml.symbols_shift);
        mSymbolsEnShiftedKeyboard = new Keyboard(this, R.xml.symbols_en_shift);
        mHangulKeyboard = new HangulKeyboard(this, R.xml.hangul);
        mHangulShiftedKeyboard = new HangulKeyboard(this, R.xml.hangul_shift);
        mSejongKeyboard = new SejongKeyboard(this, R.xml.sejong);
        mOptionKeyboard = new Keyboard(this, R.xml.option);
    }

    /**
     * Called by the framework when your view for creating input needs to
     * be generated.  This will be called the first time your input method
     * is displayed, and every time it needs to be re-created such as due to
     * a configuration change.
     */
    //키보드 디스플레이시 호출(똑같)
    @Override public View onCreateInputView() {
        mInputView = (KeyboardView) getLayoutInflater().inflate(
                R.layout.input, null);
        mInputView.setOnKeyboardActionListener(this);
        mInputView.setKeyboard(mQwertyKeyboard);
        mInputView.setPreviewEnabled(false);
        //mCurrentKeyboard = mQwertyKeyboard;
        //mCandidateView = new CandidateView(this);
        //setCandidatesViewShown(true);
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    //자동완성, 오타수정 뷰 관련 변수
    View wordBar, correctionBar, correctlayout;
    LayoutInflater li;
    Button button1;
    Button button2;
    Button button3;
    Button[] correctionButton = new Button[3];  //수정 버튼
    int popUpPosition; //현재 클릭한 오타 수정 버튼

    //자동완성/오타수정 뷰 관련 클릭 리스너
    Button.OnClickListener mOnClickListner = new View.OnClickListener(){

        @RequiresApi(api = Build.VERSION_CODES.M)
        @Override
        public void onClick(View v) {
            Log.d("aa","bb");
            Button bt;
            InputConnection ic = getCurrentInputConnection();
            Log.d("a"+ic.getExtractedText(new ExtractedTextRequest(),0).text,"b");
            Keyboard current = mInputView.getKeyboard();
            switch (v.getId()){
                    ///////////
//                    test.setLength(0);
//                    test.append("a");
//                    test2.setLength(0);
//                    test2.append("c");
//                    CorrectionInfo a =  new CorrectionInfo(2,"a","b");
////                    getCurrentInputConnection().beginBatchEdit();
////                    getCurrentInputConnection().setSelection(2,3);
////                    getCurrentInputConnection().commitText("a",1);
////                    getCurrentInputConnection().endBatchEdit();
//                    //getCurrentInputConnection().commitText("a",0);
//                    Log.d("boolean?",String.valueOf(s));
//                    Log.d(a.getOldText().toString(),a.getNewText().toString());
                    ///////////
                case R.id.changeBt2:
                    setCandidatesView(wordBar);
                    boolean s = getCurrentInputConnection().commitCorrection(new CorrectionInfo(0,"ab","cd"));
                    Log.d("zzzzzzzzzzz",String.valueOf(s));
                    break;
                //자동완성 클릭
                case R.id.word1:case R.id.word2:case R.id.word3:
                    bt = (Button)v;
                    //composing의 경우 단어 자동완성
                    if (mComposing.length() > 0) {
                        mComposing.setLength(0);
                        getCurrentInputConnection().commitText(bt.getText()+" ",1);
                    }
                    //composing이 아닌 경우 단어 자동완성
                    else{
                        setText(candidateWordPosArray[0],candidateWordPosArray[1]-candidateWordPosArray[0],bt.getText() + " ");
                    }
                    //!?!?!?!?!
                    ////////////////////
//                    Log.d("isOK?","onClickListner");
//                    //서버에 클릭한 버튼의 내용을 전송
//                    if(tcp != null &&   tcp.isSocketOn()) {
//                        Log.d("in","socket");
//                        tcp.sendData((String) bt.getText());
//                    }
                    ////////////////
                    //isTextUpdate = true;
                    //Log.d("헤헷","onUpdateSelection");
//                    getCurrentInputConnection().setComposingText(mComposing, 1);
//                    mComposing.setLength(0);
//                    getCurrentInputConnection().finishComposingText();
                    //isCommitted = true;
                    if (current == mHangulKeyboard || current == mHangulShiftedKeyboard ) {
                        clearHangul();
                    }
                    break;
                case R.id.cword1:case R.id.cword2:case R.id.cword3:
                    bt = (Button)v;
                    /* 이 부분은 popupmenu
                    PopupMenu cPopup = new PopupMenu(getApplicationContext(), bt);
                    cPopup.getMenuInflater().inflate(R.menu.correctionpopup, cPopup.getMenu());
                    cPopup.setOnMenuItemClickListener(mOnMenuItemClickListener);
                    cPopup.setOnDismissListener(mOnDismissListener);
                    cPopup.getMenu().clear();
                    if(v.getId() == R.id.cword1)
                        popUpPosition = 0;
                    else if(v.getId() == R.id.cword2)
                        popUpPosition = 1;
                    else
                        popUpPosition = 2;

                    if(cBtnList.size()>popUpPosition)
                    {
                        //수정단어를 채움(2개까지만)
                        for(int i=0; i<cBtnList.get(popUpPosition).getCorrectionWord().length && i<2; i++)
                        {
                            cPopup.getMenu().add(cBtnList.get(popUpPosition).getCorrectionWord()[i]);
                        }
                        cPopup.getMenu().add(1,1,1,cBtnList.get(popUpPosition).getOldWord());
                    }
                    if(cPopup.getMenu().size()>0)
                        cPopup.show();
                        popupmenu 끝*/
                    //popupwindow
                    if(v.getId() == R.id.cword1)
                        popUpPosition = 0;
                    else if(v.getId() == R.id.cword2)
                        popUpPosition = 1;
                    else
                        popUpPosition = 2;
                    initiatePopupWindow(popUpPosition,bt);
                    //popupwindow 끝
                    break;
                //오타수정 바 혹은 갱신 버튼 클릭시
                case R.id.changeBt:
                    //cBtnList.clear();
                    setCandidatesView(correctionBar);
                case R.id.renew:
//                    ic = getCurrentInputConnection();

                    //서버로 보낼 오타 수정 메시지 작성 및 송신
                    sendCorrectionJson();
                    //-*-test
                    //Log.d("isTestGood?","???????????????????????");
                    //String jsonTest = "{\"response\" : [\"spacing\"], \"spacing\" : \"나는 학교를 갑니다.\"}"; //"{\"response\" : [\"modified\"], \"modified\" : {\"정말루\" : [\"0\",\"정말로\"], \"하르\" : [\"2\",\"하루\"] }}";
                    //processJsonMessegeTest(jsonTest);
                    //-*-testend
//                    if(!tcp.getIsRunning())
//                        TcpOpen(tcp);
//                    if(ic.getExtractedText(new ExtractedTextRequest(), 0) != null) {
//                        String text = ic.getExtractedText(new ExtractedTextRequest(), 0).text.toString();
//                        String sendJson = makeJsonToReq(false, true, null, setTextListForCorrect(text));
//                        Log.d("text??", sendJson);
//                        tcp.sendData(sendJson);
//                    }
                    break;

//                isOkUpdateSelection = false;
            }
        }
    };

    private PopupWindow mDropdown;
    private String focusOldWordbyPopup; // old word 식별 (mOnPopclick2를 위함)
    TextView correctlist[] = new TextView[2];
    private PopupWindow initiatePopupWindow(int pos, Button btn){
        correctlayout = li.inflate(R.layout.correctionpopup, null);
        boolean isShow = false;
        correctlist[0] = (TextView)correctlayout.findViewById(R.id.cPopUp1);
        //correctlist[1] = (TextView)correctlayout.findViewById(R.id.cPopUp2);
        correctlist[1] = (TextView)correctlayout.findViewById(R.id.cPopUp3);

        correctlayout.measure(View.MeasureSpec.UNSPECIFIED,
                View.MeasureSpec.UNSPECIFIED);
        mDropdown = new PopupWindow(correctlayout, 380, 250/*FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT*/);
        mDropdown.setOutsideTouchable(true);
        mDropdown.setBackgroundDrawable(new BitmapDrawable());
        Drawable background = getResources().getDrawable(R.drawable.popuplayout_bg);
        mDropdown.setBackgroundDrawable(background);
        //correctlist[2].setBackground();
        for(int i=0; i<2; i++)
            correctlist[i].setOnClickListener(mOnPopupClickListener2);//-*-
        for(int i=0; i<2; i++)
            correctlist[i].setText("");
        if(cBtnList.size()>pos)
        {
            //수정단어를 채움(2개까지만)
            for(int i=0; i<cBtnList.get(pos).getCorrectionWord().length && i<1; i++)
            {
                correctlist[i].setText(cBtnList.get(pos).getCorrectionWord()[i]);
                //cPopup.getMenu().add(cBtnList.get(pos).getCorrectionWord()[i]);
            }
            //수정 취소 버튼(추후 이미지 수정)-*-
            correctlist[1].setText("no correct"/*cBtnList.get(pos).getOldWord()*/);//////////////////////////////////////
//            cPopup.getMenu().add(1,1,1,cBtnList.get(pos).getOldWord());
            isShow=true;
        }
        if(isShow)
            mDropdown.showAsDropDown(btn, 3, 3);
        focusOldWordbyPopup = btn.getText().toString(); //Old word 식별
        return mDropdown;
    }
    //popupwindow용 오타 수정 리스너
    TextView.OnClickListener mOnPopupClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int correctPos, correctLength;
            mDropdown.dismiss();
            Log.d("OK?","OKK?");
            InputConnection ic = getCurrentInputConnection();
            //수정 취소 버튼이라면..
            TextView tv = (TextView) v;
            if(v.getId() == R.id.cPopUp3)
            {
                cBtnList.remove(popUpPosition);
            }
            //수정할 단어를 클릭했다면..
            else {
                Log.d("a"+ic.getExtractedText(new ExtractedTextRequest(),0).text,"a");
                //수정 위치와 수정될 단어의 길이를 구함
                correctPos = cBtnList.get(popUpPosition).getStartPos() + cBtnList.get(popUpPosition).getOldWord().length();
                correctLength = tv.getText().length() - cBtnList.get(popUpPosition).getOldWord().length();
                //
//            ic.setSelection(cBtnList.get(popUpPosition).getStartPos(),
//                    cBtnList.get(popUpPosition).getStartPos() + cBtnList.get(popUpPosition).getOldWord().length());
//            if(cBtnList.get(popUpPosition).getOldWord().equals(ic.getSelectedText(0))) {
                if (mComposing.length() > 0) {
                    commitTyped(ic);
                    clearHangul();
                }
                ic.setSelection(cBtnList.get(popUpPosition).getStartPos(),
                        cBtnList.get(popUpPosition).getStartPos() + cBtnList.get(popUpPosition).getOldWord().length());
//            Log.d("compare",ic.getSelectedText(0).toString() +","+cBtnList.get(popUpPosition).getOldWord());
                if (ic.getSelectedText(0) != null && ic.getSelectedText(0).equals(cBtnList.get(popUpPosition).getOldWord())) {
                    setText(cBtnList.get(popUpPosition).getStartPos(), cBtnList.get(popUpPosition).getOldWord().length(), tv.getText().toString());
                    updateAfterCBtnList(popUpPosition, correctLength);
                } else {
                    Log.d("Not", "equal");
                    ExtractedText nowText = ic.getExtractedText(new ExtractedTextRequest(), 0);
                    if(nowText!=null) {
                        int endOfText = nowText.text.length();
                        ic.setSelection(endOfText, endOfText);
                    }
                    makeCorrectFailToast();//-*-
                }
                cBtnList.remove(popUpPosition);
            }
            renewCorrectionButtonsAsCbtnList();
        }
    };

    //-*- 오타 수정 ver.2 : 스트링을 비교하면서 오타를 수정하는 버전(문맥 X 문자만)
    TextView.OnClickListener mOnPopupClickListener2 = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            int correctPos, correctLength;
            mDropdown.dismiss();
            Log.d("OK?","OKK?");
            InputConnection ic = getCurrentInputConnection();
            //수정 취소 버튼이라면..
            TextView tv = (TextView) v;
            if(v.getId() == R.id.cPopUp3)
            {
                cBtnList.remove(popUpPosition);
            }
            //수정할 단어를 클릭했다면..
            else {
                Log.d("a"+ic.getExtractedText(new ExtractedTextRequest(),0).text,"a");
                //수정 위치와 수정될 단어의 길이를 구함
                String text =  ic.getExtractedText(new ExtractedTextRequest(),0).text.toString();
                if(text != null && !text.equals(""))
                {
                    String correctText = tv.getText().toString();
                    int textLen = focusOldWordbyPopup.length();
                    int correctTextLen = correctText.length();
                    Pattern word = Pattern.compile(focusOldWordbyPopup);
                    Matcher matchString = word.matcher(text);

                    Log.d(focusOldWordbyPopup, text);
                    if(matchString.find())
                    {
                        if (mComposing.length() > 0) {
                            commitTyped(ic);
                            clearHangul();
                        }

                        Log.d("startPos : ", matchString.start() + "");
                        setText(matchString.start(), textLen, correctText);
//                            updateAfterCBtnList(popUpPosition, correctLength);
                    }
                    else
                        makeCorrectFailToast();

                }
                else
                    makeCorrectFailToast();//-*-

                cBtnList.remove(popUpPosition);
            }
            renewCorrectionButtonsAsCbtnList();
        }
    };

//    PopupMenu.OnMenuItemClickListener mOnMenuItemClickListener = new PopupMenu.OnMenuItemClickListener() {
//        InputConnection ic;
//        int correctPos, correctLength;
//        @Override
//        public boolean onMenuItemClick(MenuItem item) {
//            ////
//           // Log.d("itemNum","" + item.getOrder() + "," + item.);//
//            //수정 취소 버튼이라면..
//            if(item.getOrder() == 1)
//            {
//                cBtnList.remove(popUpPosition);
//            }
//            //수정할 단어를 클릭했다면..
//            else {
//                //수정 위치와 수정될 단어의 길이를 구함
//                ic = getCurrentInputConnection();
//                correctPos = cBtnList.get(popUpPosition).getStartPos() + cBtnList.get(popUpPosition).getOldWord().length();
//                correctLength = item.getTitle().length() - cBtnList.get(popUpPosition).getOldWord().length();
//                //
////            ic.setSelection(cBtnList.get(popUpPosition).getStartPos(),
////                    cBtnList.get(popUpPosition).getStartPos() + cBtnList.get(popUpPosition).getOldWord().length());
////            if(cBtnList.get(popUpPosition).getOldWord().equals(ic.getSelectedText(0))) {
//                if (mComposing.length() > 0) {
//                    commitTyped(ic);
//                    clearHangul();
//                }
//                ic.setSelection(cBtnList.get(popUpPosition).getStartPos(),
//                        cBtnList.get(popUpPosition).getStartPos() + cBtnList.get(popUpPosition).getOldWord().length());
////            Log.d("compare",ic.getSelectedText(0).toString() +","+cBtnList.get(popUpPosition).getOldWord());
//                if (ic.getSelectedText(0) != null && ic.getSelectedText(0).equals(cBtnList.get(popUpPosition).getOldWord())) {
//                    setText(cBtnList.get(popUpPosition).getStartPos(), cBtnList.get(popUpPosition).getOldWord().length(), item.getTitle().toString());
//                    updateAfterCBtnList(popUpPosition, correctLength);
//                } else {
//                    Log.d("Not", "equal");
//                    int endOfText = ic.getExtractedText(new ExtractedTextRequest(), 0).text.length();
//                    ic.setSelection(endOfText, endOfText);
//                    makeCorrectFailToast();//-*-
//                }
//                cBtnList.remove(popUpPosition);
//            }
////                updateCBtnList(correctPos, correctLength);
////            }
//            //클리어 해주는 알고리즘이 필요함
//
//
////            ic.commitCorrection(new CorrectionInfo(cBtnList.get(popUpPosition).getStartPos(), cBtnList.get(popUpPosition).getOldWord(),item.getTitle()));
//            //Log.d("위치는?",String.valueOf(cBtnList.get(popUpPosition).getStartPos()));
//           // isTextUpdate=true;
//            /////////// 수정 이벤트
//           // Log.d("onUpdateSelection :","onUpdateSelection :");
////            imm.showSoftInput(mInputView,InputMethodManager.SHOW_IMPLICIT);
//            renewCorrectionButtonsAsCbtnList();
//            return true;
//        }
//    };

    private void makeCorrectFailToast(){
        Toast.makeText(this.getApplicationContext() , "수정 실패 : 이미 텍스트가 바뀌었습니다.", Toast.LENGTH_SHORT).show();
    }

//    PopupMenu.OnDismissListener mOnDismissListener = new PopupMenu.OnDismissListener(){
//
//        @Override
//        public void onDismiss(PopupMenu menu) {
//            Log.d("in","??????????????/");
//            //imm.showSoftInput(mInputView,InputMethodManager.SHOW_IMPLICIT);
//        }
//    };

    //자동완성 버튼 갱신
    void setCandidateButton(String[] str)
    {
        if(str.length > 0)
            button1.setText(str[0]);
        else
            button1.setText("");
        if(str.length > 1)
            button2.setText(str[1]);
        else
            button2.setText("");
        if(str.length > 2)
            button3.setText(str[2]);
        else
            button3.setText("");
    }

    void updateCandidateButton()
    {
//        item = getItemsFromDb(candidateOldWord);
//        String[] a = new String[3];
//        a[0] = candidateOldWord;
//        a[1] = a[0];
//        a[2] = a[1];
//        Log.d("z","a"+a[0]+"z");
        setCandidateButton(getItemsFromDb(candidateOldWord));
    }

    //candidate 생성
    @Override public View onCreateCandidatesView() {
        li = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        wordBar = li.inflate(R.layout.wordbar, null);
        correctionBar = li.inflate(R.layout.correctionbar, null);
        //LinearLayout ll = (LinearLayout) wordBar.findViewById(R.id.wordLayout);
        Button changeBtn = (Button) wordBar.findViewById(R.id.changeBt);
        Button changeBtn2 = (Button) correctionBar.findViewById(R.id.changeBt2);
        Button renewBtn = (Button) correctionBar.findViewById(R.id.renew);
        button1 = (Button) wordBar.findViewById(R.id.word1);
        button2 = (Button) wordBar.findViewById(R.id.word2);
        button3 = (Button) wordBar.findViewById(R.id.word3);
        correctionButton[0] = (Button) correctionBar.findViewById(R.id.cword1);
        correctionButton[1] = (Button) correctionBar.findViewById(R.id.cword2);
        correctionButton[2] = (Button) correctionBar.findViewById(R.id.cword3);

        button1.setOnClickListener(mOnClickListner);
        button2.setOnClickListener(mOnClickListner);
        button3.setOnClickListener(mOnClickListner);
        for(int i = 0; i< correctionButton.length; i++)
            correctionButton[i].setOnClickListener(mOnClickListner);

        changeBtn.setOnClickListener(mOnClickListner);
        changeBtn2.setOnClickListener(mOnClickListner);
        renewBtn.setOnClickListener(mOnClickListner);
        setCandidatesViewShown(true);
        return wordBar;
//        mCandidateView = new CandidateView(this);
//        mCandidateView.setService(this);
//        mTextView = (TextView) getLayoutInflater().inflate(R.layout.wordbar, null);
//        return mTextView;//mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    //텍스트에 대한 입력 준비(입력 텍스트 보일시)
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);

        Log.i("keyboardzz", "onStartInput");

        //스택들 초기화,,
        clearHangul();
//        clearSejong();
        previousCurPos = -1;

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
//        updateCandidates();
//        updateCandidateButton();// 후보자 추천 설정

        //쉬프트 클리어
        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }

        mPredictionOn = true;
//        mCompletionOn = false;
//        mCompletions = null;

        // We are now going to initialize our state based on the type of
        // text being edited.
        //편집중인 텍스트 유형에 따라 초기화
        switch (attribute.inputType&EditorInfo.TYPE_MASK_CLASS) {
            case EditorInfo.TYPE_CLASS_NUMBER:
            case EditorInfo.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                //숫자와 날짜는 기호 키보드로 기본 설정되어 있으며 추가 기능은 없습니다.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                //전화기는 기호 키보드로 기본 설정되지만 전용 전화 키보드가 필요할 수도 있습니다.
                mCurKeyboard = mSymbolsKeyboard;
                break;

            case EditorInfo.TYPE_CLASS_TEXT:
                // This is general text editing.  We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                //이것은 일반적인 텍스트 편집입니다. 우리는 정상적인 알파벳 키보드를 기본값으로 사용하고 예상 텍스트 (사용자 유형으로 후보를 표시)를 수행해야한다고 가정합니다.
                //mCurKeyboard = mQwertyKeyboard;
                if(mCurrentKeyboard==null)
                    mCurrentKeyboard=mQwertyKeyboard;
                mCurKeyboard = mCurrentKeyboard;
                mPredictionOn = true;

                // We now look for a few special variations of text that will
                // modify our behavior.
                //이제 우리의 행동을 수정하는 몇 가지 특수한 텍스트 변형을 찾습니다.
                int variation = attribute.inputType &  EditorInfo.TYPE_MASK_VARIATION;
                if (variation == EditorInfo.TYPE_TEXT_VARIATION_PASSWORD ||
                        variation == EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    //사용자가 암호를 입력 할 때 입력하는 내용 / 예측을 표시하지 않습니다.
                    mPredictionOn = false;
                }

                if (variation == EditorInfo.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_URI
                        || variation == EditorInfo.TYPE_TEXT_VARIATION_FILTER) {
                    // Our predictions are not useful for e-mail addresses
                    // or URIs.
                    //우리의 예측은 전자 메일 주소 나 URI에 유용하지 않습니다.

                    mPredictionOn = false;
                }

                if ((attribute.inputType&EditorInfo.TYPE_TEXT_FLAG_AUTO_COMPLETE) != 0) {
                    // If this is an auto-complete text view, then our predictions
                    // will not be shown and instead we will allow the editor
                    // to supply their own.  We only show the editor's
                    // candidates when in fullscreen mode, otherwise relying
                    // own it displaying its own UI.
                    //이것이 자동 완성 텍스트보기 인 경우 예측이 표시되지 않고 대신 편집기가 자체 텍스트를 제공 할 수 있도록 허용합니다.
                    //우리는 전체 화면 모드 일 때만 편집기 후보를 보여 주며, 그렇지 않은 경우 자체 UI를 표시합니다.
                    //mPredictionOn = false;
                    //mCompletionOn = isFullscreenMode();
                }

                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                //에디터의 현재 상태를 살펴보고 알파벳 키보드를 시프트 아웃할지 여부를 결정하기를 원합니다.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                //모든 알 수없는 입력 유형의 경우, 특수 기능이없는 알파벳 키보드를 기본값으로 사용하십시오.
                mCurKeyboard = mQwertyKeyboard;
                updateShiftKeyState(attribute);
        }

        // Update the label on the enter key, depending on what the application
        // says it will do.
        //Log.d("isNull?",mCurrentKeyboard.toString());
        //mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);

//        if(socket!= null) {
//            try {
//                setSocket(ip, port); //소켓 설정
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//            if (checkNetworkRead.getState() != Thread.State.RUNNABLE)
//                checkNetworkRead.start();
//        }
    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    //유저가 필드의 에딧팅을 끝내면 불려짐. 이를 이용하여 상태를 리셋할수있다.
    @Override public void onFinishInput() {
        super.onFinishInput();
        Log.d("keyboardzz","onFinishInput");

        // Clear current composing text and candidates.
        //현재 작성중인 텍스트 및 후보를 지웁니다.
        mComposing.setLength(0);
        updateCandidates();

        // We only hide the candidates window when finishing input on
        // a particular editor, to avoid popping the underlying application
        // up and down if the user is entering text into the bottom of
        // its window.
        //사용자가 창 하단에 텍스트를 입력하는 경우 기본 응용 프로그램을 팝업 혹은 팝다운을
        //피하기 위해 특정 편집기에서 입력을 마칠 때만 후보자 창을 숨 깁니다.
        //setCandidatesViewShown(false);

        //키보드 뷰 닫기
        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
        Log.d("find?","onFinishInput");

        //서버와의 소켓 닫기
        //Log.d("tcp","close");
        //TcpClose(tcp);
        //Log.d("boolean",tcp.getIsRunning() + "");
    }

    //편집기에서 입력 시작시 호출
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        Log.d("keyboardzz","onStartInputView");
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        //선택한 키보드를 입력보기에 적용하십시오.
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
        Log.d("find?","onStartInputView");
        //서버와 소켓 오픈 및 통신 시작
        //Log.d("tcp","open");
        //TcpOpen(tcp);
        //Log.d("boolean",tcp.getIsRunning() + "");
    }

    @Override
    public void onComputeInsets(InputMethodService.Insets outInsets){
        super.onComputeInsets(outInsets);
        if(!isFullscreenMode())
        {
            outInsets.contentTopInsets = outInsets.visibleTopInsets;
        }
    }

    /**
     * Deal with the editor reporting movement of its cursor.
     */
    //커서의 움직임을 보고하는 에디터를 처리함.
    // 새 선택영역을 보고할떄마다 호출,입력 메소드가 추출된 텍스트갱신을 요구했는지 상관없이 호출
    //주로 커서의 업데이트
    @Override public void onUpdateSelection(int oldSelStart, int oldSelEnd,
                                            int newSelStart, int newSelEnd,
                                            int candidatesStart, int candidatesEnd) {

        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd,
                candidatesStart, candidatesEnd);
        //Log.i("Hangul", "onUpdateSelection :" + String.valueOf(isTextUpdate));
        InputConnection ic = getCurrentInputConnection();

        if(newSelStart<newSelEnd && ic.getSelectedText(0)!=null)
        {
            candidateOldWord = ic.getSelectedText(0).toString();
            candidateWordPosArray[0] = newSelStart;
            candidateWordPosArray[1] = newSelEnd;
        }
        else if(mComposing.length()<=0)
            updateCandidateWord(newSelStart);

        //오타 수정 단어들의 위치 갱신
//        if(ic.getExtractedText(new ExtractedTextRequest(), 0)==null)
//            nowTextData="";
//        else
//            nowTextData = ic.getExtractedText(new ExtractedTextRequest(), 0).text.toString();
//        if((nowTextData.length() != beforeTextData.length()) && !(oldSelStart == newSelStart && oldSelEnd == newSelEnd))
//        {
//            Log.i("Hangul", "onUpdateSelection : 수정");
//            for(int i = 0; i<cBtnList.size(); i++)
//            {
//                if(cBtnList.get(i).getStartPos() >= oldSelEnd)
//                {
//                    cBtnList.get(i).addPos(newSelEnd-oldSelEnd);
//                }
////                else if(cBtnList.get(i).getStartPos()>=oldSelStart && cBtnList.get(i).getFinishPos()<=oldSelEnd)
////                {
////                    cBtnList.remove(i);
////                }
//            }
//        }
//        if(!(oldSelStart == newSelStart && oldSelEnd == newSelEnd))
//            beforeTextData = nowTextData;
//            isTextUpdate = false;
        Log.i("Hangul", "onUpdateSelection :"
                + Integer.toString(oldSelStart) + ":"
                + Integer.toString(oldSelEnd) + ":"
                + Integer.toString(newSelStart) + ":"
                + Integer.toString(newSelEnd) + ":"
                + Integer.toString(candidatesStart) + ":"
                + Integer.toString(candidatesEnd)
        );

        // If the current selection in the text view changes, we should
        // clear whatever candidate text we have.
        // 텍스트 뷰에서 현재 selection이 변경되면, 우리가 가진 모든 텍스트를 지워야한다.
        Keyboard current = mInputView.getKeyboard();
        if (current == mSejongKeyboard) {

        }
        else if (current != mHangulKeyboard && current != mHangulShiftedKeyboard) {
            if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                    || newSelEnd != candidatesEnd)) {
                mComposing.setLength(0);
                //updateCandidates();
//                ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.finishComposingText();
                    //isCommitted = true;
                }
            }
        }
        //한글키보드의 경우
        else {
            if (mComposing.length() > 0 && (newSelStart != candidatesEnd
                    || newSelEnd != candidatesEnd) && isOkUpdateSelection) {
                mComposing.setLength(0);
//	            updateCandidates();
                Log.d("dz","4");
                clearHangul();
//                ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.finishComposingText();
                    //isCommitted = true;
                }
            }
            isOkUpdateSelection = true;
        }

        updateCandidateButton();// 후보자 추천 설정
    }

    /**
     * This tells us about completions that the editor has determined based
     * on the current text in it.  We want to use this in fullscreen mode
     * to show the completions ourself, since the editor can not be seen
     * in that situation.
     */
    //inputMethod가 디스플레이되기 원하는 자동완성 후보자를 보고할떄 호출됨
    //자동완성 리스트 적용인듯?
    @Override public void onDisplayCompletions(CompletionInfo[] completions) {
        if (mCompletionOn) {
            mCompletions = completions;
            if (completions == null) {
                setSuggestions(null, false, false);
                return;
            }

            List<String> stringList = new ArrayList<String>();
            for (int i=0; i<(completions != null ? completions.length : 0); i++) {
                CompletionInfo ci = completions[i];
                if (ci != null) stringList.add(ci.getText().toString());
            }
            setSuggestions(stringList, true, true);
        }
    }

    /**
     * This translates incoming hard key events in to edit operations on an
     * InputConnection.  It is only needed when using the
     * PROCESS_HARD_KEYS option.
     */
    //하드 키 이벤트를 InputConnection의 편집 작업으로 변환합니다. PROCESS_HARD_KEYS 옵션을 사용할 때만 필요합니다.
    private boolean translateKeyDown(int keyCode, KeyEvent event) {
        mMetaState = MetaKeyKeyListener.handleKeyDown(mMetaState,
                keyCode, event);
        int c = event.getUnicodeChar(MetaKeyKeyListener.getMetaState(mMetaState));
        mMetaState = MetaKeyKeyListener.adjustMetaAfterKeypress(mMetaState);
        InputConnection ic = getCurrentInputConnection();
        if (c == 0 || ic == null) {
            return false;
        }

        boolean dead = false;

        if ((c & KeyCharacterMap.COMBINING_ACCENT) != 0) {
            dead = true;
            c = c & KeyCharacterMap.COMBINING_ACCENT_MASK;
        }

        if (mComposing.length() > 0) {
            char accent = mComposing.charAt(mComposing.length() -1 );
            int composed = KeyEvent.getDeadChar(accent, c);

            if (composed != 0) {
                c = composed;
                mComposing.setLength(mComposing.length()-1);
            }
        }

        onKey(c, null);

        return true;
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    //앱에 전달되는 주요 이벤트를 모니터함(키 다운 이벤트)
    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.i("Hangul", "onKeyDown :" + Integer.toString(keyCode));

        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // The InputMethodService already takes care of the back
                // key for us, to dismiss the input method if it is shown.
                // However, our keyboard could be showing a pop-up window
                // that back should dismiss, so we first allow it to do that.
                // 백키 지만 팝업창을 먼저 닫아야 하는 경우??
                if (event.getRepeatCount() == 0 && mInputView != null) {
                    if (mInputView.handleBack()) {
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_DEL:
                // Special handling of the delete key: if we currently are
                // composing text for the user, we want to modify that instead
                // of let the application to the delete itself.
                //삭제 키의 특수 처리 : 현재 사용자의 텍스트를 작성중인 경우 응용 프로그램에 삭제를 허용하는 대신 해당 텍스트를 수정하려고합니다.
                if (mComposing.length() > 0) {
                    onKey(Keyboard.KEYCODE_DELETE, null);
                    return true;
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                // Let the underlying text editor always handle these.
                // 기본 텍스트편집기가 알아서 처리
                return false;

            default:
                // For all other keys, if we want to do transformations on
                // text being entered with a hard keyboard, we need to process
                // it and do the appropriate action.
                //하드키의 다른경우
                if (PROCESS_HARD_KEYS) {
                    if (keyCode == KeyEvent.KEYCODE_SPACE
                            && (event.getMetaState()&KeyEvent.META_ALT_ON) != 0) {
                        // A silly example: in our input method, Alt+Space
                        // is a shortcut for 'android' in lower case.
                        //바보 같은 예 : 우리의 입력 방법에서, Alt + Space는 소문자에서 'android'의 지름길입니다.
                        InputConnection ic = getCurrentInputConnection();
                        if (ic != null) {
                            // First, tell the editor that it is no longer in the
                            // shift state, since we are consuming this.
                            //먼저 우리가 이것을 사용하고 있기 때문에 더 이상 shift 상태에 있지 않다는 것을 에디터에게 알리십시오.
                            ic.clearMetaKeyStates(KeyEvent.META_ALT_ON);
                            keyDownUp(KeyEvent.KEYCODE_A);
                            keyDownUp(KeyEvent.KEYCODE_N);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            keyDownUp(KeyEvent.KEYCODE_R);
                            keyDownUp(KeyEvent.KEYCODE_O);
                            keyDownUp(KeyEvent.KEYCODE_I);
                            keyDownUp(KeyEvent.KEYCODE_D);
                            // And we consume this event.
                            return true;
                        }
                    }
                    if (mPredictionOn && translateKeyDown(keyCode, event)) {
                        return true;
                    }
                }
        }

        return super.onKeyDown(keyCode, event);
    }

    /**
     * Use this to monitor key events being delivered to the application.
     * We get first crack at them, and can either resume them or let them
     * continue to the app.
     */
    //어플에 전달되는 주요 이벤트를 모니터함.
    //첫번째 crack을 얻고, 재개하거나 app에 계속되게함
    @Override public boolean onKeyUp(int keyCode, KeyEvent event) {
        // If we want to do transformations on text being entered with a hard
        // keyboard, we need to process the up events to update the meta key
        // state we are tracking.
        //하드 키보드로 입력된 텍스트의 변형을 원할 경우 추적중인 메타 키 상태를 업데이트하기 위해 업 이벤트를 처리해야함
        if (PROCESS_HARD_KEYS) {
            if (mPredictionOn) {
                mMetaState = MetaKeyKeyListener.handleKeyUp(mMetaState,
                        keyCode, event);
            }
        }

        return super.onKeyUp(keyCode, event);
    }

    /**
     * Helper function to commit any text being composed in to the editor.
     */
    //editor로 작성중인 텍스트를 커밋하는 도우미 함수
    private void commitTyped(InputConnection inputConnection) {
        if (mComposing.length() > 0) {
            //텍스트 입력
            //inputConnection.commitText(mComposing, 1);// mComposing.length());
            inputConnection.finishComposingText();
            mComposing.setLength(0);
            //isCommitted=true;
            updateCandidates();
        }
    }

    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    //초기 editor상태를 기반으로 키보드의 shift상태를 업데이트하는 helper
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null
                && mInputView != null && mQwertyKeyboard == mInputView.getKeyboard()) {
            int caps = 0;
            EditorInfo ei = getCurrentInputEditorInfo();
            if (ei != null && ei.inputType != EditorInfo.TYPE_NULL) {
                caps = getCurrentInputConnection().getCursorCapsMode(attr.inputType);
            }
            mInputView.setShifted(mCapsLock || caps != 0);
        }
    }

    /**
     * Helper to determine if a given character code is alphabetic.
     */
    //주어진 문자 코드가 알파벳인지 판단하는 도우미.
    private boolean isAlphabet(int code) {
        if (Character.isLetter(code)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Helper to send a key down / key up pair to the current editor.
     */
    //도우미가 키 다운 / 키 업 쌍을 현재 에디터로 보냅니다.
    private void keyDownUp(int keyEventCode) {
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_DOWN, keyEventCode));
        getCurrentInputConnection().sendKeyEvent(
                new KeyEvent(KeyEvent.ACTION_UP, keyEventCode));
    }

    /**
     * Helper to send a character to the editor as raw key events.
     */
    // 에디터에 문자를 raw key events로 보내는 도우미.
    private void sendKey(int keyCode) {
        switch (keyCode) {
            case '\n':
                keyDownUp(KeyEvent.KEYCODE_ENTER);
                break;
            default:
                if (keyCode >= '0' && keyCode <= '9') {
                    keyDownUp(keyCode - '0' + KeyEvent.KEYCODE_0);
                } else {
                    getCurrentInputConnection().commitText(String.valueOf((char) keyCode), 1);
                }
                break;
        }
    }

    // Implementation of KeyboardViewListener
    //onKey 리스너
    public void onKey(int primaryCode, int[] keyCodes) {
        Log.i("Hangul", "onKey PrimaryCode[" + Integer.toString(primaryCode)+"]");

        //wordSeparator(\u0020.,;:!?\n()[]*&amp;@{}/&lt;&gt;_+=|&quot;)인 경우
        if (isWordSeparator(primaryCode)) {
            // Handle separator
            Keyboard current = mInputView.getKeyboard();

            if (mComposing.length() > 0) {
                commitTyped(getCurrentInputConnection());
            }
            if (current == mHangulKeyboard || current == mHangulShiftedKeyboard ) {
                Log.d("dz","3");
                clearHangul();
                sendKey(primaryCode);
//                isTextUpdate=true;
            }
//            else if (current == mSejongKeyboard) {
//                switch(ko_state_idx) {
//                    case KO_S_1110:
//                        if (mComposing.length() > 0) {
//                            mComposing.setLength(0);
//                            getCurrentInputConnection().finishComposingText();
//                        }
//                        clearSejong();
//                        break;
//                    default :
//                        clearSejong();
//                        sendKey(primaryCode);
//                        break;
//                }
//            }
            else {
                sendKey(primaryCode);
//                isTextUpdate=true;
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
            Log.d("헤헷","onUpdateSelection");
            //자동 오타수정 및 띄어쓰기
            //서버 데이터 전송 과부하를 막기 위한 delay 삽입
            long newSendTime = System.currentTimeMillis();
            if(newSendTime-oldSendTime>NETWORK_DELAY) {
                if (isAutoCorrect)
                    sendCorrectionJson();
                if (isAutoSpacing)
                    sendSpacingJson();
                oldSendTime = newSendTime;
            }

//            clearCorrectionBar(); //-*- 입력시 수정 bar 삭제 여부
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mHangulKeyboard || current == mHangulShiftedKeyboard ) {
                hangulSendKey(-2,HCURSOR_NONE);
                //isTextUpdate=true;
            }
//            else if (current == mSejongKeyboard) {
//                sendSejongKey((char)0,HCURSOR_DELETE);
//            }
            else {
                handleBackspace();
            }
//            clearCorrectionBar();//-*- 입력시 수정 bar 삭제 여부
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        }
//        else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
//            // Show a menu or somethin'
//
//            Intent i = new Intent(Intent.ACTION_VIEW);
//            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//            i.setData(Uri.parse("http://www.kandroid.com/market/product.php?id=1"));
//            startActivity(i);
//
//            return;
//        }
        else if ((primaryCode == Keyboard.KEYCODE_MODE_CHANGE || primaryCode == -7)
                && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsEnKeyboard || current == mSymbolsEnShiftedKeyboard) {
                current = mQwertyKeyboard;
            }
            // Hangul Start Code
            else if ((current == mQwertyKeyboard && primaryCode == Keyboard.KEYCODE_MODE_CHANGE)
                    || current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                if (mComposing.length() > 0) {
                    commitTyped(getCurrentInputConnection());
                }
                Log.d("dz","2");
                clearHangul();
                current = mHangulKeyboard;
            }
            else if(current == mQwertyKeyboard && primaryCode == -7){
                if (mComposing.length() > 0) {
                    commitTyped(getCurrentInputConnection());
                }
                current = mSymbolsEnKeyboard;
            }
            // Hangul End Code
            else if (current == mHangulKeyboard || current == mHangulShiftedKeyboard) {
                if (mComposing.length() > 0) {
                    getCurrentInputConnection().commitText(mComposing, 1);//mComposing.length());
                    mComposing.setLength(0);
                }
//                clearSejong();
//                current = mSejongKeyboard;
                if(primaryCode == Keyboard.KEYCODE_MODE_CHANGE)
                    current = mQwertyKeyboard;
                else
                    current = mSymbolsKeyboard;
            }
            else {
                if (mComposing.length() > 0) {
                    getCurrentInputConnection().commitText(mComposing, 1);//mComposing.length());
                    mComposing.setLength(0);
                }
                Log.d("dz","1");
                clearHangul();
                current = mSymbolsKeyboard;
            }
            mInputView.setKeyboard(current);
            if (current == mSymbolsKeyboard || current == mSymbolsEnKeyboard || current == mHangulKeyboard || current == mQwertyKeyboard) {
                current.setShifted(false);
            }

//            if (current == mSejongKeyboard) {
//                mInputView.setPreviewEnabled(false);
//            }
//            else {
//            }
            mCurrentKeyboard = mInputView.getKeyboard();
        }
        //키보드 옵션 클릭
        else if(primaryCode == CODE_OPTION_VIEW) {
            Keyboard current = mInputView.getKeyboard();
            Log.d("what?","good");
            if(current != mOptionKeyboard) {
                if (mComposing.length() > 0) {
                    getCurrentInputConnection().commitText(mComposing, 1);//mComposing.length());
                    mComposing.setLength(0);
                }
                clearHangul();
                mCurrentKeyboard = current;
                current = mOptionKeyboard;
                mInputView.setKeyboard(current);
                mInputView.getKeyboard().getKeys().get(0).on = isAutoCorrect;
                mInputView.getKeyboard().getKeys().get(1).on = isAutoSpacing;
            }
            else {
                current = mCurrentKeyboard;
                mInputView.setKeyboard(current);
            }
        }
        else if(primaryCode == CODE_AUTO_CORRECTION)
        {
            isAutoCorrect = !isAutoCorrect;
        }
        else if(primaryCode == CODE_AUTO_SPACING)
        {
            isAutoSpacing = !isAutoSpacing;
        }
        else {

            // Hangul Start Code
            Keyboard current = mInputView.getKeyboard();
            if (current == mHangulKeyboard || current == mHangulShiftedKeyboard) {
                handleHangul(primaryCode, keyCodes);
            }
//            else if (current == mSejongKeyboard) {
//                handleSejong(primaryCode, keyCodes);
//            }
            else {
                handleCharacter(primaryCode, keyCodes);
            }
            // Hangul End Code
            //자동 오타수정 및 띄어쓰기
            //서버 데이터 전송 과부하를 막기 위한 delay 삽입
            long newSendTime = System.currentTimeMillis();
            if(newSendTime-oldSendTime>NETWORK_DELAY) {
                if (isAutoCorrect)
                    sendCorrectionJson();
                if (isAutoSpacing)
                    sendSpacingJson();
                oldSendTime = newSendTime;
            }
//            clearCorrectionBar();//-*- 입력시 수정 bar 삭제 여부
        }
        if(mComposing.length()>0)
            updateCandidateComposing();
        updateCandidateButton();// 후보자 추천 설정
        Log.d("current keyboard",mInputView.getKeyboard().toString());
    }

    public void onText(CharSequence text) {
        InputConnection ic = getCurrentInputConnection();
        if (ic == null) return;
        ic.beginBatchEdit();
        if (mComposing.length() > 0) {
            commitTyped(ic);
        }
        ic.commitText(text, 0);
        ic.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    /**
     * Update the list of available candidates from the current composing
     * text.  This will need to be filled in by however you are determining
     * candidates.
     */
    private void updateCandidates() {
        if (!mCompletionOn) {
            if (mComposing.length() > 0) {
                ArrayList<String> list = new ArrayList<String>();
                list.add(mComposing.toString());
                setSuggestions(list, true, true);
            } else {
                setSuggestions(null, false, false);
            }
        }
    }

    //자동 완성 설정
    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        //단어 추천 갱신
//        if (mCandidateView != null) {
//            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
//        }
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
            //isTextUpdate=true;
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
            //isTextUpdate=true;
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
            //isTextUpdate=true;
        }
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    private void handleShift() {
        if (mInputView == null) {
            return;
        }

        Keyboard currentKeyboard = mInputView.getKeyboard();
        if (mQwertyKeyboard == currentKeyboard) {
            // Alphabet keyboard
            checkToggleCapsLock();
            mInputView.setShifted(mCapsLock || !mInputView.isShifted());
        }
        // Hangul Code Start
        else if (currentKeyboard == mHangulKeyboard) {
            mHangulKeyboard.setShifted(true);
            mInputView.setKeyboard(mHangulShiftedKeyboard);
            mHangulShiftedKeyboard.setShifted(true);
            mHangulShiftState = 1;
        } else if (currentKeyboard == mHangulShiftedKeyboard) {
            mHangulShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mHangulKeyboard);
            mHangulKeyboard.setShifted(false);
            mHangulShiftState = 0;
        }
        // Hangul Code End
        else if (currentKeyboard == mSejongKeyboard) {

        }
        else if (currentKeyboard == mSymbolsKeyboard) {
            mSymbolsKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsShiftedKeyboard);
            mSymbolsShiftedKeyboard.setShifted(true);
        }else if (currentKeyboard == mSymbolsEnKeyboard) {
            mSymbolsEnKeyboard.setShifted(true);
            mInputView.setKeyboard(mSymbolsEnShiftedKeyboard);
            mSymbolsEnShiftedKeyboard.setShifted(true);
        }
        else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
        }
        else if (currentKeyboard == mSymbolsEnShiftedKeyboard) {
            mSymbolsEnShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsEnKeyboard);
            mSymbolsEnKeyboard.setShifted(false);
        }
    }

// Hangul Code Start

    private int isHangulKey(int stack_pos, int new_key) {
        if (stack_pos != 2) {
            switch (mHangulKeyStack[stack_pos]) {
                case 0:
                    if (new_key == 20) return 2;
                    break;
                case 3:
                    if (new_key == 23) return 4;
                    else if(new_key == 29) return 5;
                    break;
                case 8:
                    if (new_key == 0)return 9;
                    else if (new_key == 16) return 10;
                    else if (new_key == 17) return 11;
                    else if (new_key == 20) return 12;
                    else if (new_key == 27) return 13;
                    else if (new_key == 28) return 14;
                    else if (new_key == 29) return 15;
                    break;
                case 17:
                    if (new_key == 20) return 19;
                    break;
            }
        }
        else {
            switch (mHangulKeyStack[stack_pos]) {
                case 38:
                    if (new_key == 30) return 39;
                    else if (new_key == 31) return 40;
                    else if (new_key == 50) return 41;
                    break;
                case 43:
                    if (new_key == 34) return 44;
                    else if (new_key == 35) return 45;
                    else if (new_key == 50) return 46;
                    break;
                case 48:
                    if (new_key == 50) return 49;
                    break;
            }
        }
        return 0;
    }

    private static char HCURSOR_NONE = 0;
    private static char HCURSOR_NEW = 1;
    private static char HCURSOR_ADD = 2;
    private static char HCURSOR_UPDATE = 3;
    private static char HCURSOR_APPEND = 4;
    private static char HCURSOR_UPDATE_LAST = 5;
    private static char HCURSOR_DELETE_LAST = 6;
    private static char HCURSOR_DELETE = 7;


    //한글키보드의 경우, 커서가 업데이트 될때 해당 함수 내용을 실행할것인지 여부 (한글 자모 조합을 위해 setComposingText를 유지하기 위함)
    private boolean isOkUpdateSelection = true;
    //텍스트값 업데이트(변경) 여부 확인(오타 수정에서 커서 추적을 위함)
    //private boolean isTextUpdate = false;
    //커밋 여부 확인
    //private boolean isCommitted = false;
    //자동완성단어로 교체될 단어의 시작/끝위치(길이 2) 배열
    private int candidateWordPosArray[] = new int[2];
    //자동완성단어로 교체될 Old 단어
    private String candidateOldWord;
    private static int mHCursorState = HCURSOR_NONE;
    private static char h_char[] = new char[1];
    private int previousCurPos = -2;
    private int previousHangulCurPos = -1;
    private int mHangulShiftState = 0;
    private int mHangulState = 0;
    private static int mHangulKeyStack[] = {0,0,0,0,0,0};
    private static int mHangulJamoStack[] = {0,0,0};
    final static int H_STATE_0 = 0;
    final static int H_STATE_1 = 1;
    final static int H_STATE_2 = 2;
    final static int H_STATE_3 = 3;
    final static int H_STATE_4 = 4;
    final static int H_STATE_5 = 5;
    final static int H_STATE_6 = 6;
    final static char[] h_chosung_idx =
            {0,1, 9,2,12,18,3, 4,5, 0, 6,7, 9,16,17,18,6, 7, 8, 9,9,10,11,12,13,14,15,16,17,18};

    final static char[] h_jongsung_idx =
            {0, 1, 2, 3,4,5, 6, 7, 0,8, 9,10,11,12,13,14,15,16,17,0 ,18,19,20,21,22,0 ,23,24,25,26,27};

    final static int[] e2h_map =
            {16,47,25,22,6, 8,29,38,32,34,30,50,48,43,31,35,17,0, 3,20,36,28,23,27,42,26,
                    16,47,25,22,7, 8,29,38,32,34,30,50,48,43,33,37,18,1, 3,21,36,28,24,27,42,26};

    private void clearHangul() {
        mHCursorState = HCURSOR_NONE;
        mHangulState = 0;
        previousHangulCurPos = -1;
        mHangulKeyStack[0] = 0;
        mHangulKeyStack[1] = 0;
        mHangulKeyStack[2] = 0;
        mHangulKeyStack[3] = 0;
        mHangulKeyStack[4] = 0;
        mHangulKeyStack[5] = 0;
        mHangulJamoStack[0] = 0;
        mHangulJamoStack[1] = 0;
        mHangulJamoStack[2] = 0;
        Log.d("ggg","zzzzz");
        return;
    }

    private void hangulSendKey(int newHangulChar, int hCursor) {

        if (hCursor == HCURSOR_NEW) {
            Log.i("Hangul", "HCURSOR_NEW");

            mComposing.append((char)newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            //isTextUpdate=true;
            mHCursorState = HCURSOR_NEW;
        }
        else if (hCursor == HCURSOR_ADD) {
            mHCursorState = HCURSOR_ADD;
            Log.i("Hangul", "HCURSOR_ADD");
//            if (mComposing.length() > 0) {
//                mComposing.setLength(0);
//                getCurrentInputConnection().finishComposingText();
//                isOkUpdateSelection = false;
//            }

            mComposing.append((char)newHangulChar);
            Log.d("length",mComposing.toString());
            getCurrentInputConnection().setComposingText(mComposing, 1);
            //isTextUpdate=true;
        }
        else if (hCursor == HCURSOR_UPDATE) {
            Log.i("Hangul", "HCURSOR_UPDATE");
            if(mComposing.length()>0)
                mComposing.setCharAt(mComposing.length()-1, (char)newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            mHCursorState = HCURSOR_UPDATE;
            Log.d("length", mComposing.toString());
        }
        else if (hCursor == HCURSOR_APPEND) {
            Log.i("Hangul", "HCURSOR_APPEND");
            mComposing.append((char)newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            mHCursorState = HCURSOR_APPEND;
        }
        else if (hCursor == HCURSOR_NONE) {
            if (newHangulChar == -1) {
                Log.i("Hangul", "HCURSOR_NONE [DEL -1]");
                keyDownUp(KeyEvent.KEYCODE_DEL);
                Log.d("dz","8");
                clearHangul();
            }
            else if (newHangulChar == -2) {
                int hangulKeyIdx;
                int cho_idx,jung_idx,jong_idx;

                Log.i("Hangul", "HCURSOR_NONE [DEL -2]");

                switch(mHangulState) {
                    case H_STATE_0:
//                        if(mComposing.length()>0) {
//                           // mComposing.setLength(mComposing.length() - 1);
//                            getCurrentInputConnection().setComposingText(mComposing, 1);
//                        }
                        if(mComposing.length()>0) {
                            mComposing.delete(mComposing.length()-1,mComposing.length());
                            //isTextUpdate=true;
                            getCurrentInputConnection().setComposingText(mComposing, 1);
                        }
                        else{
                            getCurrentInputConnection().commitText("", 0);
                            clearHangul();
                            keyDownUp(KeyEvent.KEYCODE_DEL);
                            //isTextUpdate=true;
                        }
                        break;
                    case H_STATE_1: // �ʼ�
//					keyDownUp(KeyEvent.KEYCODE_DEL);
                        mComposing.setLength(mComposing.length()-1);//
                        getCurrentInputConnection().setComposingText(mComposing, 1);
                        //isTextUpdate=true;
                        Log.d("dz","7");
                        if(mComposing.length() == 0) {
                            getCurrentInputConnection().commitText("", 0);
                            clearHangul();
                        }
                        mHangulState = H_STATE_0;
                        break;
                    case H_STATE_2:
                        newHangulChar = 0x3131 + mHangulKeyStack[0];
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                        mHangulKeyStack[1] = 0;
                        mHangulJamoStack[0] = mHangulKeyStack[0];
                        mHangulState = H_STATE_1;
                        break;
                    case H_STATE_3:
                        if (mHangulKeyStack[3] == 0) {
//						keyDownUp(KeyEvent.KEYCODE_DEL);
                            mComposing.setLength(mComposing.length()-1);
                            getCurrentInputConnection().setComposingText(mComposing, 1);
                            //isTextUpdate=true;
                            Log.d("dz","6");
                            if(mComposing.length() == 0) {
                                getCurrentInputConnection().commitText("", 0);
                                clearHangul();
                            }
                            mHangulState = H_STATE_0;
                        }
                        else {
                            mHangulKeyStack[3] = 0;
                            newHangulChar = 0x314F + (mHangulKeyStack[2] - 30);
                            hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                            mHangulJamoStack[1] = mHangulKeyStack[2];
                            mHangulState = H_STATE_3; // goto �߼�
                        }
                        break;
                    case H_STATE_4:
                        if (mHangulKeyStack[3] == 0) {
                            mHangulKeyStack[2] = 0;
                            mHangulJamoStack[1] = 0;
                            newHangulChar = 0x3131 + mHangulJamoStack[0];
                            hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                            mHangulState = H_STATE_1; // goto �ʼ�
                        }
                        else {
                            mHangulJamoStack[1]= mHangulKeyStack[2];
                            mHangulKeyStack[3] = 0;
                            cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                            jung_idx = mHangulJamoStack[1] - 30;
                            jong_idx = h_jongsung_idx[mHangulJamoStack[2]];
                            newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                            hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                        }
                        break;
                    case H_STATE_5:
                        mHangulJamoStack[2] = 0;
                        mHangulKeyStack[4] = 0;
                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = h_jongsung_idx[mHangulJamoStack[2]];
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                        mHangulState = H_STATE_4;
                        break;
                    case H_STATE_6:
                        mHangulKeyStack[5] = 0;
                        mHangulJamoStack[2] = mHangulKeyStack[4];
                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = h_jongsung_idx[mHangulJamoStack[2]+1];;
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                        mHangulState = H_STATE_5;
                        break;
                }
            }
            else if (newHangulChar == -3) {
                Log.i("Hangul", "HCURSOR_NONE [DEL -3]");
                final int length = mComposing.length();
                if (length > 1) {
                    mComposing.delete(length - 1, length);
                    //isTextUpdate=true;
                }
            }

        }
    }

    private int prevJaumKeyCode = 0;


    final private char ko_first_state[] = {
            1,3,4,6,7,8,10,12,13,	// 0
            16,3,4,6,7,8,10,12,13,	// 1
            1,3,4,6,7,8,10,12,13,	// 2
            1,3,4,6,7,8,10,12,13,	// 3
            1,3,17,6,7,8,10,12,13,	// 4
            1,3,4,6,7,8,10,12,13,	// 5
            1,3,4,6,7,8,10,12,13,	// 6
            1,3,4,6,7,8,10,12,13,	// 7
            1,3,4,6,7,18,10,12,13,	// 8
            1,3,4,6,7,8,10,12,13,	// 9
            1,3,4,6,7,8,11,12,13,	// 10
            1,3,4,6,7,8,10,12,13,	// 11
            1,3,4,6,7,8,10,19,13,	// 12
            1,3,4,6,7,8,10,12,15,	// 13
            1,3,4,6,7,8,10,12,13,	// 14
            1,3,4,6,7,8,10,12,14,	// 15
            2,3,4,6,7,8,10,12,13,	// 16
            1,3,5,6,7,8,10,12,13,	// 17
            1,3,4,6,7,9,10,12,13,	// 18
            1,3,4,6,7,8,10,12,13,	// 19
    };

    final private char ko_middle_state[] = {
//		 	0			   1			     2		   	3			    4			    5		    	6			    7				
            23,1,21,	7,2,11,		9,1,15,		4,5,0, 		0,0,0, 		6,3,0, 		0,0,0,		8,0,0,
//		 	8			    9		     	10		   	11			  12		   	13			  14		   	15
            0,0,0,		10,0,0,		0,0,0,		14,0,0,		13,0,0,		0,0,0,		0,12,0,		0,0,0,
//			16			  17			  18			  19			  20			  21			  22		  	23
            19,20,0,	18,0,0,		0,0,0,		0,0,0,		17,16,0,	22,16,0,	0,0,0,		0,3,0
    };

    final private char ko_last_state[] = {
            1,4,7,8,16,17,19,21,22,	// 0
            24,0,0,0,0,0,3,0,0,		// 1
            1,0,0,0,0,0,0,0,0,		// 2
            0,0,0,0,0,0,31,0,0,		// 3
            0,0,0,0,0,0,0,32,5,		// 4
            0,0,0,0,0,0,0,0,33,		// 5
            0,0,0,0,0,0,0,0,0,		// 6
            0,0,25,0,0,0,0,0,0,		// 7
            9,0,44,0,0,11,12,43,0,		// 8
            36,0,0,0,0,0,0,0,0,		// 9
            0,0,0,0,0,0,0,0,0,		// 10
            0,0,0,0,0,14,0,0,0,		// 11
            0,0,0,0,0,0,39,0,0,		// 12
            0,0,45,0,0,0,0,0,0,		// 13
            0,0,0,0,0,38,0,0,0,		// 14
            0,0,0,0,0,0,0,43,0,		// 15
            0,0,0,0,0,0,0,0,0,		// 16
            0,0,0,0,0,26,18,0,0,		// 17
            0,0,0,0,0,0,41,0,0,		// 18
            0,0,0,0,0,0,20,0,0,		// 19
            0,0,0,0,0,0,19,0,0,		// 20
            0,0,0,0,0,0,0,0,0,		// 21
            0,0,0,0,0,0,0,0,23,		// 22
            0,0,0,0,0,0,0,0,42,		// 23
            2,0,0,0,0,0,0,0,0,		// 24
            0,0,28,0,0,0,0,0,0,		// 25
            0,0,0,0,0,29,0,0,0,		// 26
            0,0,0,0,0,0,0,0,0,		// 27
            0,0,7,0,0,0,0,0,0,		// 28
            0,0,0,0,0,17,0,0,0,		// 29
            0,0,0,0,0,0,0,0,0,		// 30
            0,0,0,0,0,0,3,0,0,		// 31
            0,0,0,0,0,0,0,35,0,		// 32
            0,0,0,0,0,0,0,0,34,		// 33
            0,0,0,0,0,0,0,0,5,		// 34
            0,0,0,0,0,0,0,32,0,		// 35
            37,0,0,0,0,0,0,0,0,		// 36
            9,0,0,0,0,0,0,0,0,		// 37
            0,0,0,0,0,11,0,0,0,		// 38
            0,0,0,0,0,0,12,0,0,		// 39
            0,0,0,0,0,0,0,0,0,		// 40
            0,0,0,0,0,0,18,0,0,		// 41
            0,0,0,0,0,0,0,0,22,		// 42
            0,0,0,0,0,0,0,15,0,		// 43
            0,0,13,0,0,0,0,0,0,		// 44
            0,0,44,0,0,0,0,0,0,		// 45
    };

    final private char ko_jong_m_split[] = {
            0,1, 0,2, 1,10, 0,3, 4,13,
            4,19, 0,4, 0,6, 8,1, 8,7,
            8,8, 8,10, 8,17, 8,18, 8,19,
            0,7,0,8,17,10,0,10,0,11,
            0,12,0,13,0,15,0,16,0,17,
            0,18,0,19
    };

    final private char ko_jong_l_split[] = {
            0,5,0,9,
            1,19,1,11,
            4,12,4,15,4,14,4,19,
            8,16,8,2,8,9,8,11,
            17,19,17,11,
            0,14,
            8,12,8,4,8,5
    };

    final private char jongsung_28idx[] = {
            0, 0, 1, 1, 4, 4, 4, 8, 8, 8, 8, 17, 17, 0, 8, 8, 8
    };

    final static char KO_S_0000 = 0;
    final static char KO_S_0100 = 1;
    final static char KO_S_1000 = 2;
    final static char KO_S_1100 = 3;
    final static char KO_S_1110 = 4;
    final static char KO_S_1111 = 5;

//    private void clearSejong() {
//        ko_state_idx = KO_S_0000;
//        ko_state_first_idx = 0;
//        ko_state_middle_idx = 0;
//        ko_state_last_idx = 0;
//        ko_state_next_idx = 0;
//        prev_key = -1;
//        return;
//    }

    private int prev_key = -1;
    private char ko_state_idx = KO_S_0000;
    private char ko_state_first_idx;
    private char ko_state_middle_idx;
    private char ko_state_last_idx;
    private char ko_state_next_idx;


    final private int key_idx[] =
            {0, 1, 2, 3, 4, 5, 6, 7, 8,0,1,2,};
    // ��,��,��,��,��,��,��, ��,��,��,��,��,


    final private char chosung_code[] = {
            0, 1, 3, 6, 7, 8, 16, 17, 18, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29
    };
    final private char jongsung_code[] = {
            0,1,2,3,4,5,6,  // ��
            8,9,10,11,12,13,14,15,16,17, // ��
            19,20,21,22,23, // ��
            25,26,27,28,29
    };

    final private char jungsung_stack[] = {
            // 1  2 3  4  5  6  7 8  9  10 11 12 13 14 15 16 17 18 19 20 21 22 23
            // . .. ��,��,��,��,��,��,��,��,��,��,��, ��,��,��,��,��,��,��,��,��, ��
            0,0, 0, 0, 0, 0, 0, 0,0, 0, 0, 11,11,11, 0, 0,16,16,16,0, 0, 21, 0
    };

    private void sendSejongKey(char newHangulChar, char hCursor) {

        Log.i("Hangul", "newHangulChar[" + Integer.toString(newHangulChar) + "]");

        if (hCursor == HCURSOR_NEW) {
            Log.i("Hangul", "HCURSOR_NEW");

            mComposing.append(newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            mHCursorState = HCURSOR_NEW;
        }
        else if (hCursor == HCURSOR_ADD) {
            mHCursorState = HCURSOR_ADD;
            Log.i("Hangul", "HCURSOR_ADD");
            if (mComposing.length() > 0) {
                mComposing.setLength(0);
                getCurrentInputConnection().finishComposingText();
                //isCommitted = true;
            }

            mComposing.append(newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
        }
        else if (hCursor == HCURSOR_UPDATE) {
            Log.i("Hangul", "HCURSOR_UPDATE");
            mComposing.setCharAt(0, newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            mHCursorState = HCURSOR_UPDATE;
        }
        else if (hCursor == HCURSOR_APPEND) {
            Log.i("Hangul", "HCURSOR_APPEND");
            mComposing.append(newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            mHCursorState = HCURSOR_APPEND;
        }
        else if (hCursor == HCURSOR_UPDATE_LAST) {
            Log.i("Hangul", "HCURSOR_UPDATE_LAST");
            mComposing.setCharAt(1, newHangulChar);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            mHCursorState = HCURSOR_UPDATE_LAST;
        }
        else if (hCursor == HCURSOR_DELETE_LAST) {
            Log.i("Hangul", "HCURSOR_DELETE_LAST");
            final int length = mComposing.length();
            if (length > 1) {
                Log.i("Hangul", "Delete start :" + Integer.toString(length));
                mComposing.delete(length - 1, length);
                getCurrentInputConnection().setComposingText(mComposing, 1);
            }
        }
        else if (hCursor == HCURSOR_DELETE) {
            char hChar;
            char cho_idx, jung_idx, jong_idx;
            switch(ko_state_idx) {
                case KO_S_0000:
                case KO_S_0100:
                case KO_S_1000:
                    keyDownUp(KeyEvent.KEYCODE_DEL);
                    clearHangul();
                    break;
                case KO_S_1100:
                    ko_state_middle_idx = jungsung_stack[ko_state_middle_idx - 1];
                    if (ko_state_middle_idx > 0) {
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        hChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                        sendSejongKey(hChar, HCURSOR_UPDATE);
                    }
                    else {
                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                        ko_state_idx = KO_S_1000;
                    }
                    break;
                case KO_S_1110:
                    cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                    jung_idx = (char)(ko_state_middle_idx - 3);
                    hChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                    sendSejongKey(hChar, HCURSOR_UPDATE);
                    ko_state_last_idx = 0;
                    ko_state_idx = KO_S_1110;
                    break;
                case KO_S_1111:
                    ko_state_last_idx = jongsung_28idx[ko_state_last_idx - 28];
                    sendSejongKey((char)0,HCURSOR_DELETE_LAST);
                    ko_state_next_idx = 0;
                    ko_state_idx = KO_S_1110;
                    break;
            }
        }

    }

    private char getJungsungCode(char jungsung_idx)
    {
        Log.i("Hangul", "getJungsungCode[" + Integer.toString(jungsung_idx) + "]");

        switch(jungsung_idx) {
            case 1:
                return 0xB7;
            case 2:
                return 0x3A;
            default :
                return  (char)(0x314F + jungsung_idx - 3);
        }
    }

//    private void handleSejong(int primaryCode, int[] keyCodes) {
//
//        char new_last_idx;
//        int base_idx;
//        char new_state_idx;
//        int idx;
//        char newHangulChar;
//        char cho_idx, jung_idx, jong_idx;
//        int key = primaryCode;
//
//        base_idx = primaryCode - 0x41;
//        idx = key_idx[base_idx];
//
//        Log.i("Hangul", "state[" + Integer.toString(ko_state_idx) + "]" + "["
//                + Integer.toString(ko_state_first_idx) + ","
//                + Integer.toString(ko_state_middle_idx) + ","
//                + Integer.toString(ko_state_last_idx) + ","
//                + Integer.toString(ko_state_next_idx) + "]"
//        );
//        Log.i("Hangul", "base_idx,idx[" + Integer.toString(base_idx) +
//                ":" + Integer.toString(idx) + "]");
//        if (base_idx < 9) {
//            switch(ko_state_idx) {
//                case KO_S_0000: // clear
//                    ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
//                    newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                    sendSejongKey(newHangulChar, HCURSOR_NEW);
//                    ko_state_idx = KO_S_1000;
//                    break;
//                case KO_S_0100:
//                    ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
//                    ko_state_middle_idx = 0;
//                    newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                    sendSejongKey(newHangulChar, HCURSOR_NEW);
//                    ko_state_idx = KO_S_1000;
//                    break;
//                case KO_S_1000:
//                    if (key == prev_key) {
//                        new_state_idx = ko_first_state[ko_state_first_idx * 9 + idx];
//                        if (new_state_idx == ko_state_first_idx) {
//                            newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                            sendSejongKey(newHangulChar, HCURSOR_ADD);
//                        }
//                        else {
//                            ko_state_first_idx = new_state_idx;
//                            newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                            sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//                        }
//                    }
//                    else {
//                        ko_state_first_idx = ko_first_state[idx];
//                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                    }
//                    break;
//                case KO_S_1100: // �ʼ�,�߼�
//                    ko_state_last_idx = ko_last_state[ko_state_last_idx * 9 + idx];
//                    Log.i("Hangul","ko_state_last_idx[" + Integer.toString(ko_state_last_idx) + "]");
//                    if (ko_state_middle_idx > 2) {
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        jong_idx = h_jongsung_idx[jongsung_code[ko_state_last_idx - 1]+1];
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//                        ko_state_idx = KO_S_1110;
//                    }
//                    else {
//                        Log.i("Hangul", "Not Combination...");
//                        // must be implemented.
//                    }
//                    break;
//                case KO_S_1110:
//                    new_last_idx = ko_last_state[ko_state_last_idx * 9 + idx];
//
//                    if(new_last_idx >= 28) {
//                        ko_state_last_idx = new_last_idx;
//                        ko_state_next_idx
//                                = ko_jong_l_split[(new_last_idx - 28) * 2 + 1];
//
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        char split_last_idx = jongsung_28idx[ko_state_last_idx - 28];
//                        Log.i("Hangul", "split_last_idx[" + Integer.toString(split_last_idx) + "]");
//                        if (split_last_idx > 0) {
////	 	               Log.i("Hangul", "jongsung_code[" + Integer.toString(jongsung_code[split_last_idx-1]) + "]");
////	    	            Log.i("Hangul", "jong_idx[" + Integer.toString(h_jongsung_idx[jongsung_code[split_last_idx - 1]+1]) + "]");
//                            jong_idx = h_jongsung_idx[jongsung_code[split_last_idx - 1]+1];
//                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
//                        }
//                        else {
//                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
//                        }
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//
//                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_next_idx - 1]);
//                        sendSejongKey(newHangulChar, HCURSOR_APPEND);
//
//                        ko_state_idx = KO_S_1111;
//                    }
//                    else if (new_last_idx == 0) {
//
//                        ko_state_first_idx = 0;
//                        ko_state_middle_idx = 0;
//                        ko_state_last_idx = 0;
//
//                        idx = key_idx[base_idx];
//                        ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
//                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                        ko_state_idx = KO_S_1000;
//                    }
//                    else {
//                        ko_state_last_idx = new_last_idx;
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        jong_idx = h_jongsung_idx[jongsung_code[ko_state_last_idx - 1]+1];
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//                    }
//
//                    break;
//                case KO_S_1111:
//                    new_last_idx = ko_last_state[ko_state_last_idx * 9 + idx];
//
//                    if(new_last_idx >= 28) {
//                        ko_state_next_idx
//                                = ko_jong_l_split[(new_last_idx - 28) * 2 + 1];
//                        ko_state_last_idx = new_last_idx;
//
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        char split_last_idx = jongsung_28idx[ko_state_last_idx - 28];
//                        jong_idx = h_jongsung_idx[jongsung_code[split_last_idx - 1]+1];
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//
//                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_next_idx - 1]);
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE_LAST);
//
//                        ko_state_idx = KO_S_1111;
//                    }
//                    else {
//                        if (prev_key == key) {
//                            ko_state_last_idx = new_last_idx;
//                            // delete last cursor
//                            ko_state_next_idx = 0;
//
//                            cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                            jung_idx = (char)(ko_state_middle_idx - 3);
//                            jong_idx = h_jongsung_idx[jongsung_code[ko_state_last_idx - 1]+1];
//                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
//                            sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//
//                            sendSejongKey((char)0,HCURSOR_DELETE_LAST);
//                            ko_state_idx = KO_S_1110;
//                        }
//                        else {
//                            clearSejong();
//                            ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
//                            newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                            sendSejongKey(newHangulChar, HCURSOR_ADD);
//                            ko_state_idx = KO_S_1000;
//                        }
//                    }
//                    break;
//
//            }
//        }
//        else {
//            switch(ko_state_idx) {
//
//                case KO_S_0000:
//                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//                    newHangulChar = getJungsungCode(ko_state_middle_idx);
//                    sendSejongKey(newHangulChar, HCURSOR_NEW);
//                    ko_state_idx = KO_S_0100;
//                    break;
//                case KO_S_0100:
//                    if (ko_middle_state[ko_state_middle_idx * 3 + idx] == 0) {
//                        ko_state_middle_idx = 0;
//                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//                        newHangulChar = getJungsungCode(ko_state_middle_idx);
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                    }
//                    else {
//                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//                        newHangulChar = getJungsungCode(ko_state_middle_idx);
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//                    }
//                    break;
//                case KO_S_1000:
//                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//
//                    if (ko_state_middle_idx > 2) {
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//                    }
//                    else {
//                        newHangulChar = getJungsungCode(ko_state_middle_idx);
//                        sendSejongKey(newHangulChar, HCURSOR_APPEND);
//                        // must be implemented.
//                    }
//                    ko_state_idx = KO_S_1100;
//                    break;
//                case KO_S_1100:
//                    if (ko_middle_state[ko_state_middle_idx * 3 + idx] == 0) {
//                        ko_state_first_idx = 0;
//                        ko_state_middle_idx = 0;
//                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//                        newHangulChar = getJungsungCode(ko_state_middle_idx);
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                        ko_state_idx = KO_S_0100;
//                    }
//                    else {
//                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//                        if (ko_state_middle_idx > 2) {
//                            cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                            jung_idx = (char)(ko_state_middle_idx - 3);
//                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
//                            sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//                            sendSejongKey((char)0,HCURSOR_DELETE_LAST);
//                        }
//                        else {
//                            newHangulChar = getJungsungCode(ko_state_middle_idx);
//                            sendSejongKey(newHangulChar, HCURSOR_UPDATE_LAST);
//                        }
//                    }
//
//                    break;
//                case KO_S_1110:
//                    if (ko_jong_m_split[(ko_state_last_idx - 1) * 2] > 0) {
//                        // update jongsong
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        jong_idx = h_jongsung_idx[jongsung_code[ko_jong_m_split[(ko_state_last_idx - 1) * 2]]];
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//
//                    }
//                    else {
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
//                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
//                    }
//
//                    ko_state_first_idx
//                            = ko_jong_m_split[(ko_state_last_idx - 1) * 2 + 1];
//                    ko_state_middle_idx = 0;
//                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//
//                    if (ko_state_middle_idx > 2) {
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                        ko_state_idx = KO_S_1100;
//                    }
//                    else {
//                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                        newHangulChar = getJungsungCode(ko_state_middle_idx);
//                        sendSejongKey(newHangulChar, HCURSOR_APPEND);
//                        // must be implemented.
//                    }
//
//                    ko_state_last_idx = 0;
//                    ko_state_idx = KO_S_1100;
//                    break;
//                case KO_S_1111:
//                    if (ko_state_last_idx >= 28) {
//                        ko_state_last_idx = jongsung_28idx[ko_state_last_idx - 28];
//                    }
//
//                    ko_state_first_idx = ko_state_next_idx;
//                    ko_state_middle_idx = 0;
//                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
//
//                    sendSejongKey((char)0,HCURSOR_DELETE_LAST);
//
//                    if (ko_state_middle_idx > 2) {
//                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
//                        jung_idx = (char)(ko_state_middle_idx - 3);
//                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                        ko_state_idx = KO_S_1100;
//                    }
//                    else {
//                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
//                        sendSejongKey(newHangulChar, HCURSOR_ADD);
//                        newHangulChar = getJungsungCode(ko_state_middle_idx);
//                        sendSejongKey(newHangulChar, HCURSOR_APPEND);
//                    }
//
//                    ko_state_last_idx = 0;
//                    ko_state_next_idx = 0;
//                    ko_state_idx = KO_S_1100;
//                    break;
//
//            }
//        }
//
//        prev_key = key;
//    }


    private void handleHangul(int primaryCode, int[] keyCodes) {

        int hangulKeyIdx = -1;
        int newHangulChar;
        int cho_idx,jung_idx,jong_idx;
        int hangulChar = 0;

        //한글 범위 안
        if (primaryCode >= 0x61 && primaryCode <= 0x7A) {

            if (mHangulShiftState == 0) {
                hangulKeyIdx = e2h_map[primaryCode - 0x61];
            }
            else {
                hangulKeyIdx = e2h_map[primaryCode - 0x61 + 26];
                mHangulShiftedKeyboard.setShifted(false);
                mInputView.setKeyboard(mHangulKeyboard);
                mHangulKeyboard.setShifted(false);
                mHangulShiftState = 0;
            }
            hangulChar = 1;
        }
        else if (primaryCode >= 0x41 && primaryCode <= 0x5A) {
            hangulKeyIdx = e2h_map[primaryCode - 0x41 + 26];
            hangulChar = 1;
        }
        /*
        else  if (primaryCode >= 0x3131 && primaryCode <= 0x3163) {
        	hangulKeyIdx = primaryCode - 0x3131;
        	hangulChar = 1;
        }
        */
        else {
            hangulChar = 0;
        }


        if (hangulChar == 1) {

            switch(mHangulState) {

                case H_STATE_0: // Hangul Clear State
                    Log.i("SoftKey", "HAN_STATE 0");
                    if (hangulKeyIdx < 30) { // if 자음
                        newHangulChar = 0x3131 + hangulKeyIdx;
                        hangulSendKey(newHangulChar, HCURSOR_NEW);  // 커밋(텍스트에 입력)
                        mHangulKeyStack[0] = hangulKeyIdx;
                        mHangulJamoStack[0] = hangulKeyIdx;
                        mHangulState = H_STATE_1; // goto �ʼ�
                    }
                    else { // if 모음
                        newHangulChar = 0x314F + (hangulKeyIdx - 30);
                        hangulSendKey(newHangulChar, HCURSOR_NEW);
                        mHangulKeyStack[2] = hangulKeyIdx;
                        mHangulJamoStack[1] = hangulKeyIdx;
                        mHangulState = H_STATE_3; // goto �߼�
                    }
                    break;

                case H_STATE_1: // 첫번째 입력을 자음으로 하고 두번째 입력일 경우
                    Log.i("SoftKey", "HAN_STATE 1");
                    if (hangulKeyIdx < 30) { // if 자음일 경우
                        int newHangulKeyIdx = isHangulKey(0,hangulKeyIdx);//자음 조합 확인
                        if (newHangulKeyIdx > 0) { // if 자음 조합이 있을 경우
                            newHangulChar = 0x3131 + newHangulKeyIdx;
                            mHangulKeyStack[1] = hangulKeyIdx;
                            mHangulJamoStack[0] = newHangulKeyIdx;
//	                    hangulSendKey(-1);
                            hangulSendKey(newHangulChar, HCURSOR_UPDATE);   //자음조합으로 커밋
                            mHangulState = H_STATE_2;
                        }
                        else { // 자음조합이 없을경우

                            // cursor error trick start
                            newHangulChar = 0x3131 + mHangulJamoStack[0];
                            hangulSendKey(newHangulChar,HCURSOR_UPDATE);    //선자음 업데이트
                            // trick end

                            newHangulChar = 0x3131 + hangulKeyIdx;
                            hangulSendKey(newHangulChar, HCURSOR_ADD);      //둘째자음 더하기(ADD로 이전자음 픽스)
                            mHangulKeyStack[0] = hangulKeyIdx;              //다시 시작
                            mHangulJamoStack[0] = hangulKeyIdx;
                            mHangulState = H_STATE_1;
                        }
                    }
                    else { // if 모음
                        mHangulKeyStack[2] = hangulKeyIdx;
                        mHangulJamoStack[1] = hangulKeyIdx;
//	                hangulSendKey(-1);
                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = h_jongsung_idx[mHangulJamoStack[2]];
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                        mHangulState = H_STATE_4; // goto �ʼ�,�߼�
                    }
                    break;

                case H_STATE_2:
                    Log.i("SoftKey", "HAN_STATE 2");
                    if (hangulKeyIdx < 30) { // if 자음

                        // cursor error trick start
                        newHangulChar = 0x3131 + mHangulJamoStack[0];
                        hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                        // trick end


                        mHangulKeyStack[0] = hangulKeyIdx;
                        mHangulJamoStack[0] = hangulKeyIdx;
                        mHangulJamoStack[1] = 0;
                        newHangulChar = 0x3131 + hangulKeyIdx;
                        hangulSendKey(newHangulChar, HCURSOR_ADD);
                        mHangulState = H_STATE_1; // goto �ʼ�
                    }
                    else { // if 모음
                        newHangulChar = 0x3131 + mHangulKeyStack[0];
                        mHangulKeyStack[0] = mHangulKeyStack[1];
                        mHangulJamoStack[0] = mHangulKeyStack[0];
                        mHangulKeyStack[1] = 0;
//	                hangulSendKey(-1);
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);

                        mHangulKeyStack[2] = hangulKeyIdx;
                        mHangulJamoStack[1] = mHangulKeyStack[2];
                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = 0;

                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar, HCURSOR_ADD);
                        mHangulState = H_STATE_4; // goto �ʼ�,�߼�
                    }
                    break;

                case H_STATE_3:
                    Log.i("SoftKey", "HAN_STATE 3");
                    if (hangulKeyIdx < 30) { // 자음

                        // cursor error trick start
                        newHangulChar = 0x314F + (mHangulJamoStack[1] - 30);
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                        // trick end

                        newHangulChar = 0x3131 + hangulKeyIdx;
                        hangulSendKey(newHangulChar, HCURSOR_ADD);
                        mHangulKeyStack[0] = hangulKeyIdx;
                        mHangulJamoStack[0] = hangulKeyIdx;
                        mHangulJamoStack[1] = 0;
                        mHangulState = H_STATE_1; // goto �ʼ�
                    }
                    else { // 모음
                        if (mHangulKeyStack[3] == 0) {
                            int newHangulKeyIdx = isHangulKey(2,hangulKeyIdx);  //모음조합확인
                            if (newHangulKeyIdx > 0) { // 모음 조합이 존재할 경우
                                //	                	hangulSendKey(-1);
                                newHangulChar = 0x314F + (newHangulKeyIdx - 30);
                                hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                                mHangulKeyStack[3] = hangulKeyIdx;
                                mHangulJamoStack[1] = newHangulKeyIdx;
                            }
                            else { // 모음 조합이 존재안할 경우

                                // cursor error trick start
                                newHangulChar = 0x314F + (mHangulJamoStack[1] - 30);
                                hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                                // trick end

                                newHangulChar = 0x314F + (hangulKeyIdx - 30);
                                hangulSendKey(newHangulChar,HCURSOR_ADD);
                                mHangulKeyStack[2] = hangulKeyIdx;
                                mHangulJamoStack[1] = hangulKeyIdx;
                            }
                        }
                        else {

                            // cursor error trick start
                            newHangulChar = 0x314F + (mHangulJamoStack[1] - 30);
                            hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                            // trick end

                            newHangulChar = 0x314F + (hangulKeyIdx - 30);
                            hangulSendKey(newHangulChar,HCURSOR_ADD);
                            mHangulKeyStack[2] = hangulKeyIdx;
                            mHangulJamoStack[1] = hangulKeyIdx;
                            mHangulKeyStack[3] = 0;
                        }
                        mHangulState = H_STATE_3;
                    }
                    break;
                case H_STATE_4:
                    Log.i("SoftKey", "HAN_STATE 4");
                    if (hangulKeyIdx < 30 && hangulKeyIdx != 7 && hangulKeyIdx != 18 && hangulKeyIdx != 24) { // if 자음
                        mHangulKeyStack[4] = hangulKeyIdx;
                        mHangulJamoStack[2] = hangulKeyIdx;
//	                hangulSendKey(-1);
                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = h_jongsung_idx[mHangulJamoStack[2]+1];
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                        //없어도 되는부분??
//                        if (jong_idx == 0) {
//                            mHangulKeyStack[0] = hangulKeyIdx;
//                            mHangulKeyStack[1] = 0;
//                            mHangulKeyStack[2] = 0;
//                            mHangulKeyStack[3] = 0;
//                            mHangulKeyStack[4] = 0;
//                            mHangulJamoStack[0] = hangulKeyIdx;
//                            mHangulJamoStack[1] = 0;
//                            mHangulJamoStack[2] = 0;
//                            newHangulChar = 0x3131 + hangulKeyIdx;
//                            hangulSendKey(newHangulChar,HCURSOR_ADD);
//                            mHangulState = H_STATE_1;
//                        }
//                        else {
                            mHangulState = H_STATE_5;
//                        }
                    }
                    else if(hangulKeyIdx < 30)
                    {
                        // cursor error trick start
                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28));
                        hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                        // trick end


                        mHangulKeyStack[0] = hangulKeyIdx;
                        mHangulKeyStack[1] = 0;
                        mHangulKeyStack[2] = 0;
                        mHangulKeyStack[3] = 0;
                        mHangulKeyStack[4] = 0;
                        mHangulJamoStack[0] = hangulKeyIdx;
                        mHangulJamoStack[1] = 0;
                        mHangulJamoStack[2] = 0;
                        newHangulChar = 0x3131 + hangulKeyIdx;
                        hangulSendKey(newHangulChar,HCURSOR_ADD);
                        mHangulState = H_STATE_1;
                    }
                    else { // if 모음
                        if (mHangulKeyStack[3] == 0) {  //조합 모음이 아직 안나온 경우
                            int newHangulKeyIdx = isHangulKey(2,hangulKeyIdx);
                            if (newHangulKeyIdx > 0) { // if 모음조합이 된 경우
                                //	                	hangulSendKey(-1);
                                //	                    mHangulKeyStack[2] = newHangulKeyIdx;
                                mHangulKeyStack[3] = hangulKeyIdx;
                                mHangulJamoStack[1] = newHangulKeyIdx;
                                cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                                jung_idx = mHangulJamoStack[1] - 30;
                                jong_idx = 0;
                                newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                                hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                                mHangulState = H_STATE_4;
                            }
                            else { // 모음조합이 안되는 경우

                                // cursor error trick start
                                cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                                jung_idx = mHangulJamoStack[1] - 30;
                                jong_idx = 0;
                                newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                                hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                                // trick end

                                newHangulChar = 0x314F + (hangulKeyIdx - 30);
                                hangulSendKey(newHangulChar,HCURSOR_ADD);
                                mHangulKeyStack[0] = 0;
                                mHangulKeyStack[1] = 0;
                                mHangulJamoStack[0] = 0;
                                mHangulKeyStack[2] = hangulKeyIdx;
                                mHangulJamoStack[1] = hangulKeyIdx;
                                mHangulState = H_STATE_3;
                            }
                        }
                        else {//조합모음이 이미 나온 경우

                            // cursor error trick start
                            cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                            jung_idx = mHangulJamoStack[1] - 30;
                            jong_idx = 0;
                            newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                            hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                            // trick end


                            newHangulChar = 0x314F + (hangulKeyIdx - 30);
                            hangulSendKey(newHangulChar,HCURSOR_ADD);
                            mHangulKeyStack[0] = 0;
                            mHangulKeyStack[1] = 0;
                            mHangulJamoStack[0] = 0;
                            mHangulKeyStack[2] = hangulKeyIdx;
                            mHangulJamoStack[1] = hangulKeyIdx;
                            mHangulKeyStack[3] = 0;
                            mHangulState = H_STATE_3;

                        }
                    }
                    break;
                case H_STATE_5:
                    Log.i("SoftKey", "HAN_STATE 5");
                    if (hangulKeyIdx < 30) { // if 자음
                        int newHangulKeyIdx = isHangulKey(4,hangulKeyIdx);
                        if (newHangulKeyIdx > 0) { // if 자음조합이 있을 경우
//	                	hangulSendKey(-1);
                            mHangulKeyStack[5] = hangulKeyIdx;
                            mHangulJamoStack[2] = newHangulKeyIdx;

                            cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                            jung_idx = mHangulJamoStack[1] - 30;
                            jong_idx = h_jongsung_idx[mHangulJamoStack[2]+1];;
                            newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                            hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                            mHangulState = H_STATE_6;
                        }
                        else { // if 자음조합이 없을 경우

                            // cursor error trick start
                            cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                            jung_idx = mHangulJamoStack[1] - 30;
                            jong_idx = h_jongsung_idx[mHangulJamoStack[2]+1];;
                            newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                            hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                            // trick end


                            mHangulKeyStack[0] = hangulKeyIdx;
                            mHangulKeyStack[1] = 0;
                            mHangulKeyStack[2] = 0;
                            mHangulKeyStack[3] = 0;
                            mHangulKeyStack[4] = 0;
                            mHangulJamoStack[0] = hangulKeyIdx;
                            mHangulJamoStack[1] = 0;
                            mHangulJamoStack[2] = 0;
                            newHangulChar = 0x3131 + hangulKeyIdx;
                            hangulSendKey(newHangulChar,HCURSOR_ADD);
                            mHangulState = H_STATE_1;
                        }
                    }
                    else { // if 모음
//	            	hangulSendKey(-1);

                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = 0;
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);

                        mHangulKeyStack[0] = mHangulKeyStack[4];
                        mHangulKeyStack[1] = 0;
                        mHangulKeyStack[2] = hangulKeyIdx;
                        mHangulKeyStack[3] = 0;
                        mHangulKeyStack[4] = 0;
                        mHangulJamoStack[0] = mHangulKeyStack[0];
                        mHangulJamoStack[1] = mHangulKeyStack[2];
                        mHangulJamoStack[2] = 0;

                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = 0;
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar, HCURSOR_ADD);

                        // Log.i("SoftKey", "--- Goto HAN_STATE 4");
                        mHangulState = H_STATE_4;
                    }
                    break;
                case H_STATE_6:
                    Log.i("SoftKey", "HAN_STATE 6");
                    if (hangulKeyIdx < 30) {

                        // cursor error trick start
                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = h_jongsung_idx[mHangulJamoStack[2]+1];;
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar,HCURSOR_UPDATE);
                        // trick end


                        mHangulKeyStack[0] = hangulKeyIdx;
                        mHangulKeyStack[1] = 0;
                        mHangulKeyStack[2] = 0;
                        mHangulKeyStack[3] = 0;
                        mHangulKeyStack[4] = 0;
                        mHangulJamoStack[0] = hangulKeyIdx;
                        mHangulJamoStack[1] = 0;
                        mHangulJamoStack[2] = 0;

                        newHangulChar = 0x3131 + hangulKeyIdx;
                        hangulSendKey(newHangulChar,HCURSOR_ADD);

                        mHangulState = H_STATE_1;
                    }
                    else {
//	            	hangulSendKey(-1);
                        mHangulJamoStack[2] = mHangulKeyStack[4];

                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = h_jongsung_idx[mHangulJamoStack[2]+1];;
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);

                        mHangulKeyStack[0] = mHangulKeyStack[5];
                        mHangulKeyStack[1] = 0;
                        mHangulKeyStack[2] = hangulKeyIdx;
                        mHangulKeyStack[3] = 0;
                        mHangulKeyStack[4] = 0;
                        mHangulKeyStack[5] = 0;
                        mHangulJamoStack[0] = mHangulKeyStack[0];
                        mHangulJamoStack[1] = mHangulKeyStack[2];
                        mHangulJamoStack[2] = 0;

                        cho_idx = h_chosung_idx[mHangulJamoStack[0]];
                        jung_idx = mHangulJamoStack[1] - 30;
                        jong_idx = 0;
                        newHangulChar = 0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx);
                        hangulSendKey(newHangulChar,HCURSOR_ADD);

                        mHangulState = H_STATE_4; // goto �ʼ�,�߼�
                    }
                    break;
            }
        }
        else {
            // Log.i("Hangul", "handleHangul - No hancode");
            Log.d("dz","5");
            clearHangul();
            sendKey(primaryCode);
        }

    }
// Hangul Code End    

    private void handleCharacter(int primaryCode, int[] keyCodes) {
        if (isInputViewShown()) {
            if (mInputView.isShifted()) {
                primaryCode = Character.toUpperCase(primaryCode);
            }
        }
        if (isAlphabet(primaryCode) && mPredictionOn) {
            mComposing.append((char) primaryCode);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            //isTextUpdate=true;
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } else {
            sendKeyChar((char)primaryCode);
            //isTextUpdate = true;
        	/*
            getCurrentInputConnection().commitText(
                    String.valueOf((char) primaryCode), 1);
            */
        }
    }

    private void handleClose() {
        commitTyped(getCurrentInputConnection());
        requestHideSelf(0);
        mInputView.closing();
    }

    private void checkToggleCapsLock() {
        long now = System.currentTimeMillis();
        if (mLastShiftTime + 800 > now) {
            mCapsLock = !mCapsLock;
            mLastShiftTime = 0;
        } else {
            mLastShiftTime = now;
        }
    }

    private String getWordSeparators() {
        return mWordSeparators;
    }

    //wordSeparator인지 판별하는 함수
    public boolean isWordSeparator(int code) {
        String separators = getWordSeparators();
        //contatins : 문자열에 지정된 char 값을 포함하는경우에만 true 리턴
        return separators.contains(String.valueOf((char)code));
    }

    public void pickDefaultCandidate() {
        pickSuggestionManually(0);
    }

    public void pickSuggestionManually(int index) {
        if (mCompletionOn && mCompletions != null && index >= 0
                && index < mCompletions.length) {
            CompletionInfo ci = mCompletions[index];
            getCurrentInputConnection().commitCompletion(ci);
//            if (mCandidateView != null) {
//                mCandidateView.clear();
//            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }

    public void swipeRight() {
        /*
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
        */
    }

    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
        sendSpacingJson();
        //-*- test
//        String jsonTest = "{\"response\" : [\"spacing\"], \"spacing\" : \"주말에 실컷 놀아야지\"}"; //"{\"response\" : [\"modified\"], \"modified\" : {\"정말루\" : [\"0\",\"정말로\"], \"하르\" : [\"2\",\"하루\"] }}";
//        processJsonMessegeTest(jsonTest);
        //-*-
//        InputConnection ic = getCurrentInputConnection();
//
//        //서버로 보낼 오타 수정 메시지 작성 및 송신
//        if(!tcp.getIsRunning())
//            TcpOpen(tcp);
//        String text = ic.getExtractedText(new ExtractedTextRequest(), 0).text.toString();
//        String sendJson = makeJsonToReq(true, false, setTextListForCorrect(text), null);
//        Log.d("text??", sendJson);
//        tcp.sendData(sendJson);
        ////////////
    }

    public void onPress(int primaryCode) {
    }

    public void onRelease(int primaryCode) {
    }

//    //소켓 연결
//    public void setSocket(String ip, int port) throws IOException {
//        try {
//            socket = new Socket(ip, port);
//            networkWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
//            networkReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
//        } catch (IOException e) {
//            Log.w("Error", "Network");
//            e.printStackTrace();
//        }
//    }

    //서버로 보낼 json 데이터 제작 함수
    public String makeJsonToReq(boolean isSpacing, boolean isModified, String spacingData, String modifiedData)
    {
        String msg = "{\"request\":[\"";
        if(isSpacing)
            msg += "spacing\"";
        if(isModified) {
            if(isSpacing)
                msg += ",";
            msg += "modified\"";
        }
        msg += "]";
        if(isSpacing) {
            msg += ",\"spacingData\":\"" + spacingData + "\"";
        }
        if(isModified){
            msg += ",\"modifiedData\":\"" + modifiedData + "\"";
        }
        msg += "}";

        return msg;
    }

    //오타 검사를 위해 서버로 문자열을 보내기 위한 준비(textListSeparated 리스트에 splitWord로 분해한 어절을 저장하고, 분해한 어절을 반환)
    private String setTextListForCorrect(String text)
    {
//        String strToServer = "";
        int pos = -1;
        boolean isContinueChar = false;
//        textListSeparated.clear();
        correctionTextPosition.clear();
        //한글을 제외한 단어를 공백처리하기 위한 정규식
        String splitWord = "[^ㄱ-힣]+";

        String rePlaceStr = text.replaceAll(splitWord," ");
        rePlaceStr = rePlaceStr.concat(" ");

        int wordStartPos = 0;
        int wordFinishPos = 0;
        int tmp[] = new int[2];
        //각 어절의 위치를 correctionTextPosition에 저장
        while (pos<rePlaceStr.length()-1)
        {
            pos++;
            //Log.d("pos",String.valueOf(pos));
            if(!isContinueChar && rePlaceStr.charAt(pos)!=' ')
            {
                wordStartPos = pos;
                isContinueChar = true;
            }
            else if(!isContinueChar)
            {
                continue;
            }
            else if(rePlaceStr.charAt(pos)!=' ')
            {
                continue;
            }
            else
            {
                wordFinishPos = pos-1;
                isContinueChar = false;
                tmp[0] = wordStartPos;
                tmp[1] = wordFinishPos;
                correctionTextPosition.add(tmp.clone());
                Log.d("SIZE : " + (correctionTextPosition.size()-1), "POS : " + tmp[0]);
            }
        }
//        //split한 어절들을 textListSeparated에 삽입
//        for(int i=0; i<stringList.length; i++) {
//            String[] tmp = new String[correctWord_MAX];
//            tmp[0] = stringList[i];
//            textListSeparated.add(tmp);
//            strToServer = strToServer.concat(stringList[i]+" ");
//        }
//        Log.d("strToServer",strToServer);
        for(int i = 0; i<correctionTextPosition.size(); i++)
        {
            Log.d("array", String.valueOf(correctionTextPosition.get(i)[0]) + "," + String.valueOf(correctionTextPosition.get(i)[1]));
        }
        return rePlaceStr;
    }



    //softKeyboard에서 처리하는 메시지핸들러
    public void handleMessage(Message msg){

        switch (msg.what)
        {
            case MSG_REQUEST_RECEIVE:
                processJsonMessege(msg);
                break;
        }
    }

    private void processJsonMessege(Message msg){
        JSONObject obj;
        JSONArray responseType;
        JSONArray correctionList;
        InputConnection ic;
        String spacingData;
        JSONObject modifiedData;

        Toast.makeText(this,"Receive Data : " + msg.obj.toString(), Toast.LENGTH_LONG).show();
        Log.d("Receive Data : ", msg.obj.toString());
        try {
            ic = getCurrentInputConnection();
            obj = new JSONObject((String) msg.obj);
//            obj = new JSONObject("{\"response\" : [\"modified\"],\"modified\" : {\"나는\" : [\"0\",\"ㅋㅋ\"],\"김정민\" : [\"1\",\"ㅎㅎ\"]}}");
            //Log.d("닿?","나?");
            responseType = (JSONArray) obj.getJSONArray("response");
            //responseType 별로 처리
            for(int i=0; i < responseType.length(); i++)
            {
                //자동 띄어쓰기 일 경우
                if(responseType.getString(i).equals("spacing"))
                {
                    //오타 단어 및 수정리스트 삭제
                    //cBtnList.clear(); //mathcer를 사용 하므로
                    spacingData = obj.getString("spacing");
                    ic.finishComposingText();
                    //isCommitted = true;
                    //-*-띄어쓰기 문제 있을시 deleteSurrounding을 주석처리하고 아래 세 줄 주석해제 할것.
//                            int oldTextNum = ic.getExtractedText(new ExtractedTextRequest(), 0).text.length();
//                            ic.setSelection(oldTextNum, oldTextNum);
//                            ic.deleteSurroundingText(oldTextNum,0);
                    ic.deleteSurroundingText(MAX_TEXT,MAX_TEXT);
                    ic.commitText(spacingData,1);
                }

                //오타 수정의 경우
                else if(responseType.getString(i).equals("modified"))
                {
                    cBtnList.clear();

                    if(!obj.getString("modified").equals("noData")) {
                        modifiedData = obj.getJSONObject("modified");
                        Iterator<String> correctionKeys = modifiedData.keys();

                        while (correctionKeys.hasNext()){
                            String oldWord = correctionKeys.next();
                            String[] correctionWordTmp;

                            correctionList = modifiedData.getJSONArray(oldWord);

                            int index = 1;
                            correctionWordTmp = new String[correctionList.length()-1];
                            while(index < correctionList.length())
                            {
                                correctionWordTmp[index-1] = correctionList.getString(index++);
                            }

                            //오타 검사 단어와 오타 단어가 같을 경우 제외
                            if(!oldWord.equals(correctionWordTmp[0])) {
                                cBtnList.add(new correctionButtonInform(
                                        correctionTextPosition.get(correctionList.getInt(0))[0],
                                        correctionTextPosition.get(correctionList.getInt(0))[1],
                                        oldWord,
                                        correctionWordTmp));
                            }
                        }
                    }

                    //오타 수정 버튼 텍스트 갱신
                    renewCorrectionButtonsAsCbtnList();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    //-*-test(msg.obj -> msg(String))
    private void processJsonMessegeTest(String msg){
        JSONObject obj;
        JSONArray responseType;
        JSONArray correctionList;
        InputConnection ic;
        String spacingData;
        JSONObject modifiedData;

        Log.d("value",msg);
//-*-        Toast.makeText(this,"Receive Data : " + msg, Toast.LENGTH_LONG).show();
        try {
            ic = getCurrentInputConnection();
            obj = new JSONObject((String) msg);
            //obj = new JSONObject("{\"response\" : [\"modified\"],\"modified\" : {\"나는\" : [\"0\",\"ㅋㅋ\"],\"김정민\" : [\"1\",\"ㅎㅎ\"]}}");
            //Log.d("닿?","나?");
            responseType = (JSONArray) obj.getJSONArray("response");
            //responseType 별로 처리
            for(int i=0; i < responseType.length(); i++)
            {
                //자동 띄어쓰기 일 경우
                if(responseType.getString(i).equals("spacing"))
                {
                    //오타 단어 및 수정리스트 삭제
                    cBtnList.clear();
                    spacingData = obj.getString("spacing");
                    ic.finishComposingText();
                    //isCommitted = true;
                    //-*-띄어쓰기 문제 있을시 deleteSurrounding을 주석처리하고 아래 세 줄 주석해제 할것.
//                            int oldTextNum = ic.getExtractedText(new ExtractedTextRequest(), 0).text.length();
//                            ic.setSelection(oldTextNum, oldTextNum);
//                            ic.deleteSurroundingText(oldTextNum,0);
                    ic.deleteSurroundingText(MAX_TEXT,MAX_TEXT);
                    ic.commitText(spacingData,1);
                }

                //오타 수정의 경우
                else if(responseType.getString(i).equals("modified"))
                {
                    cBtnList.clear();

                    if(!obj.getString("modified").equals("noData")) {
                        modifiedData = obj.getJSONObject("modified");
                        Iterator<String> correctionKeys = modifiedData.keys();

                        while (correctionKeys.hasNext()){
                            String oldWord = correctionKeys.next();
                            String[] correctionWordTmp;

                            correctionList = modifiedData.getJSONArray(oldWord);

                            int index = 1;
                            correctionWordTmp = new String[correctionList.length()-1];
                            while(index < correctionList.length())
                            {
                                correctionWordTmp[index-1] = correctionList.getString(index++);
                            }

                            //오타 검사 단어와 오타 단어가 같을 경우 제외
                            if(!oldWord.equals(correctionWordTmp[0]) && correctionTextPosition.size() > correctionList.getInt(0)) {
                                cBtnList.add(new correctionButtonInform(
                                        correctionTextPosition.get(correctionList.getInt(0))[0],
                                        correctionTextPosition.get(correctionList.getInt(0))[1],
                                        oldWord,
                                        correctionWordTmp));
                            }
                        }
                    }

                    //오타 수정 버튼 텍스트 갱신
                    renewCorrectionButtonsAsCbtnList();
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    //-*-testend

    private void renewCorrectionButtonsAsCbtnList(){
        clearButtonListText(correctionButton);
        for(int j = 0; j < cBtnList.size(); j++)
        {
            if(j>=correctionButton.length)
                break;

            correctionButton[j].setText(cBtnList.get(j).getOldWord());
        }
    }
    private void clearButtonListText(Button[] btn){
        for(int i = 0; i < btn.length; i++)
        {
            btn[i].setText("");
        }
    }

    void clearCorrectionBar(){
        for(int i=0; i<3; i++)
            correctionButton[i].setText("");
        cBtnList.clear();
    }

    //tcp 연결 및 통신 시작
    private void TcpOpen(TcpClient tcp){
        if(!tcp.getIsRunning()) {
            t = null;
            t = new Thread(tcp);
            tcp.setRunningState(true);
//            Log.d("what Thread",tcp.toString());
            t.start();
            Log.d("numThread",String.valueOf(Thread.activeCount()));
//            if(tcp.getState() == Thread.State.TERMINATED)
//                tcp.start();
        }
    }

    //tcp 연결 해제
    private void TcpClose(TcpClient tcp){
        tcp.setRunningState(false);
        tcp.socketClose();
    }

    private void updateCandidateWord(int cursor)
    {
        InputConnection ic = getCurrentInputConnection();
        candidateWordPosArray[0] = candidateWordPosArray[1] = cursor;
        //자동완성 단어 추적
        int beforeWordLen, nowWordLen;
        candidateOldWord = "";
        beforeWordLen=0;
        nowWordLen = 0;
        String nowWord, beforeWord="";
        //커서 전 단어
        for(int i=1;;i++)
        {
            CharSequence a = ic.getTextBeforeCursor(i,0);
            if(a==null)
                break;

            Log.d("completeBtLoop",String.valueOf(i));
            nowWord = a.toString();
            nowWordLen = nowWord.length();
            Log.d("completeBtWord",nowWord);
            Log.d("completeBtLen",String.valueOf(nowWordLen) + "," + String.valueOf(beforeWordLen));
//            Log.d("completeBtIndex", String.valueOf(nowWord.charAt(0)));
            if(nowWordLen==beforeWordLen || (nowWord.length() > 0 && isWordSeparator(nowWord.charAt(0))))
            {
                Log.d("completeBt",String.valueOf(i));
                candidateWordPosArray[0] = cursor - i + 1;
                if(i>1) {
                    candidateOldWord += beforeWord;
                }

                break;
            }
            beforeWord = nowWord;
            beforeWordLen = nowWordLen;
        }
        //커서 후 단어
        beforeWordLen=0;
        for(int i=1;;i++)
        {
            CharSequence a = ic.getTextAfterCursor(i,0);
            if(a==null)
                break;

            nowWord = a.toString();
            nowWordLen = nowWord.length();
            if(nowWordLen==beforeWordLen || (nowWord.length() > 0 && isWordSeparator(nowWord.charAt(nowWord.length()-1))))
            {
                Log.d("completeBt",String.valueOf(i));
                candidateWordPosArray[1] = cursor + i - 1;
                if(i>1) {
                    candidateOldWord += beforeWord;
                }

                break;
            }
            beforeWord = nowWord;
            beforeWordLen = nowWordLen;
        }
    }

    private void updateCandidateComposing(){
        candidateOldWord = mComposing.toString();
    }

    private void setText(int start, int len, String newWord)
    {
        InputConnection ic = getCurrentInputConnection();
//        if (mComposing.length() > 0) {
//            commitTyped(getCurrentInputConnection());
//        }
        clearHangul();
        ic.setSelection(start,start);
        Log.d(String.valueOf(start),String.valueOf(len));
        ic.deleteSurroundingText(0,len);
        ic.commitText(newWord,1);
    }

    private void updateAfterCBtnList(int index, int subLength)
    {
        for(int i = index+1 ; i<cBtnList.size(); i++)
            cBtnList.get(i).addPos(subLength);
    }

    private void sendCorrectionJson()
    {
        InputConnection ic = getCurrentInputConnection();
        if(!tcp.getIsRunning())
            TcpOpen(tcp);
        if(ic.getExtractedText(new ExtractedTextRequest(), 0) != null) {
            String text = ic.getExtractedText(new ExtractedTextRequest(), 0).text.toString();
            String sendJson = makeJsonToReq(false, true, null, setTextListForCorrect(text));
            Log.d("text??", sendJson);
            tcp.sendData(sendJson);
        }
    }

    private void sendSpacingJson()
    {
        Log.d("in???","?????????");
        InputConnection ic = getCurrentInputConnection();

        //서버로 보낼 자동 띄어쓰기 모델 메시지 작성 및 송신
        if(!tcp.getIsRunning())
            TcpOpen(tcp);
        if(ic.getExtractedText(new ExtractedTextRequest(), 0) != null) {
            String text = ic.getExtractedText(new ExtractedTextRequest(), 0).text.toString();
            String sendJson = makeJsonToReq(true, false, text.replaceAll("\n"," "), null);
            Log.d("text??", sendJson);
            tcp.sendData(sendJson);
        }
    }
}