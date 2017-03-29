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

import android.content.Intent;
import android.graphics.Rect;
import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.net.Uri;
import android.text.method.MetaKeyKeyListener;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.CompletionInfo;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;


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

    private KeyboardView mInputView;
    private CandidateView mCandidateView;
    private CompletionInfo[] mCompletions;

    //수정 가능한 String
    private StringBuilder mComposing = new StringBuilder();
    private boolean mPredictionOn;
    private boolean mCompletionOn;
    private int mLastDisplayWidth;
    private boolean mCapsLock;
    private long mLastShiftTime;
    private long mMetaState;

    private LatinKeyboard mSymbolsKeyboard;
    private LatinKeyboard mSymbolsShiftedKeyboard;
    private LatinKeyboard mQwertyKeyboard;

    private Keyboard mHangulKeyboard; // Hangul Code
    private Keyboard mHangulShiftedKeyboard; // Hangul Code
    private Keyboard mSejongKeyboard;

    private LatinKeyboard mCurKeyboard;

    private String mWordSeparators;

    /**
     * Main initialization of the input method component.  Be sure to call
     * to super class.
     */
    @Override public void onCreate() {
        super.onCreate();
        mWordSeparators = getResources().getString(R.string.word_separators);
    }

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
        mQwertyKeyboard = new LatinKeyboard(this, R.xml.qwerty);
        mSymbolsKeyboard = new LatinKeyboard(this, R.xml.symbols);
        mSymbolsShiftedKeyboard = new LatinKeyboard(this, R.xml.symbols_shift);
        mHangulKeyboard = new HangulKeyboard(this, R.xml.hangul);
        mHangulShiftedKeyboard = new HangulKeyboard(this, R.xml.hangul_shift);
        mSejongKeyboard = new SejongKeyboard(this, R.xml.sejong);
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
        return mInputView;
    }

    /**
     * Called by the framework when your view for showing candidates needs to
     * be generated, like {@link #onCreateInputView}.
     */
    //candidate 보여주기 위함인듯?
    @Override public View onCreateCandidatesView() {
        mCandidateView = new CandidateView(this);
        mCandidateView.setService(this);
        return mCandidateView;
    }

    /**
     * This is the main point where we do our initialization of the input method
     * to begin operating on an application.  At this point we have been
     * bound to the client, and are now receiving all of the detailed information
     * about the target of our edits.
     */
    //입력메소드 초기화??(각 값들 초기화인듯)
    @Override public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
