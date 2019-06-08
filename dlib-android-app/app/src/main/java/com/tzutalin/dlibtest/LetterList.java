package com.tzutalin.dlibtest;

import java.util.HashMap;

public class LetterList {
    private String listNumber;
    private String storedMessage;
    private HashMap <String, String> messageMap = new HashMap<>();

    public LetterList() {
        initializeMap();
    }

    public void initializeMap() {
        messageMap.put("0", "나중에 연락을 드리겠습니다.");
        messageMap.put("1", "지금은 회의중입니다.");
        messageMap.put("2", "지금은 업무중입니다.");
        messageMap.put("3", "문자를 남겨주세요");
        messageMap.put("4", "지금은 식사중입니다");
    }

    //메세지 불러오기
    public String getStoredMessage(String listNum) {
        return messageMap.get(listNum);
    }

    //메세지 설정하기
    public void setStoredMessage(String listNum, String storedMessage) {
        int mode = Integer.parseInt(listNum);
        if(mode >=0 && mode <= 4){
            messageMap.remove(listNum);
            messageMap.put(listNum, storedMessage);
        }
    }

    //메세지 목록 초기화
    public void resetMessageList(){
        messageMap.clear();
        initializeMap();
    }


}
