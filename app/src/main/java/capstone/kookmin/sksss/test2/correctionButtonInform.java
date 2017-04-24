package capstone.kookmin.sksss.test2;

/**
 * Created by sksss on 2017-04-17.
 */

//수정 버튼에 관한 정보가 담긴 클래스
public class correctionButtonInform {

    private int startPos, finishPos;
    private String oldWord;
    private String[] correctionWord;

    correctionButtonInform(int startPos, int finishPos, String oldWord, String[] correctionWord)
    {
        this.startPos = startPos;
        this.finishPos = finishPos;
        this.oldWord = oldWord;
        this.correctionWord = correctionWord;
    }

    public int getStartPos() {
        return startPos;
    }

    public int getFinishPos(){
        return finishPos;
    }

    public void addStartPos(int pos){
        startPos += pos;
    }

    public void addFinishPos(int pos){
        finishPos += pos;
    }

    public void addPos(int pos){
        startPos += pos;
        finishPos += pos;
    }

    public String getOldWord(){
        return  oldWord;
    }

    public String[] getCorrectionWord(){
        return correctionWord;
    }
}
