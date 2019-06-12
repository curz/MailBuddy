package com.example.mailbuddy;

public class ListItem {

    private String header;
    private String desc;

    public ListItem(String header, String desc){
        this.header = header;
        this.desc = desc;
    }

    public String getHeader() {
        return header;
    }

    public String getDesc() {
        return desc;
    }
}
