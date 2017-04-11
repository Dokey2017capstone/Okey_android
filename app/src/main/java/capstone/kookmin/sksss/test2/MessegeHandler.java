package capstone.kookmin.sksss.test2;

import android.os.Handler;
import android.os.Message;

import java.lang.ref.WeakReference;

/**
 * Created by sksss on 2017-04-10.
 */

//서버에서 받은 메시지를 처리하기 위한 핸들러
public class MessegeHandler extends Handler {

    private final WeakReference<SoftKeyboard> mService;
    public MessegeHandler(SoftKeyboard service){
        mService = new WeakReference<SoftKeyboard>(service);
    }

    @Override
    public void handleMessage(Message msg){
        SoftKeyboard keyboard = mService.get();
        if(keyboard != null)
        {
            keyboard.handleMessage(msg);
        }
    }
}