/*        
        Log.i("Hangul", "onStartInput");
*/
        //스택들 초기화,,
        clearHangul();
        clearSejong();
        previousCurPos = -1;

        // Reset our state.  We want to do this even if restarting, because
        // the underlying state of the text editor could have changed in any way.
        mComposing.setLength(0);
        updateCandidates();

        //쉬프트 클리어
        if (!restarting) {
            // Clear shift states.
            mMetaState = 0;
        }

        mPredictionOn = true;
        mCompletionOn = false;
        mCompletions = null;

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
                mCurKeyboard = mQwertyKeyboard;
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
                    mPredictionOn = false;
                    mCompletionOn = isFullscreenMode();
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
        mCurKeyboard.setImeOptions(getResources(), attribute.imeOptions);

    }

    /**
     * This is called when the user is done editing a field.  We can use
     * this to reset our state.
     */
    //유저가 필드의 에딧팅을 끝내면 불려짐. 이를 이용하여 상태를 리셋할수있다.
    @Override public void onFinishInput() {
        super.onFinishInput();

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
        setCandidatesViewShown(false);

        //키보드 뷰 닫기
        mCurKeyboard = mQwertyKeyboard;
        if (mInputView != null) {
            mInputView.closing();
        }
    }

    //편집기에서 입력 시작시 호출
    @Override public void onStartInputView(EditorInfo attribute, boolean restarting) {
        super.onStartInputView(attribute, restarting);
        // Apply the selected keyboard to the input view.
        //선택한 키보드를 입력보기에 적용하십시오.
        mInputView.setKeyboard(mCurKeyboard);
        mInputView.closing();
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
                updateCandidates();
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.finishComposingText();
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
                InputConnection ic = getCurrentInputConnection();
                if (ic != null) {
                    ic.finishComposingText();
                }
            }
            isOkUpdateSelection = true;
        }
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
            inputConnection.commitText(mComposing, mComposing.length());
            mComposing.setLength(0);
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
            }
            else if (current == mSejongKeyboard) {
                switch(ko_state_idx) {
                    case KO_S_1110:
                        if (mComposing.length() > 0) {
                            mComposing.setLength(0);
                            getCurrentInputConnection().finishComposingText();
                        }
                        clearSejong();
                        break;
                    default :
                        clearSejong();
                        sendKey(primaryCode);
                        break;
                }
            }
            else {
                sendKey(primaryCode);
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (primaryCode == Keyboard.KEYCODE_DELETE) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mHangulKeyboard || current == mHangulShiftedKeyboard ) {
                hangulSendKey(-2,HCURSOR_NONE);
            }
            else if (current == mSejongKeyboard) {
                sendSejongKey((char)0,HCURSOR_DELETE);
            }
            else {
                handleBackspace();
            }
        } else if (primaryCode == Keyboard.KEYCODE_SHIFT) {
            handleShift();
        } else if (primaryCode == Keyboard.KEYCODE_CANCEL) {
            handleClose();
            return;
        } else if (primaryCode == LatinKeyboardView.KEYCODE_OPTIONS) {
            // Show a menu or somethin'

            Intent i = new Intent(Intent.ACTION_VIEW);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            i.setData(Uri.parse("http://www.kandroid.com/market/product.php?id=1"));
            startActivity(i);

            return;
        } else if (primaryCode == Keyboard.KEYCODE_MODE_CHANGE
                && mInputView != null) {
            Keyboard current = mInputView.getKeyboard();
            if (current == mSymbolsKeyboard || current == mSymbolsShiftedKeyboard) {
                current = mQwertyKeyboard;
            }
            // Hangul Start Code
            else if (current == mQwertyKeyboard) {
                if (mComposing.length() > 0) {
                    commitTyped(getCurrentInputConnection());
                }
                Log.d("dz","2");
                clearHangul();
                current = mHangulKeyboard;
            }
            // Hangul End Code
            else if (current == mHangulKeyboard) {
                if (mComposing.length() > 0) {
                    getCurrentInputConnection().commitText(mComposing, mComposing.length());
                    mComposing.setLength(0);
                }
                clearSejong();
                current = mSejongKeyboard;
            }
            else {
                if (mComposing.length() > 0) {
                    getCurrentInputConnection().commitText(mComposing, mComposing.length());
                    mComposing.setLength(0);
                }
                Log.d("dz","1");
                clearHangul();
                current = mSymbolsKeyboard;
            }
            mInputView.setKeyboard(current);
            if (current == mSymbolsKeyboard) {
                current.setShifted(false);
            }

            if (current == mSejongKeyboard) {
                mInputView.setPreviewEnabled(false);
            }
            else {
                mInputView.setPreviewEnabled(true);
            }
        }
        else {

            // Hangul Start Code
            Keyboard current = mInputView.getKeyboard();
            if (current == mHangulKeyboard || current == mHangulShiftedKeyboard) {
                handleHangul(primaryCode, keyCodes);
            }
            else if (current == mSejongKeyboard) {
                handleSejong(primaryCode, keyCodes);
            }
            else {
                handleCharacter(primaryCode, keyCodes);
            }
            // Hangul End Code
        }
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

    public void setSuggestions(List<String> suggestions, boolean completions,
                               boolean typedWordValid) {
        if (suggestions != null && suggestions.size() > 0) {
            setCandidatesViewShown(true);
        } else if (isExtractViewShown()) {
            setCandidatesViewShown(true);
        }
        if (mCandidateView != null) {
            mCandidateView.setSuggestions(suggestions, completions, typedWordValid);
        }
    }

    private void handleBackspace() {
        final int length = mComposing.length();
        if (length > 1) {
            mComposing.delete(length - 1, length);
            getCurrentInputConnection().setComposingText(mComposing, 1);
            updateCandidates();
        } else if (length > 0) {
            mComposing.setLength(0);
            getCurrentInputConnection().commitText("", 0);
            updateCandidates();
        } else {
            keyDownUp(KeyEvent.KEYCODE_DEL);
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
        } else if (currentKeyboard == mSymbolsShiftedKeyboard) {
            mSymbolsShiftedKeyboard.setShifted(false);
            mInputView.setKeyboard(mSymbolsKeyboard);
            mSymbolsKeyboard.setShifted(false);
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


    private boolean isOkUpdateSelection = true;
    //한글키보드의 경우, 커서가 업데이트 될때 해당 함수 내용을 실행할것인지 여부 (한글 자모 조합을 위해 setComposingText를 유지하기 위함)
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
            mHCursorState = HCURSOR_NEW;
        }
        else if (hCursor == HCURSOR_ADD) {
            mHCursorState = HCURSOR_ADD;
            Log.i("Hangul", "HCURSOR_ADD");
            if (mComposing.length() > 0) {
                mComposing.setLength(0);
                getCurrentInputConnection().finishComposingText();
                isOkUpdateSelection = false;
            }

            mComposing.append((char)newHangulChar);
            Log.d("length",mComposing.toString());
            getCurrentInputConnection().setComposingText(mComposing, 1);
        }
        else if (hCursor == HCURSOR_UPDATE) {
            Log.i("Hangul", "HCURSOR_UPDATE");
            mComposing.setCharAt(0, (char)newHangulChar);
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
                        keyDownUp(KeyEvent.KEYCODE_DEL);
                        break;
                    case H_STATE_1: // �ʼ�
//					keyDownUp(KeyEvent.KEYCODE_DEL);
                        mComposing.setLength(0);
                        getCurrentInputConnection().commitText("", 0);
                        Log.d("dz","7");
                        clearHangul();
                        mHangulState = H_STATE_0;
                        break;
                    case H_STATE_2: // �ʼ�(������)
                        newHangulChar = 0x3131 + mHangulKeyStack[0];
                        hangulSendKey(newHangulChar, HCURSOR_UPDATE);
                        mHangulKeyStack[1] = 0;
                        mHangulJamoStack[0] = mHangulKeyStack[0];
                        mHangulState = H_STATE_1; // goto �ʼ�
                        break;
                    case H_STATE_3: // �߼�(�ܸ���,������)
                        if (mHangulKeyStack[3] == 0) {
//						keyDownUp(KeyEvent.KEYCODE_DEL);
                            mComposing.setLength(0);
                            getCurrentInputConnection().commitText("", 0);
                            Log.d("dz","6");
                            clearHangul();
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
                    case H_STATE_4: // �ʼ�,�߼�(�ܸ���,������)
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
                    case H_STATE_5:	// �ʼ�,�߼�,����
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

    private void clearSejong() {
        ko_state_idx = KO_S_0000;
        ko_state_first_idx = 0;
        ko_state_middle_idx = 0;
        ko_state_last_idx = 0;
        ko_state_next_idx = 0;
        prev_key = -1;
        return;
    }

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

    private void handleSejong(int primaryCode, int[] keyCodes) {

        char new_last_idx;
        int base_idx;
        char new_state_idx;
        int idx;
        char newHangulChar;
        char cho_idx, jung_idx, jong_idx;
        int key = primaryCode;

        base_idx = primaryCode - 0x41;
        idx = key_idx[base_idx];

        Log.i("Hangul", "state[" + Integer.toString(ko_state_idx) + "]" + "["
                + Integer.toString(ko_state_first_idx) + ","
                + Integer.toString(ko_state_middle_idx) + ","
                + Integer.toString(ko_state_last_idx) + ","
                + Integer.toString(ko_state_next_idx) + "]"
        );
        Log.i("Hangul", "base_idx,idx[" + Integer.toString(base_idx) +
                ":" + Integer.toString(idx) + "]");
        if (base_idx < 9) {
            switch(ko_state_idx) {
                case KO_S_0000: // clear
                    ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
                    newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                    sendSejongKey(newHangulChar, HCURSOR_NEW);
                    ko_state_idx = KO_S_1000;
                    break;
                case KO_S_0100:
                    ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
                    ko_state_middle_idx = 0;
                    newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                    sendSejongKey(newHangulChar, HCURSOR_NEW);
                    ko_state_idx = KO_S_1000;
                    break;
                case KO_S_1000:
                    if (key == prev_key) {
                        new_state_idx = ko_first_state[ko_state_first_idx * 9 + idx];
                        if (new_state_idx == ko_state_first_idx) {
                            newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                            sendSejongKey(newHangulChar, HCURSOR_ADD);
                        }
                        else {
                            ko_state_first_idx = new_state_idx;
                            newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                            sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                        }
                    }
                    else {
                        ko_state_first_idx = ko_first_state[idx];
                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                    }
                    break;
                case KO_S_1100: // �ʼ�,�߼�
                    ko_state_last_idx = ko_last_state[ko_state_last_idx * 9 + idx];
                    Log.i("Hangul","ko_state_last_idx[" + Integer.toString(ko_state_last_idx) + "]");
                    if (ko_state_middle_idx > 2) {
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        jong_idx = h_jongsung_idx[jongsung_code[ko_state_last_idx - 1]+1];
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                        ko_state_idx = KO_S_1110;
                    }
                    else {
                        Log.i("Hangul", "Not Combination...");
                        // must be implemented.
                    }
                    break;
                case KO_S_1110:
                    new_last_idx = ko_last_state[ko_state_last_idx * 9 + idx];

                    if(new_last_idx >= 28) {
                        ko_state_last_idx = new_last_idx;
                        ko_state_next_idx
                                = ko_jong_l_split[(new_last_idx - 28) * 2 + 1];

                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        char split_last_idx = jongsung_28idx[ko_state_last_idx - 28];
                        Log.i("Hangul", "split_last_idx[" + Integer.toString(split_last_idx) + "]");
                        if (split_last_idx > 0) {
//	 	               Log.i("Hangul", "jongsung_code[" + Integer.toString(jongsung_code[split_last_idx-1]) + "]");
//	    	            Log.i("Hangul", "jong_idx[" + Integer.toString(h_jongsung_idx[jongsung_code[split_last_idx - 1]+1]) + "]");
                            jong_idx = h_jongsung_idx[jongsung_code[split_last_idx - 1]+1];
                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
                        }
                        else {
                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                        }
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);

                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_next_idx - 1]);
                        sendSejongKey(newHangulChar, HCURSOR_APPEND);

                        ko_state_idx = KO_S_1111;
                    }
                    else if (new_last_idx == 0) {

                        ko_state_first_idx = 0;
                        ko_state_middle_idx = 0;
                        ko_state_last_idx = 0;

                        idx = key_idx[base_idx];
                        ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                        ko_state_idx = KO_S_1000;
                    }
                    else {
                        ko_state_last_idx = new_last_idx;
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        jong_idx = h_jongsung_idx[jongsung_code[ko_state_last_idx - 1]+1];
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                    }

                    break;
                case KO_S_1111:
                    new_last_idx = ko_last_state[ko_state_last_idx * 9 + idx];

                    if(new_last_idx >= 28) {
                        ko_state_next_idx
                                = ko_jong_l_split[(new_last_idx - 28) * 2 + 1];
                        ko_state_last_idx = new_last_idx;

                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        char split_last_idx = jongsung_28idx[ko_state_last_idx - 28];
                        jong_idx = h_jongsung_idx[jongsung_code[split_last_idx - 1]+1];
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);

                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_next_idx - 1]);
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE_LAST);

                        ko_state_idx = KO_S_1111;
                    }
                    else {
                        if (prev_key == key) {
                            ko_state_last_idx = new_last_idx;
                            // delete last cursor
                            ko_state_next_idx = 0;

                            cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                            jung_idx = (char)(ko_state_middle_idx - 3);
                            jong_idx = h_jongsung_idx[jongsung_code[ko_state_last_idx - 1]+1];
                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
                            sendSejongKey(newHangulChar, HCURSOR_UPDATE);

                            sendSejongKey((char)0,HCURSOR_DELETE_LAST);
                            ko_state_idx = KO_S_1110;
                        }
                        else {
                            clearSejong();
                            ko_state_first_idx = ko_first_state[ko_state_first_idx * 9 + idx];
                            newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                            sendSejongKey(newHangulChar, HCURSOR_ADD);
                            ko_state_idx = KO_S_1000;
                        }
                    }
                    break;

            }
        }
        else {
            switch(ko_state_idx) {

                case KO_S_0000:
                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
                    newHangulChar = getJungsungCode(ko_state_middle_idx);
                    sendSejongKey(newHangulChar, HCURSOR_NEW);
                    ko_state_idx = KO_S_0100;
                    break;
                case KO_S_0100:
                    if (ko_middle_state[ko_state_middle_idx * 3 + idx] == 0) {
                        ko_state_middle_idx = 0;
                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
                        newHangulChar = getJungsungCode(ko_state_middle_idx);
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                    }
                    else {
                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
                        newHangulChar = getJungsungCode(ko_state_middle_idx);
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                    }
                    break;
                case KO_S_1000:
                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];

                    if (ko_state_middle_idx > 2) {
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                    }
                    else {
                        newHangulChar = getJungsungCode(ko_state_middle_idx);
                        sendSejongKey(newHangulChar, HCURSOR_APPEND);
                        // must be implemented.
                    }
                    ko_state_idx = KO_S_1100;
                    break;
                case KO_S_1100:
                    if (ko_middle_state[ko_state_middle_idx * 3 + idx] == 0) {
                        ko_state_first_idx = 0;
                        ko_state_middle_idx = 0;
                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
                        newHangulChar = getJungsungCode(ko_state_middle_idx);
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                        ko_state_idx = KO_S_0100;
                    }
                    else {
                        ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];
                        if (ko_state_middle_idx > 2) {
                            cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                            jung_idx = (char)(ko_state_middle_idx - 3);
                            newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                            sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                            sendSejongKey((char)0,HCURSOR_DELETE_LAST);
                        }
                        else {
                            newHangulChar = getJungsungCode(ko_state_middle_idx);
                            sendSejongKey(newHangulChar, HCURSOR_UPDATE_LAST);
                        }
                    }

                    break;
                case KO_S_1110:
                    if (ko_jong_m_split[(ko_state_last_idx - 1) * 2] > 0) {
                        // update jongsong
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        jong_idx = h_jongsung_idx[jongsung_code[ko_jong_m_split[(ko_state_last_idx - 1) * 2]]];
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28) + jong_idx));
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);

                    }
                    else {
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                        sendSejongKey(newHangulChar, HCURSOR_UPDATE);
                    }

                    ko_state_first_idx
                            = ko_jong_m_split[(ko_state_last_idx - 1) * 2 + 1];
                    ko_state_middle_idx = 0;
                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];

                    if (ko_state_middle_idx > 2) {
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                        ko_state_idx = KO_S_1100;
                    }
                    else {
                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                        newHangulChar = getJungsungCode(ko_state_middle_idx);
                        sendSejongKey(newHangulChar, HCURSOR_APPEND);
                        // must be implemented.
                    }

                    ko_state_last_idx = 0;
                    ko_state_idx = KO_S_1100;
                    break;
                case KO_S_1111:
                    if (ko_state_last_idx >= 28) {
                        ko_state_last_idx = jongsung_28idx[ko_state_last_idx - 28];
                    }

                    ko_state_first_idx = ko_state_next_idx;
                    ko_state_middle_idx = 0;
                    ko_state_middle_idx = ko_middle_state[ko_state_middle_idx * 3 + idx];

                    sendSejongKey((char)0,HCURSOR_DELETE_LAST);

                    if (ko_state_middle_idx > 2) {
                        cho_idx = h_chosung_idx[chosung_code[ko_state_first_idx - 1]];
                        jung_idx = (char)(ko_state_middle_idx - 3);
                        newHangulChar = (char)(0xAC00 + ((cho_idx * 21 * 28) + (jung_idx * 28)));
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                        ko_state_idx = KO_S_1100;
                    }
                    else {
                        newHangulChar = (char)(0x3131 + chosung_code[ko_state_first_idx - 1]);
                        sendSejongKey(newHangulChar, HCURSOR_ADD);
                        newHangulChar = getJungsungCode(ko_state_middle_idx);
                        sendSejongKey(newHangulChar, HCURSOR_APPEND);
                    }

                    ko_state_last_idx = 0;
                    ko_state_next_idx = 0;
                    ko_state_idx = KO_S_1100;
                    break;

            }
        }

        prev_key = key;
    }


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
                            mHangulState = H_STATE_2; // goto �ʼ�(������)
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
                    if (hangulKeyIdx < 30) { // if 자음
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
//                            mHangulState = H_STATE_1; // goto �ʼ�
//                        }
//                        else {
                            mHangulState = H_STATE_5; // goto �ʼ�,�߼�,����
//                        }
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
                                mHangulState = H_STATE_3; // goto �߼�
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
                            mHangulState = H_STATE_6; // goto  �ʼ�,�߼�,����(������)
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
                            hangulSendKey(newHangulChar,HCURSOR_ADD);///////////////////////////////
                            mHangulState = H_STATE_1; // goto �ʼ�
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
                        mHangulState = H_STATE_4; // goto �ʼ�,�߼�
                    }
                    break;
                case H_STATE_6: // �ʼ�,�߼�,����(������)
                    Log.i("SoftKey", "HAN_STATE 6");
                    if (hangulKeyIdx < 30) { // if ����

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

                        mHangulState = H_STATE_1; // goto �ʼ�
                    }
                    else { // if ����
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
            updateShiftKeyState(getCurrentInputEditorInfo());
            updateCandidates();
        } else {
            sendKeyChar((char)primaryCode);
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
            if (mCandidateView != null) {
                mCandidateView.clear();
            }
            updateShiftKeyState(getCurrentInputEditorInfo());
        } else if (mComposing.length() > 0) {
            // If we were generating candidate suggestions for the current
            // text, we would commit one of them here.  But for this sample,
            // we will just commit the current text.
            commitTyped(getCurrentInputConnection());
        }
    }

    public void swipeRight() {
        if (mCompletionOn) {
            pickDefaultCandidate();
        }
    }

    public void swipeLeft() {
        handleBackspace();
    }

    public void swipeDown() {
        handleClose();
    }

    public void swipeUp() {
    }

    public void onPress(int primaryCode) {
    }

    public void onRelease(int primaryCode) {
    }
}