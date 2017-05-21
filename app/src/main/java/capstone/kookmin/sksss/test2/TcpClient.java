package capstone.kookmin.sksss.test2;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;

import static capstone.kookmin.sksss.test2.SoftKeyboard.MSG_REQUEST_RECEIVE;

/**
 * Created by sksss on 2017-04-07.
 */

public class TcpClient implements Runnable {
    private Socket socket;
    private String ip;
    private int port;
    private BufferedReader networkReader;
    private BufferedWriter networkWriter;
    String dataFromServer, JsonMessage;
    private MessegeHandler mHandler;
    private boolean isRunning = false;

    private Context context;

    //서버에서 메시지 수신을 기다리는 스레드
    public void run(){
        JsonMessage = "";
        //서버에서 메시지가 올 경우
        while(isRunning) {/////////////////
                if (socket == null || !socket.isConnected())
                    Log.d("!!!!!!!!!!!!!!","!!!!!!!!!!!!!!!!!");
                    this.openSocket();
                try {
                    if (networkReader != null) {
                        Log.d("IN","netwirkReader != null");
                        while (((dataFromServer = networkReader.readLine())!=null) && (!dataFromServer.equals("\f"))) {
                            JsonMessage += dataFromServer;
                            dataFromServer = null;
                            Log.d("Is","Loop?");
                        }
//                        dataFromServer = networkReader.readLine();
//                        String temp = null;
//                        temp = networkReader.readLine();
//                        while (temp != null && !temp.equals("")) {
//                            dataFromServer += temp;
//                            temp = null;
//                            temp = networkReader.readLine();
//                            Log.d("hi",temp);
//                        }
                        if (JsonMessage != null && !JsonMessage.equals("")) {
                            Message msg = mHandler.obtainMessage(MSG_REQUEST_RECEIVE, JsonMessage);
                            Log.d("Get from server", JsonMessage);
                            mHandler.sendMessage(msg);
                            //Toast.makeText(context,"Receive Data : " + msg.toString(), Toast.LENGTH_LONG).show();
                            dataFromServer = null;
                            JsonMessage = "";
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
        }
    }

    //생성자
    public TcpClient(Context context, String ip, int port, MessegeHandler mHandler)
    {
        this.context = context;
        this.ip = ip;
        this.port = port;
        this.mHandler = mHandler;
        this.socket = null;
    }

    //서버로 데이터를 전송하는 메소드
    public void sendData(String str)
    {
        if(socket!=null && socket.isConnected()) {
            Log.d("in","socket");
            PrintWriter out = new PrintWriter(networkWriter, true);
            out.println(str+"\n");
            Toast.makeText(context,"Send Data : " + str, Toast.LENGTH_LONG).show();
        }
        else{
            Log.w("Network error","Not open socket.");
        }
    }

    //socket 연결 메소드
    public void openSocket()
    {
            if(socket!=null)
                socketClose();
            try {
                Log.d("!?!?!?!??!?",ip + "," + port);
                socket = new Socket(ip, port);
                networkWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
                networkReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                socketClose();
                isRunning = false;
                //this.interrupt();
                e.printStackTrace();
                Log.w("Error", "Network");
                Log.w("Why error?", e.getMessage());
            }
    }

    //socket 해제 메소드
    public void socketClose()
    {
        if(socket!=null) {
            try {
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        socket = null;
    }

    //socket이 연결되어 있는지 확인하는 메소드
    public boolean isSocketOn()
    {
        if(socket==null || !socket.isConnected())
            return false;
        else
            return true;
    }

    //스레드 작동여부를 설정하는 메소드
    public void setRunningState(boolean state){
        isRunning = state;
    }

    public boolean getIsRunning(){
        return isRunning;
    }
}
